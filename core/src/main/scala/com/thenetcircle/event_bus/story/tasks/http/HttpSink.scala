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

package com.thenetcircle.event_bus.story.tasks.http

import java.util.concurrent.ThreadLocalRandom

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.pattern.AskTimeoutException
import akka.stream._
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import com.thenetcircle.event_bus.context.{AppContext, TaskRunningContext}
import com.thenetcircle.event_bus.event.EventStatus.{NORM, TOFB}
import com.thenetcircle.event_bus.event.{Event, EventStatus}
import com.thenetcircle.event_bus.misc.{Logging, Util}
import com.thenetcircle.event_bus.story.interfaces.{ISinkTask, ITaskBuilder}
import com.thenetcircle.event_bus.story.tasks.http.HttpSink.RetrySender
import com.thenetcircle.event_bus.story.{Payload, StoryMat}
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Random, Success, Try}

class UnexpectedResponseException(info: String) extends RuntimeException(info)

case class HttpSinkSettings(
    defaultRequest: HttpRequest,
    minBackoff: FiniteDuration = 1.second,
    maxBackoff: FiniteDuration = 30.seconds,
    randomFactor: Double = 0.2,
    maxRetryTime: FiniteDuration = 12.hours,
    concurrentRetries: Int = 1,
    poolSettings: Option[ConnectionPoolSettings] = None
)

class HttpSink(val settings: HttpSinkSettings) extends ISinkTask with Logging {

  def createRequest(event: Event): HttpRequest = {
    var _request = settings.defaultRequest.withEntity(HttpEntity(event.body.data))
    if (event.hasExtra("generatorUrl"))
      _request = _request.withUri(event.getExtra("generatorUrl").get)
    _request
  }

  var retrySender: Option[ActorRef] = None

  override def flow()(
      implicit runningContext: TaskRunningContext
  ): Flow[Payload, Payload, StoryMat] = {

    implicit val system: ActorSystem               = runningContext.getActorSystem()
    implicit val materializer: Materializer        = runningContext.getMaterializer()
    implicit val exectionContext: ExecutionContext = runningContext.getExecutionContext()

    // TODO performance test
    // Init retry sender
    val senderActor = retrySender.getOrElse({
      retrySender = Some(
        system
          .actorOf(
            Props(classOf[RetrySender], settings, runningContext),
            "http-sender-" + getStoryName() + "-" + Random.nextInt()
          )
      )
      retrySender.get
    })

    Flow[Payload]
      .mapAsync(settings.concurrentRetries) {
        case (NORM, event) =>
          val retryTimeout = settings.maxRetryTime

          import akka.pattern.ask
          implicit val askTimeout: Timeout = Timeout(retryTimeout)

          val endPoint: String   = settings.defaultRequest.getUri().toString
          val eventBrief: String = Util.getBriefOfEvent(event)

          (senderActor ? RetrySender.Req(createRequest(event), retryTimeout.fromNow))
            .mapTo[Try[HttpResponse]]
            .map[(EventStatus, Event)] {
              case Success(resp) =>
                consumerLogger.info(s"A event successfully sent to HTTP endpoint [$endPoint], $eventBrief")
                (NORM, event)
              case Failure(ex) =>
                consumerLogger.warn(
                  s"A event unsuccessfully sent to HTTP endpoint [$endPoint], $eventBrief, failed with error $ex"
                )
                (TOFB(Some(ex), getTaskName()), event)
            }
            .recover {
              case ex: AskTimeoutException =>
                consumerLogger.warn(
                  s"A event sent to HTTP endpoint [$endPoint] timeout, exceed [$retryTimeout], $eventBrief"
                )
                (TOFB(Some(ex), getTaskName()), event)
            }

        case others => Future.successful(others)
      }

  }

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit = {
    logger.info(s"Shutting down http-sink of story ${getStoryName()}.")
    retrySender.foreach(s => {
      runningContext.getActorSystem().stop(s);
      retrySender = None
    })
  }
}

object HttpSink extends Logging {

  val RESPONSE_OK                  = "ok"
  val RESPONSE_EXPONENTIAL_BACKOFF = "exponential_backoff"

  object RetrySender {
    case class Req(payload: HttpRequest, deadline: Deadline, retryTimes: Int = 1) {
      def retry(): Req = copy(retryTimes = retryTimes + 1)
    }
    case class Retry(req: Req)
    case class CheckResp(resp: HttpResponse, req: Req)

    private def calculateDelay(
        retryTimes: Int,
        minBackoff: FiniteDuration,
        maxBackoff: FiniteDuration,
        randomFactor: Double
    ): FiniteDuration = {
      val rnd = 1.0 + ThreadLocalRandom.current().nextDouble() * randomFactor
      if (retryTimes >= 30) // Duration overflow protection (> 100 years)
        maxBackoff
      else
        maxBackoff.min(minBackoff * math.pow(2, retryTimes)) * rnd match {
          case f: FiniteDuration ⇒ f
          case _                 ⇒ maxBackoff
        }
    }
  }

  class RetrySender(httpSinkSettings: HttpSinkSettings)(implicit runningContext: TaskRunningContext)
      extends Actor
      with ActorLogging {

    import RetrySender._

    implicit val system: ActorSystem                = runningContext.getActorSystem()
    implicit val materializer: Materializer         = runningContext.getMaterializer()
    implicit val executionContext: ExecutionContext = runningContext.getExecutionContext()

    val http = Http()
    val poolSettings: ConnectionPoolSettings =
      httpSinkSettings.poolSettings.getOrElse(
        ConnectionPoolSettings(runningContext.getActorSystem())
      )

    def replyToReceiver(result: Try[HttpResponse], receiver: ActorRef): Unit = {
      consumerLogger.debug(s"Replying response to http-sink")
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
        val receiver   = sender()
        val requestUrl = payload.getUri().toString
        http
          .singleRequest(request = payload, settings = poolSettings) andThen {
          case Success(resp) => self.tell(CheckResp(resp, req), receiver)
          case Failure(ex) =>
            if (retryTimes == 1)
              consumerLogger.warn(
                s"Sending request to $requestUrl failed with error $ex, going to retry now."
              )
            else
              consumerLogger.info(
                s"Resending request to $requestUrl failed with error $ex, retry-times is $retryTimes"
              )

            self.tell(Retry(req), receiver)
        }

      case CheckResp(resp @ HttpResponse(status, _, entity, _), req) =>
        val receiver = sender()
        if (status.isSuccess()) {
          Unmarshaller
            .byteStringUnmarshaller(entity)
            .map { _body =>
              val body = _body.utf8String
              consumerLogger.info(
                s"Get a response from upstream with status code ${status.value} and body $body"
              )
              if (body == RESPONSE_OK) {
                replyToReceiver(Success(resp), receiver)
              } else if (body == RESPONSE_EXPONENTIAL_BACKOFF) {
                consumerLogger.info(
                  s"Going to retry now since got retry signal from the endpoint"
                )
                self.tell(Retry(req), receiver)
              } else {
                // the response body was not expected
                replyToReceiver(
                  Failure(new UnexpectedResponseException(s"The response body $body was not expected.")),
                  receiver
                )
              }
            }
        } else {
          val errorMsg = s"Get a response from upstream with non-200 [$status] status code"
          consumerLogger.debug(errorMsg)
          resp.discardEntityBytes()
          replyToReceiver(Failure(new UnexpectedResponseException(errorMsg)), receiver)
        }

      case Retry(req) =>
        backoffRetry(req, sender()) // not safe
    }
  }
}

class HttpSinkBuilder() extends ITaskBuilder[HttpSink] with Logging {

  override val taskType: String = "http"

  override val defaultConfig: Config =
    ConfigFactory.parseString(
      """{
      |  # the default request could be overrided by info of the event
      |  default-request {
      |    method = POST
      |    # uri = "http://www.google.com"
      |  }
      |  min-backoff = 1 s
      |  max-backoff = 30 s
      |  random-factor = 0.2
      |  max-retrytime = 12 h
      |  concurrent-retries = 1
      |
      |  # pool settings will override the default settings of akka.http.host-connection-pool
      |  pool {
      |    # max-connections = 4
      |    # min-connections = 0
      |    # max-open-requests = 32
      |    # pipelining-limit = 1
      |    # idle-timeout = 30 s
      |    # ...
      |  }
      |}""".stripMargin
    )

  override def buildTask(
      config: Config
  )(implicit appContext: AppContext): HttpSink =
    try {
      val requestMethod = config.as[String]("default-request.method").toUpperCase match {
        case "POST" => HttpMethods.POST
        case "GET"  => HttpMethods.GET
        case unacceptedMethod =>
          throw new IllegalArgumentException(s"Request method $unacceptedMethod is not supported.")
      }
      val requsetUri                  = Uri(config.as[String]("default-request.uri"))
      val defaultRequest: HttpRequest = HttpRequest(method = requestMethod, uri = requsetUri)

      val poolSettingsMap = config.as[Map[String, String]]("pool")
      val poolSettingsOption = if (poolSettingsMap.nonEmpty) {
        var settingsStr =
          poolSettingsMap.foldLeft("")((acc, kv) => acc + "\n" + s"${kv._1} = ${kv._2}")
        Some(ConnectionPoolSettings(s"""akka.http.host-connection-pool {
             |$settingsStr
             |}""".stripMargin))
      } else None

      val settings = HttpSinkSettings(
        defaultRequest,
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
