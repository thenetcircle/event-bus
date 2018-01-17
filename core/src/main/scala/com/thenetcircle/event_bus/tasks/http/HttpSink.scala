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

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{StatusCode, _}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream._
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import akka.{Done, NotUsed}
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.interface.{SinkTask, SinkTaskBuilder}
import com.thenetcircle.event_bus.misc.ConfigStringParser
import com.thenetcircle.event_bus.story.TaskRunningContext
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._

import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

case class HttpSinkSettings(defaultRequest: HttpRequest,
                            expectedResponseBody: String,
                            maxRetryTimes: Int = 9,
                            maxConcurrentRetries: Int = 1,
                            totalRetryTimeout: FiniteDuration = 6.seconds,
                            poolSettings: Option[ConnectionPoolSettings] = None)

class HttpSink(val settings: HttpSinkSettings) extends SinkTask with StrictLogging {

  def createRequest(event: Event): HttpRequest = {
    settings.defaultRequest.withEntity(HttpEntity(event.body.data.utf8String))
  }

  def checkResponse(status: StatusCode, headers: Seq[HttpHeader], body: Option[String]): Boolean = {
    status == StatusCodes.OK && body.get == settings.expectedResponseBody
  }

  override def getHandler()(
      implicit context: TaskRunningContext
  ): Flow[Event, (Try[Done], Event), NotUsed] = {
    import HttpSink.RetrySender._
    import HttpSink._

    implicit val system: ActorSystem = context.getActorSystem()
    implicit val materializer: Materializer = context.getMaterializer()
    implicit val exectionContext: ExecutionContext = context.getExecutionContext()

    // TODO: includes StoryName
    val retrySender = system.actorOf(
      RetrySender.props(settings.maxRetryTimes, checkResponse, settings.poolSettings),
      "http-retry-sender"
    )

    Flow[Event]
      .mapAsync(settings.maxConcurrentRetries) { event =>
        import akka.pattern.ask
        implicit val askTimeout: Timeout = Timeout(settings.totalRetryTimeout)

        (retrySender ? Send(createRequest(event)))
          .mapTo[Result]
          .map(result => (result.payload.map(_ => Done), event))
          .recover {
            case NonFatal(ex) => (Failure(ex), event)
          }
      }
    // Comment this because of that the flow might be materialized multiple times(like KafkaSource)
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

    case class Send(request: HttpRequest, retryTimes: Int = 0) {
      def retry(): Send = copy(retryTimes = retryTimes + 1)
    }
    case class Resp(respTry: Try[HttpResponse], send: Send)
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

    def replyToOriginalSender(msg: Try[HttpResponse]): Unit = {
      log.debug(s"replying to original sender: $msg")
      sender() ! Result(msg)
    }

    override def receive: Receive = {
      case send @ Send(req, _) =>
        val originalSender = sender()

        val respFuture = conntionPoolSettings match {
          case Some(_settings) => Http().singleRequest(request = req, settings = _settings)
          case None            => Http().singleRequest(request = req)
        }
        respFuture.andThen {
          case respTry => self.tell(Resp(respTry, send), originalSender)
        }

      case Resp(respTry, send: Send) =>
        val originalSender = sender()

        respTry match {
          case Success(resp @ HttpResponse(status @ StatusCodes.OK, headers, entity, _)) =>
            Unmarshaller
              .byteStringUnmarshaller(entity)
              .map { body =>
                log.debug("Got response, body: " + body.utf8String)
                respCheckFunc(status, headers, Some(body.utf8String))
              }
              .andThen {
                case resultTry => self.tell(Check(resultTry, send, Some(resp)), originalSender)
              }
          case Success(resp @ HttpResponse(status, headers, _, _)) =>
            log.info("Got non-200 status code: " + status)
            resp.discardEntityBytes()
            self.tell(
              Check(Try(respCheckFunc(status, headers, None)), send, Some(resp)),
              originalSender
            )
          case Failure(ex) =>
            self.tell(Check(Failure(ex), send, None), originalSender)
        }

      case Check(resultTry, send, respOption) =>
        val originalSender = sender()

        val canRetry: Boolean = maxRetryTimes < 0 || send.retryTimes < maxRetryTimes
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

  override def build(configString: String): HttpSink = {

    val defaultConfig: Config =
      ConfigStringParser.convertStringToConfig("""
                                |{
                                |  "request": {
                                |    "method": "POST"
                                |    # "uri": "..."
                                |  },
                                |  "max-concurrent-retries": 1,
                                |  "max-retry-times": 9,
                                |  "expected-response": "OK",
                                |  "total-retry-timeout": "6 s",
                                |  "pool-settings": {
                                |    "max-connections": 4,
                                |    "min-connections": 0,
                                |    "max-retries": 5,
                                |    "max-open-requests": 32,
                                |    "pipelining-limit": 1,
                                |    "idle-timeout": "30 s"
                                |  }
                                |}
                              """.stripMargin)

    val config: Config =
      ConfigStringParser.convertStringToConfig(configString).withFallback(defaultConfig)

    try {
      val requestMethod = config.as[String]("request.method").toUpperCase() match {
        case "POST" => HttpMethods.POST
        case "GET"  => HttpMethods.GET
        case unacceptedMethod =>
          throw new IllegalArgumentException(s"Request method $unacceptedMethod is not supported.")
      }
      val requsetUri = Uri(config.as[String]("request.uri"))
      val defaultRequest: HttpRequest = HttpRequest(method = requestMethod, uri = requsetUri)

      val poolSettingsMap = config.as[Map[String, String]]("pool-settings")
      val poolSettingsOption = if (poolSettingsMap.nonEmpty) {
        var _settingsStr =
          poolSettingsMap.foldLeft("")((acc, kv) => acc + "\n" + s"${kv._1} = ${kv._2}")
        Some(ConnectionPoolSettings(s""" akka.http.host-connection-pool {
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
        logger.error(s"Creating HttpSinkSettings failed with error: ${ex.getMessage}")
        throw ex
    }

  }
}
