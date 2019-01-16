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

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.pattern.AskTimeoutException
import akka.stream._
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import com.thenetcircle.event_bus.AppContext
import com.thenetcircle.event_bus.event.EventStatus.{NORMAL, STAGING}
import com.thenetcircle.event_bus.event.{Event, EventStatus}
import com.thenetcircle.event_bus.misc.{Logging, Util}
import com.thenetcircle.event_bus.story.interfaces.{ISink, ITaskBuilder, TaskLogging}
import com.thenetcircle.event_bus.story.tasks.http.HttpSink.RetrySender
import com.thenetcircle.event_bus.story.{Payload, StoryLogger, StoryMat, TaskRunningContext}
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Random, Success, Try}

class UnexpectedResponseException(info: String) extends RuntimeException(info)

case class HttpSinkSettings(
    defaultRequest: HttpRequest,
    retrySettings: RetrySettings = RetrySettings(),
    connectionPoolSettings: Option[ConnectionPoolSettings] = None,
    concurrentRetries: Int = 1,
    requestContentType: ContentType.NonBinary = ContentTypes.`text/plain(UTF-8)`
)

case class RetrySettings(
    minBackoff: FiniteDuration = 1.second,
    maxBackoff: FiniteDuration = 30.seconds,
    randomFactor: Double = 0.2,
    maxRetryTime: FiniteDuration = 12.hours
)

class HttpSink(val settings: HttpSinkSettings) extends ISink with TaskLogging {

  def createHttpRequest(event: Event): HttpRequest =
    settings.defaultRequest.withEntity(HttpEntity(settings.requestContentType, event.body.data))

  var retrySender: Option[ActorRef] = None

  override def sinkFlow()(
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
            Props(classOf[RetrySender], settings, storyLogger, runningContext),
            "http-sender-" + getStoryName() + "-" + Random.nextInt()
          )
      )
      retrySender.get
    })

    Flow[Payload]
      .mapAsync(settings.concurrentRetries) {
        case (NORMAL, event) =>
          val retryTimeout = settings.retrySettings.maxRetryTime

          import akka.pattern.ask
          implicit val askTimeout: Timeout = Timeout(retryTimeout)

          val endPoint: String   = settings.defaultRequest.getUri().toString
          val eventBrief: String = Util.getBriefOfEvent(event)

          (senderActor ? RetrySender.Req(createHttpRequest(event), retryTimeout.fromNow))
            .mapTo[Try[HttpResponse]]
            .map[(EventStatus, Event)] {
              case Success(resp) =>
                storyLogger.info(s"A event successfully sent to HTTP endpoint [$endPoint], $eventBrief")
                (NORMAL, event)
              case Failure(ex) =>
                storyLogger.warn(
                  s"A event unsuccessfully sent to HTTP endpoint [$endPoint], $eventBrief, failed with error $ex"
                )
                (STAGING(Some(ex), getTaskName()), event)
            }
            .recover {
              case ex: AskTimeoutException =>
                storyLogger.warn(
                  s"A event sent to HTTP endpoint [$endPoint] timeout, exceed [$retryTimeout], $eventBrief"
                )
                (STAGING(Some(ex), getTaskName()), event)
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

object HttpSink {

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

  class RetrySender(httpSinkSettings: HttpSinkSettings, storyLogger: StoryLogger)(
      implicit runningContext: TaskRunningContext
  ) extends Actor {

    import RetrySender._

    implicit val system: ActorSystem                = runningContext.getActorSystem()
    implicit val materializer: Materializer         = runningContext.getMaterializer()
    implicit val executionContext: ExecutionContext = runningContext.getExecutionContext()

    val http = Http()
    val poolSettings: ConnectionPoolSettings =
      httpSinkSettings.connectionPoolSettings.getOrElse(
        ConnectionPoolSettings(runningContext.getActorSystem())
      )

    def replyToReceiver(result: Try[HttpResponse], receiver: ActorRef): Unit = {
      storyLogger.debug(s"Replying response to http-sink")
      receiver ! result
    }

    def backoffRetry(req: Req, receiver: ActorRef): Unit = if (req.deadline.hasTimeLeft()) {
      val retryDelay = calculateDelay(
        req.retryTimes,
        httpSinkSettings.retrySettings.minBackoff,
        httpSinkSettings.retrySettings.maxBackoff,
        httpSinkSettings.retrySettings.randomFactor
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
              storyLogger.warn(
                s"Sending request to $requestUrl failed with error $ex, going to retry now."
              )
            else
              storyLogger.info(
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
              storyLogger.info(
                s"Get a response from upstream with status code ${status.value} and body $body"
              )
              if (body == RESPONSE_OK) {
                replyToReceiver(Success(resp), receiver)
              } else if (body == RESPONSE_EXPONENTIAL_BACKOFF) {
                storyLogger.info(
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
          storyLogger.debug(errorMsg)
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
      |    protocol = "HTTP/1.1"
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

  private def buildHttpRequestFromConfig(config: Config)(implicit appContext: AppContext): HttpRequest = {
    val method = HttpMethods.getForKeyCaseInsensitive(config.as[String]("method")).get
    val uri    = Uri(config.as[String]("uri"))
    val headers = config
      .as[Option[Map[String, String]]]("headers")
      .map(
        _.to[collection.immutable.Seq]
          .map {
            case (name, value) =>
              HttpHeader.parse(name, value) match {
                case Ok(header, errors) => Some(header)
                case _                  => None
              }
          }
          .filter(_.isDefined)
          .map(_.get)
      )
      .getOrElse(Nil)
    val protocol = HttpProtocols.getForKeyCaseInsensitive(config.as[String]("protocol")).get

    HttpRequest(method = method, uri = uri, headers = headers, protocol = protocol)
  }

  private def buildConnectionPoolSettings(
      settings: Map[String, String]
  ): ConnectionPoolSettings = {
    var configString = settings.foldLeft("")((acc, kv) => acc + "\n" + s"${kv._1} = ${kv._2}")
    ConnectionPoolSettings(s"""akka.http.host-connection-pool {
                              |$configString
                              |}""".stripMargin)
  }

  override def buildTask(
      config: Config
  )(implicit appContext: AppContext): HttpSink =
    try {
      val defaultRequest: HttpRequest = buildHttpRequestFromConfig(config.getConfig("default-request"))
      val connectionPoolSettings =
        config.as[Option[Map[String, String]]]("pool").filter(_.nonEmpty).map(buildConnectionPoolSettings)
      val retrySettings = RetrySettings(
        config.as[FiniteDuration]("min-backoff"),
        config.as[FiniteDuration]("max-backoff"),
        config.as[Double]("random-factor"),
        config.as[FiniteDuration]("max-retrytime")
      )

      new HttpSink(
        HttpSinkSettings(
          defaultRequest,
          retrySettings,
          connectionPoolSettings,
          config.as[Int]("concurrent-retries")
        )
      )
    } catch {
      case ex: Throwable =>
        logger.error(s"Build HttpSink failed with error: ${ex.getMessage}")
        throw ex
    }
}
