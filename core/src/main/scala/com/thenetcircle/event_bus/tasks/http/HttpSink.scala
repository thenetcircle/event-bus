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
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.pattern.AskTimeoutException
import akka.stream._
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import com.thenetcircle.event_bus.context.{TaskBuildingContext, TaskRunningContext}
import com.thenetcircle.event_bus.helper.ConfigStringParser
import com.thenetcircle.event_bus.interfaces.EventStatus.{Fail, Norm, ToFB}
import com.thenetcircle.event_bus.interfaces.{Event, EventStatus, SinkTask, SinkTaskBuilder}
import com.thenetcircle.event_bus.tasks.http.HttpSink.RetrySender
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

case class HttpSinkSettings(defaultRequest: HttpRequest,
                            expectedResponseBody: String,
                            minBackoff: FiniteDuration = 1.second,
                            maxBackoff: FiniteDuration = 30.seconds,
                            randomFactor: Double = 0.2,
                            maxRetryTime: FiniteDuration = 6.seconds,
                            concurrentRetries: Int = 1,
                            poolSettings: Option[ConnectionPoolSettings] = None)

class HttpSink(val settings: HttpSinkSettings) extends SinkTask with StrictLogging {

  def createRequest(event: Event): HttpRequest = {
    settings.defaultRequest.withEntity(HttpEntity(event.body.data))
  }

  var retrySender: Option[ActorRef] = None

  override def getHandler()(
      implicit runningContext: TaskRunningContext
  ): Flow[Event, (EventStatus, Event), NotUsed] = {

    implicit val system: ActorSystem = runningContext.getActorSystem()
    implicit val materializer: Materializer = runningContext.getMaterializer()
    implicit val exectionContext: ExecutionContext = runningContext.getExecutionContext()

    // TODO performance test
    // Init retry sender
    val senderActor = retrySender.getOrElse({
      retrySender = Some(
        system
          .actorOf(
            Props(classOf[RetrySender], settings, runningContext),
            runningContext.getStoryName() + "-http-sender"
          )
      )
      retrySender.get
    })

    Flow[Event]
      .mapAsync(settings.concurrentRetries) { event =>
        val retryTimeout = settings.maxRetryTime

        import akka.pattern.ask
        implicit val askTimeout: Timeout = Timeout(retryTimeout)

        (senderActor ? RetrySender.Req(createRequest(event), retryTimeout.fromNow))
          .mapTo[Try[HttpResponse]]
          .map[(EventStatus, Event)] {
            case Success(resp) => (Norm, event)
            case Failure(ex)   => (ToFB(Some(ex)), event)
          }
          .recover {
            case ex: AskTimeoutException =>
              logger.warn(
                s"the request to ${settings.defaultRequest.getUri().toString} exceed retry-timeout $retryTimeout"
              )
              (Fail(ex), event)
          }
      }

  }

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit = {
    retrySender.foreach(s => { runningContext.getActorSystem().stop(s); retrySender = None })
  }
}

object HttpSink {
  object RetrySender {
    case class Req(payload: HttpRequest, deadline: Deadline, retryTimes: Int = 1) {
      def retry(): Req = copy(retryTimes = retryTimes + 1)
    }
    case class Retry(req: Req)
    case class Resp(payload: HttpResponse)

    private def calculateDelay(retryTimes: Int,
                               minBackoff: FiniteDuration,
                               maxBackoff: FiniteDuration,
                               randomFactor: Double): FiniteDuration = {
      val rnd = 1.0 + ThreadLocalRandom.current().nextDouble() * randomFactor
      if (retryTimes >= 30) // Duration overflow protection (> 100 years)
        maxBackoff
      else
        maxBackoff.min(minBackoff * math.pow(2, retryTimes)) * rnd match {
          case f: FiniteDuration ⇒ f
          case _ ⇒ maxBackoff
        }
    }

    class UnexpectedResponseException(info: String) extends RuntimeException(info)
  }

  class RetrySender(httpSinkSettings: HttpSinkSettings)(implicit runningContext: TaskRunningContext)
      extends Actor
      with ActorLogging {

    import RetrySender._

    implicit val system: ActorSystem = runningContext.getActorSystem()
    implicit val materializer: Materializer = runningContext.getMaterializer()
    implicit val executionContext: ExecutionContext = runningContext.getExecutionContext()

    val http = Http()
    val poolSettings: ConnectionPoolSettings =
      httpSinkSettings.poolSettings.getOrElse(
        ConnectionPoolSettings(runningContext.getActorSystem())
      )

    def replyToReceiver(result: Try[HttpResponse], receiver: ActorRef): Unit = {
      log.debug(s"replying response to the receiver")
      receiver ! result
    }

    def backoffRetry(req: Req, receiver: ActorRef): Unit = if (req.deadline.hasTimeLeft()) {
      val retryDelay = calculateDelay(
        req.retryTimes,
        httpSinkSettings.minBackoff,
        httpSinkSettings.maxBackoff,
        httpSinkSettings.randomFactor
      )
      if (req.deadline.compare(retryDelay.fromNow) > 0)
        context.system.scheduler
          .scheduleOnce(retryDelay, self, req.retry())(executionContext, receiver)
    }

    override def receive: Receive = {
      case req @ Req(payload, _, retryTimes) =>
        val receiver = sender()
        val requestUrl = payload.getUri().toString
        http
          .singleRequest(request = payload, settings = poolSettings) andThen {
          case Success(resp) => self.tell(Resp(resp), receiver)
          case Failure(ex) =>
            if (retryTimes == 1)
              log.info(s"request to $requestUrl send failed with error: $ex")
            else
              log.debug(s"request to $requestUrl resend failed with error: $ex")

            self.tell(Retry(req), receiver)
        }

      case Resp(resp @ HttpResponse(status, _, entity, _)) =>
        val receiver = sender()
        if (status.isSuccess()) {
          Unmarshaller
            .byteStringUnmarshaller(entity)
            .map { _body =>
              val body = _body.utf8String
              log.debug(s"Got response with status code ${status.value} and body: $body")
              if (body != httpSinkSettings.expectedResponseBody) {
                // the response body was not expected
                replyToReceiver(
                  Failure(new UnexpectedResponseException("the response body was not expected")),
                  receiver
                )
              } else {
                replyToReceiver(Success(resp), receiver)
              }
            }
        } else {
          log.debug(s"Get response with non-200 [$status] status code")
          resp.discardEntityBytes()
          replyToReceiver(Failure(new UnexpectedResponseException("non-200 status code")), receiver)
        }

      case Retry(req) =>
        backoffRetry(req, sender()) // not safe
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
        config.as[FiniteDuration]("min-backoff"),
        config.as[FiniteDuration]("max-backoff"),
        config.as[Double]("random-factor"),
        config.as[FiniteDuration]("max-retrytime"),
        config.as[Int]("concurrent-retries"),
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
