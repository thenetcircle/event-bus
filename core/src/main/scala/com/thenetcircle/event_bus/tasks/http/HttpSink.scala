/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Beineng Ma <baineng.ma@gmail.com>
 */

package com.thenetcircle.event_bus.tasks.http

import java.util.concurrent.ThreadLocalRandom

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{StatusCode, _}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream._
import akka.stream.scaladsl.{Flow, RestartFlow}
import com.thenetcircle.event_bus.context.{TaskBuildingContext, TaskRunningContext}
import com.thenetcircle.event_bus.helper.ConfigStringParser
import com.thenetcircle.event_bus.interfaces.EventStatus.{Norm, ToFB}
import com.thenetcircle.event_bus.interfaces.{Event, SinkTask, SinkTaskBuilder}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._

import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class HttpSinkSettings(defaultRequest: HttpRequest,
                            expectedResponseBody: String,
                            maxRetryTimes: Int = 9,
                            maxConcurrentRetries: Int = 1,
                            totalRetryTimeout: FiniteDuration = 6.seconds,
                            poolSettings: Option[ConnectionPoolSettings] = None)

class HttpSink(val settings: HttpSinkSettings) extends SinkTask with StrictLogging {

  def createRequest(event: Event): HttpRequest = {
    settings.defaultRequest.withEntity(HttpEntity(event.body.data))
  }

  def checkResponse(status: StatusCode, headers: Seq[HttpHeader], body: Option[String]): Boolean = {
    status == StatusCodes.OK && body.get == settings.expectedResponseBody
  }

  override def getHandler()(
      implicit runningContext: TaskRunningContext
  ): Flow[Event, (Status, Event), NotUsed] = {

    implicit val system: ActorSystem = runningContext.getActorSystem()
    implicit val materializer: Materializer = runningContext.getMaterializer()
    implicit val exectionContext: ExecutionContext = runningContext.getExecutionContext()

    val sendingFlow =
      Http().superPool[Event](
        settings = settings.poolSettings.getOrElse(ConnectionPoolSettings(system))
      )

    val retrySendingFlow: Flow[(HttpRequest, Event), (Status, Event), NotUsed] =
      RestartFlow.withBackoff(minBackoff = 1.second, maxBackoff = 30.seconds, randomFactor = 0.2) {
        () =>
          Flow[(HttpRequest, Event)]
            .via(sendingFlow)
            .mapAsync(1) {
              case (Success(resp), event) =>
                Unmarshaller
                  .byteStringUnmarshaller(resp.entity)
                  .map { _body =>
                    val body = _body.utf8String
                    logger.debug(
                      s"Got response, status ${resp.status.value}, body: $_body"
                    )

                    if (resp.status.isSuccess()) {
                      if (body != settings.expectedResponseBody)
                        ToFB() -> event
                      else
                        Norm -> event
                    } else {
                      throw new RuntimeException("Got Non-2xx status code from endpoint")
                    }
                  }

              case (Failure(ex), _) =>
                // let it retry with failed cases (network issue, no response etc...)
                Future.failed(ex)
            }
      }

    Flow[Event]
      .map(e => createRequest(e) -> e)
      .via(retrySendingFlow)
    /*// TODO: includes StoryName
    val retrySender = system.actorOf(
      RetrySender.props(settings.maxRetryTimes, checkResponse, settings.poolSettings),
      "http-retry-sender"
    )

    Flow[Event]
      .mapAsync(settings.maxConcurrentRetries) { event =>
        import akka.pattern.ask
        implicit val askTimeout: Timeout = Timeout(settings.totalRetryTimeout)

        (retrySender ? RetrySender.Send(createRequest(event)))
          .mapTo[RetrySender.Result]
          .map(result => {
            // TODO for failure case will retry by backoff, for non-200 case / for ko case will send to fallback
            // TODO use ToFB for failure case
            (createStatusFromTry(result.payload), event)
          })

      }*/

    /*.recover {
            case ex: AskTimeoutException => (Failure(ex), event)
          }*/
    // Comment this because of that the flow might be materialized multiple times(such as KafkaSource)
    /*.watchTermination() { (_, done) =>
        done.map(_ => {
          system.stop(retrySender)
          // retrySender ! PoisonPill
        })
        NotUsed
      }*/
  }
}

object HttpSink {
  object RetrySender {
    def props(
        maxRetryTimes: Int,
        respCheckFunc: (StatusCode, Seq[HttpHeader], Option[String]) => Boolean,
        conntionPoolSettings: Option[ConnectionPoolSettings] = None
    )(implicit runningContext: TaskRunningContext): Props = {
      Props(new RetrySender(maxRetryTimes, respCheckFunc, conntionPoolSettings))
    }

    case class Send(request: HttpRequest, sendTimes: Int = 1) {
      def retry(): Send = copy(sendTimes = sendTimes + 1)
    }
    case class RespCheck(result: Try[HttpResponse], send: Send)
    case class Check(resultTry: Try[Boolean], send: Send, respOption: Option[HttpResponse])
    case class Result(payload: Try[HttpResponse])
  }

  class RetrySender(
      maxRetryTimes: Int,
      respCheckFunc: (StatusCode, Seq[HttpHeader], Option[String]) => Boolean,
      conntionPoolSettings: Option[ConnectionPoolSettings] = None
  )(implicit runningContext: TaskRunningContext)
      extends Actor
      with ActorLogging {

    import RetrySender._

    implicit val system: ActorSystem = runningContext.getActorSystem()
    implicit val materializer: Materializer = runningContext.getMaterializer()
    implicit val executionContext: ExecutionContext = runningContext.getExecutionContext()

    val http = Http()
    val poolSettings: ConnectionPoolSettings =
      conntionPoolSettings.getOrElse(ConnectionPoolSettings(system))

    def replyToOriginalSender(msg: Try[HttpResponse]): Unit = {
      log.debug(s"replying to original sender: $msg")
      sender() ! Result(msg)
    }

    def resend(send: Send, originalSender: ActorRef): Unit = {}

    def backoffResend(send: Send, originalSender: ActorRef): Unit = {
      val restartDelay = calculateDelay(send.sendTimes, 1.second, 30.seconds, 0.2)
      context.system.scheduler
        .scheduleOnce(restartDelay, self, send.retry())(executionContext, originalSender)
    }

    private def calculateDelay(restartCount: Int,
                               minBackoff: FiniteDuration,
                               maxBackoff: FiniteDuration,
                               randomFactor: Double): FiniteDuration = {
      val rnd = 1.0 + ThreadLocalRandom.current().nextDouble() * randomFactor
      if (restartCount >= 30) // Duration overflow protection (> 100 years)
        maxBackoff
      else
        maxBackoff.min(minBackoff * math.pow(2, restartCount)) * rnd match {
          case f: FiniteDuration ⇒ f
          case _ ⇒ maxBackoff
        }
    }

    override def receive: Receive = {
      case send @ Send(req, _) =>
        val originalSender = sender()
        http.singleRequest(request = req, settings = poolSettings) andThen {
          case result => self.tell(RespCheck(result, send), originalSender)
        }

      case RespCheck(result, send) =>
        val originalSender = sender()

        result match {
          case Success(resp @ HttpResponse(status, headers, entity, _)) =>
            Unmarshaller
              .byteStringUnmarshaller(entity)
              .map { body =>
                log.debug(s"Got response, status ${status.value}, body: ${body.utf8String}")
                respCheckFunc(status, headers, Some(body.utf8String))
              }
              .andThen {
                case resultTry => self.tell(Check(resultTry, send, Some(resp)), originalSender)
              }

          /*case Success(resp @ HttpResponse(status, headers, entity, _)) =>
            log.info("Got non-200 status code: " + status)
            resp.discardEntityBytes()
            self.tell(
              Check(Try(respCheckFunc(status, headers, None)), send, Some(resp)),
              originalSender
            )*/

          case Failure(ex) =>
            self.tell(Check(Failure(ex), send, None), originalSender)
        }

      case Check(resultTry, send, respOption) =>
        val originalSender = sender()

        val canRetry: Boolean = maxRetryTimes < 0 || send.sendTimes < maxRetryTimes
        resultTry match {
          case Success(true) => replyToOriginalSender(Success(respOption.get))
          case Success(false) =>
            if (canRetry) {
              self.tell(send.retry(), originalSender)
            } else {
              val failedMessage = "The response checking was failed."
              log.debug(failedMessage)
              replyToOriginalSender(Failure[HttpResponse](new Exception(failedMessage)))
            }
          case Failure(ex) =>
            log.warning(s"Request failed with error: $ex")
            if (canRetry)
              self.tell(send.retry(), originalSender)
            else
              replyToOriginalSender(Failure[HttpResponse](ex))
        }
    }
  }
}

class HttpSinkBuilder() extends SinkTaskBuilder with StrictLogging {

  override def build(
      configString: String
  )(implicit buildingContext: TaskBuildingContext): HttpSink = {

    val config: Config =
      ConfigStringParser
        .convertStringToConfig(configString)
        .withFallback(buildingContext.getSystemConfig().getConfig("task.http-sink"))

    try {
      val requestMethod = config.as[String]("request.method").toUpperCase match {
        case "POST" => HttpMethods.POST
        case "GET"  => HttpMethods.GET
        case unacceptedMethod =>
          throw new IllegalArgumentException(s"Request method $unacceptedMethod is not supported.")
      }
      val requsetUri = Uri(config.as[String]("request.uri"))
      val defaultRequest: HttpRequest = HttpRequest(method = requestMethod, uri = requsetUri)

      val poolSettingsMap = config.as[Map[String, String]]("pool")
      val poolSettingsOption = if (poolSettingsMap.nonEmpty) {
        var _settingsStr =
          poolSettingsMap.foldLeft("")((acc, kv) => acc + "\n" + s"${kv._1} = ${kv._2}")
        Some(ConnectionPoolSettings(s"""akka.http.host-connection-pool {
             |${_settingsStr}
             |}""".stripMargin))
      } else None

      val settings = HttpSinkSettings(
        defaultRequest,
        config.as[String]("expected-response"),
        config.as[Int]("max-retry-times"),
        config.as[Int]("max-concurrent-retries"),
        config.as[FiniteDuration]("total-retry-timeout"),
        poolSettingsOption
      )

      new HttpSink(settings)

    } catch {
      case ex: Throwable =>
        logger.error(s"Build HttpSink failed with error: ${ex.getMessage}")
        throw ex
    }

  }
}
