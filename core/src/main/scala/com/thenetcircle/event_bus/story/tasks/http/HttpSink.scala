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

import java.util.concurrent.{ThreadLocalRandom, TimeoutException}

import akka.actor.{Actor, ActorRef, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.pattern.{ask, AskTimeoutException}
import akka.stream._
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, SourceQueueWithComplete}
import akka.util.Timeout
import com.thenetcircle.event_bus.event.EventStatus.{FAILED, NORMAL, STAGING}
import com.thenetcircle.event_bus.event.{Event, EventStatus}
import com.thenetcircle.event_bus.misc.Logging
import com.thenetcircle.event_bus.story.interfaces.{ISink, ITaskBuilder, ITaskLogging}
import com.thenetcircle.event_bus.story.tasks.http.HttpSink.{CheckResponseResult, RequestException, RetrySender}
import com.thenetcircle.event_bus.story._
import com.thenetcircle.event_bus.story.tasks.http.HttpSink.RetrySender.Result
import com.thenetcircle.event_bus.{AppContext, BuildInfo}
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.{NoStackTrace, NonFatal}
import scala.util.{Failure, Random, Success, Try}

class UnexpectedResponseException(info: String) extends RuntimeException(info)

case class HttpSinkSettings(
    defaultRequest: HttpRequest,
    useRetrySender: Boolean = true,
    retrySenderSettings: RetrySenderSettings = RetrySenderSettings(),
    connectionPoolSettings: Option[ConnectionPoolSettings] = None,
    concurrentRequests: Int = 1,
    requestBufferSize: Int = 1000,
    expectedResponse: Option[String] = Some("ok"),
    allowExtraSignals: Boolean = true,
    requestContentType: ContentType.NonBinary = ContentTypes.`text/plain(UTF-8)`,
    useHttpsConnectionPool: Boolean = false,
    logRequestBody: Boolean = false
)

case class RetrySenderSettings(
    minBackoff: FiniteDuration = 1.second,
    maxBackoff: FiniteDuration = 30.seconds,
    randomFactor: Double = 0.2,
    retryDuration: FiniteDuration = 12.hours
)

class HttpSink(val settings: HttpSinkSettings) extends ISink with ITaskLogging {

  require(
    settings.defaultRequest.uri.scheme.nonEmpty && settings.defaultRequest.uri.authority.nonEmpty,
    "Default request scheme and target endpoint are required"
  )

  def normalSendingFlow()(
      implicit runningContext: TaskRunningContext
  ): Flow[Payload, Payload, StoryMat] =
    Flow[Payload]
      .mapAsync(settings.concurrentRequests) {
        case payload @ (NORMAL, _) => doNormalSend(payload)
        case others                => Future.successful(others)
      }

  def doNormalSend(payload: Payload)(implicit runningContext: TaskRunningContext): Future[Payload] = {
    implicit val executionContext: ExecutionContext = runningContext.getExecutionContext()

    val event = payload._2
    val req   = createHttpRequest(event)
    try {
      send(req)
        .flatMap(resp => checkResponse(req, resp))
        .map {
          case CheckResponseResult.Passed => (NORMAL, event)
          case result =>
            (
              STAGING(
                Some(new UnexpectedResponseException(s"Check response failed with result $result")),
                getTaskName()
              ),
              event
            )
        }
    } catch {
      case NonFatal(ex) =>
        taskLogger.warn(
          s"A event was sent to HTTP endpoint failed by by doNormalSend, ${getEventSummary(event)}, With error $ex"
        )
        Future.successful((FAILED(ex, getTaskName()), event))
    }
  }

  def retrySendingFlow()(
      implicit runningContext: TaskRunningContext
  ): Flow[Payload, Payload, StoryMat] = {
    initRetrySender()
    Flow[Payload]
      .watch(retrySender.get)
      .mapAsync(settings.concurrentRequests) {
        case payload @ (NORMAL, _) => doRetrySend(payload)
        case others                => Future.successful(others)
      }
  }

  def doRetrySend(
      payload: Payload
  )(implicit runningContext: TaskRunningContext): Future[Payload] = {

    val retryDuration                               = settings.retrySenderSettings.retryDuration
    implicit val askTimeout: Timeout                = Timeout(retryDuration)
    implicit val executionContext: ExecutionContext = runningContext.getExecutionContext()

    val event = payload._2
    val t0    = System.nanoTime()
    try {
      val request  = createHttpRequest(event)
      val endPoint = request.getUri().toString

      (retrySender.get ? RetrySender.Commands.Req(request, retryDuration.fromNow))
        .mapTo[Try[Result]]
        .map[(EventStatus, Event)] {
          case Success(Result(None)) =>
            val t1 = calRequestTime(t0)
            StoryMonitor(getStoryName()).onHttpSinkGetResponse(t1)
            taskLogger.info(
              s"A event was successfully sent to HTTP endpoint [$endPoint] in $t1 ms, ${getEventSummary(event)}"
            )
            (NORMAL, event)

          case Success(Result(Some(ex))) =>
            val t1 = calRequestTime(t0)
            StoryMonitor(getStoryName()).onHttpSinkGetResponse(t1)
            taskLogger.warn(
              s"A event was sent to HTTP endpoint [$endPoint] failed in $t1 ms because of unexpected status, ${getEventSummary(event)}, Throwable: $ex"
            )
            (STAGING(Some(ex), getTaskName()), event)

          case Failure(ex) =>
            taskLogger.warn(
              s"A event was sent to HTTP endpoint [$endPoint] failed in ${calRequestTime(t0)} ms, ${getEventSummary(event)}, With error $ex"
            )
            (FAILED(ex, getTaskName()), event)
        }
        .recover {
          case ex: AskTimeoutException =>
            taskLogger.warn(
              s"A event was sent to HTTP endpoint [$endPoint] timeout in ${calRequestTime(t0)} ms, exceed [$retryDuration], ${getEventSummary(event)}"
            )
            (FAILED(ex, getTaskName()), event)
        }
    } catch {
      case NonFatal(ex) =>
        taskLogger.warn(
          s"A event was sent to HTTP endpoint failed by doRetrySend in ${calRequestTime(t0)} ms, ${getEventSummary(event)}, With error $ex"
        )
        Future.successful((FAILED(ex, getTaskName()), event))
    }
  }

  override def sinkFlow()(
      implicit runningContext: TaskRunningContext
  ): Flow[Payload, Payload, StoryMat] = {
    initHttpSender()

    if (settings.useRetrySender)
      retrySendingFlow()
    else
      normalSendingFlow()
  }

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit = {
    taskLogger.info(s"Shutting down Http Sink.")
    retrySender.foreach(s => {
      runningContext.getActorSystem().stop(s)
      retrySender = None
    })
    httpSender.foreach(s => {
      s.complete()
      httpSender = None
    })
  }

  def createHttpRequest(event: Event): HttpRequest = {
    val req = settings.defaultRequest.withEntity(HttpEntity(settings.requestContentType, event.body.data))
    taskLogger.debug(s"Created a new Request ${req}.")
    return req
  }

  def send(request: HttpRequest)(implicit runningContext: TaskRunningContext): Future[HttpResponse] = {
    val responsePromise = Promise[HttpResponse]()
    httpSender.get
      .offer(request -> responsePromise)
      .flatMap {
        case QueueOfferResult.Enqueued =>
          responsePromise.future
        case QueueOfferResult.Failure(ex) => Future.failed(ex)
        case QueueOfferResult.Dropped =>
          Future.failed(new RequestException(s"HttpSender buffer is overflowed"))
        case QueueOfferResult.QueueClosed =>
          Future.failed(new RequestException(s"HttpSender Buffer was closed (pool shut down)"))
      }(runningContext.getExecutionContext())
  }

  def checkResponse(
      request: HttpRequest,
      response: HttpResponse
  )(implicit runningContext: TaskRunningContext): Future[CheckResponseResult] = {
    implicit val materializer: Materializer         = runningContext.getMaterializer()
    implicit val executionContext: ExecutionContext = runningContext.getExecutionContext()

    val status = response.status

    if (status.isSuccess()) {
      var requestBodyLog: String = ""
      if (settings.logRequestBody) {
        val theBody = request.entity match {
          case strict: HttpEntity.Strict =>
            strict.data.utf8String
          case _ =>
            "Unknown"
        }
        requestBodyLog = s"Request: $theBody"
      }

      if (settings.expectedResponse.isDefined) {
        Unmarshaller
          .byteStringUnmarshaller(response.entity)
          .map { _body =>
            val body = _body.utf8String
            taskLogger.info(
              s"[CheckResponse] Response: status_code ${status.value}, body $body. $requestBodyLog"
            )

            if (body.trim == settings.expectedResponse.get.trim)
              CheckResponseResult.Passed
            else if (settings.allowExtraSignals && CheckResponseResult.resolveExtraSignals(body.trim).isDefined)
              CheckResponseResult.resolveExtraSignals(body.trim).get
            else
              CheckResponseResult.UnexpectedBody
          }
      } else {
        Unmarshaller
          .byteStringUnmarshaller(response.entity)
          .map { _body =>
            val body = _body.utf8String
            taskLogger.info(
              s"[CheckResponse] Response status_code ${status.value}, body $body. $requestBodyLog"
            )
          }
        // response.discardEntityBytes()

        Future.successful(CheckResponseResult.Passed)
      }
    } else {
      taskLogger.warn(s"Get a response from upstream with non-200 [$status] status code")
      response.discardEntityBytes()
      Future.successful(CheckResponseResult.UnexpectedHttpCode)
    }
  }

  protected var httpSender: Option[SourceQueueWithComplete[(HttpRequest, Promise[HttpResponse])]] = None
  protected def initHttpSender()(implicit runningContext: TaskRunningContext): Unit = {
    if (httpSender.isDefined) return

    implicit val materializer: Materializer = runningContext.getMaterializer()

    val connectionPool = newHostConnectionPool()

    httpSender = Some(
      Source
        .queue[(HttpRequest, Promise[HttpResponse])](settings.requestBufferSize, OverflowStrategy.backpressure)
        .via(connectionPool)
        .toMat(Sink.foreach {
          case (Success(resp), p) => p.success(resp)
          case (Failure(e), p)    => p.failure(e)
        })(Keep.left)
        .run()
    )

    taskLogger.info(s"HttpSender is initialized.")
  }

  protected def newHostConnectionPool()(
      implicit runningContext: TaskRunningContext
  ): Flow[(HttpRequest, Promise[HttpResponse]), (Try[HttpResponse], Promise[HttpResponse]), Http.HostConnectionPool] = {
    implicit val materializer: Materializer = runningContext.getMaterializer()

    val http = Http()(runningContext.getActorSystem())
    val host = settings.defaultRequest.uri.authority.host.toString()
    val port = settings.defaultRequest.uri.effectivePort

    if (settings.connectionPoolSettings.isDefined) {
      val connectionPoolSettings = settings.connectionPoolSettings.get
      taskLogger.info(
        s"Initializing a new HttpSender of $host:$port with connection pool settings: $connectionPoolSettings"
      )
      if (settings.useHttpsConnectionPool) {
        http.newHostConnectionPoolHttps[Promise[HttpResponse]](host, port, settings = connectionPoolSettings)
      } else {
        http.newHostConnectionPool[Promise[HttpResponse]](host, port, settings = connectionPoolSettings)
      }
    } else {
      taskLogger.info(s"Initializing a new HttpSender of $host:$port")
      if (settings.useHttpsConnectionPool) {
        http.newHostConnectionPoolHttps[Promise[HttpResponse]](host, port)
      } else {
        http.newHostConnectionPool[Promise[HttpResponse]](host, port)
      }
    }
  }

  protected var retrySender: Option[ActorRef] = None
  protected def initRetrySender()(implicit runningContext: TaskRunningContext): Unit =
    if (retrySender.isEmpty) {
      retrySender = Some(
        runningContext
          .getActorSystem()
          .actorOf(
            Props(
              classOf[RetrySender],
              (request: HttpRequest) => this.send(request)(runningContext),
              (request: HttpRequest, response: HttpResponse) => this.checkResponse(request, response)(runningContext),
              settings.retrySenderSettings,
              taskLogger,
              runningContext
            ),
            "HttpSink-retrySender-" + getStoryName() + "-" + Random.nextInt()
          )
      )
    }

  // protected def calRequestTime(t0: Long): String = "%.3f".format((System.nanoTime().toFloat - t0.toFloat) / 1000000)
  protected def calRequestTime(t0: Long): Long        = (System.nanoTime() - t0) / 1000000
  protected def getEventSummary(event: Event): String = event.summary
}

object HttpSink {

  sealed trait CheckResponseResult
  object CheckResponseResult {
    case object Passed             extends CheckResponseResult
    case object UnexpectedHttpCode extends CheckResponseResult
    case object UnexpectedBody     extends CheckResponseResult
    case object Retry              extends CheckResponseResult
    case object ExpBackoffRetry    extends CheckResponseResult

    def resolveExtraSignals(signal: String): Option[CheckResponseResult] =
      signal.toLowerCase match {
        case "retry"                     => Some(Retry)
        case "exponential_backoff_retry" => Some(ExpBackoffRetry)
        case _                           => None
      }
  }

  class RequestException(msg: String) extends RuntimeException(msg) with NoStackTrace

  object RetrySender {
    object Commands {
      case class Req(request: HttpRequest, deadline: Deadline, retryTimes: Int = 1) {
        def retry(): Req = copy(retryTimes = retryTimes + 1)
      }
      case class ExpBackoffRetry(req: Req)
    }

    case class Result(ex: Option[Throwable])

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

  class RetrySender(
      sendFunc: HttpRequest => Future[HttpResponse],
      checkRespFunc: (HttpRequest, HttpResponse) => Future[CheckResponseResult],
      retrySettings: RetrySenderSettings,
      taskLogger: TaskLogger
  )(
      implicit runningContext: TaskRunningContext
  ) extends Actor {

    import RetrySender.Commands._

    implicit val executionContext: ExecutionContext = runningContext.getExecutionContext()

    def replyToReceiver(result: Try[Result], receiver: ActorRef): Unit = {
      taskLogger.debug(s"Replying response to http-sink")
      receiver ! result
    }

    def doExpBackoffRetry(req: Req, receiver: ActorRef): Unit = if (req.deadline.hasTimeLeft()) {
      val retryDelay = RetrySender.calculateDelay(
        req.retryTimes,
        retrySettings.minBackoff,
        retrySettings.maxBackoff,
        retrySettings.randomFactor
      )
      if (req.deadline.compare(retryDelay.fromNow) > 0)
        context.system.scheduler
          .scheduleOnce(retryDelay, self, req.retry())(executionContext, receiver)
    }

    def isRetriableException(ex: Throwable): Boolean = ex match {
      case _: StreamTcpException | _: TimeoutException | _: RequestTimeoutException        => true
      case _ if ex.getClass.getSimpleName.contains("UnexpectedConnectionClosureException") => true
      case _                                                                               => false
    }

    override def receive: Receive = {
      case req @ Req(request, _, retryTimes) =>
        val receiver   = sender()
        val requestUrl = request.getUri().toString
        sendFunc(request) andThen {
          case Success(resp) =>
            checkRespFunc(request, resp) andThen {
              case Success(CheckResponseResult.Passed) => replyToReceiver(Success(Result(None)), receiver)
              case Success(CheckResponseResult.UnexpectedHttpCode) =>
                replyToReceiver(
                  Success(
                    Result(
                      Some(
                        new UnexpectedResponseException(
                          s"Get a response from upstream with non-200 [${resp.status}] status code"
                        )
                      )
                    )
                  ),
                  receiver
                )
              case Success(CheckResponseResult.UnexpectedBody) =>
                replyToReceiver(
                  Success(Result(Some(new UnexpectedResponseException(s"The response body was not expected.")))),
                  receiver
                )
              case Success(CheckResponseResult.Retry) =>
                self.tell(req.retry(), receiver)
              case Success(CheckResponseResult.ExpBackoffRetry) =>
                taskLogger.info(s"Going to retry now since got ExpBackoffRetry signal from the endpoint")
                self.tell(ExpBackoffRetry(req), receiver)
              case Failure(ex) =>
                taskLogger.info(s"Checking a http response failed with error message: ${ex.getMessage}")
                try { resp.discardEntityBytes()(runningContext.getMaterializer()) } catch { case NonFatal(_) => }
                replyToReceiver(Success(Result(Some(ex))), receiver)
            }

          case Failure(ex) if isRetriableException(ex) =>
            if (retryTimes == 1)
              taskLogger.warn(
                s"Sending request to $requestUrl failed with error $ex, going to retry now."
              )
            else
              taskLogger.info(
                s"Resending request to $requestUrl failed with error $ex, retry-times is $retryTimes"
              )
            self.tell(ExpBackoffRetry(req), receiver)

          // TODO maybe think about retry a few times when get other exceptions
          case Failure(ex) =>
            taskLogger.error(s"Sending request to $requestUrl failed with error $ex, Set stream to failure.")
            replyToReceiver(Failure(ex), receiver)
        }

      case ExpBackoffRetry(req) =>
        doExpBackoffRetry(req, sender()) // not safe
    }
  }
}

class HttpSinkBuilder() extends ITaskBuilder[HttpSink] with Logging {

  override val taskType: String = "http"

  override def defaultConfig: Config =
    ConfigFactory.parseString(
      s"""{
      |  # this could be overwritten by event info later
      |  default-request {
      |    # uri = "http://www.google.com"
      |    method = POST
      |    protocol = "HTTP/1.1"
      |    headers {
      |      "user-agent": "event-bus/${BuildInfo.version}"
      |    }
      |  }
      |
      |  concurrent-requests = 1
      |  request-buffer-size = 1000
      |  expected-response = "ok"
      |  allow-extra-signals = true
      |  content-type = "text"
      |
      |  use-retry-sender = true
      |  retry-sender {
      |    min-backoff = 1 s
      |    max-backoff = 30 s
      |    random-factor = 0.2
      |    retry-duration = 12 h
      |  }
      |
      |  use-https-connection-pool = false
      |  log-request-body = false
      |
      |  # pool settings will override the default settings of akka.http.host-connection-pool
      |  pool {
      |    max-connections = 32
      |    min-connections = 0
      |    max-open-requests = 256
      |    # pipelining-limit = 1
      |    # idle-timeout = 30 s
      |    # ...
      |  }
      |}""".stripMargin
    )

  protected def buildHttpRequestFromConfig(config: Config)(implicit appContext: AppContext): HttpRequest = {
    val method = HttpMethods.getForKey(config.as[String]("method").toUpperCase).get
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
    val protocol = HttpProtocols.getForKey(config.as[String]("protocol").toUpperCase).get

    HttpRequest(method = method, uri = uri, headers = headers, protocol = protocol)
  }

  protected def buildConnectionPoolSettings(
      settings: Map[String, String]
  ): ConnectionPoolSettings = {
    var configString = settings.foldLeft("")((acc, kv) => acc + "\n" + s"${kv._1} = ${kv._2}")
    ConnectionPoolSettings(s"""akka.http.host-connection-pool {
                              |$configString
                              |}""".stripMargin)
  }

  protected def createHttpSinkSettings(config: Config)(implicit appContext: AppContext): HttpSinkSettings = {
    val defaultRequest: HttpRequest = buildHttpRequestFromConfig(config.getConfig("default-request"))
    val connectionPoolSettings =
      config.as[Option[Map[String, String]]]("pool").filter(_.nonEmpty).map(buildConnectionPoolSettings)

    val retrySenderConfig = config.getConfig("retry-sender")
    val retrySenderSettings = RetrySenderSettings(
      retrySenderConfig.as[FiniteDuration]("min-backoff"),
      retrySenderConfig.as[FiniteDuration]("max-backoff"),
      retrySenderConfig.as[Double]("random-factor"),
      retrySenderConfig.as[FiniteDuration]("retry-duration")
    )

    val mediaType = MediaTypes.forExtensionOption(config.getString("content-type")).getOrElse(MediaTypes.`text/plain`)
    // val contentType = ContentType(mediaType, () => HttpCharsets.`UTF-8`)
    val contentType = mediaType match {
      case x: MediaType.WithFixedCharset ⇒ ContentType(x)
      case x: MediaType.WithOpenCharset  ⇒ ContentType(x, HttpCharsets.`UTF-8`)
    }

    HttpSinkSettings(
      defaultRequest,
      config.as[Boolean]("use-retry-sender"),
      retrySenderSettings,
      connectionPoolSettings,
      config.as[Int]("concurrent-requests"),
      config.as[Int]("request-buffer-size"),
      config.as[Option[String]]("expected-response").filter(_.trim != ""),
      config.as[Boolean]("allow-extra-signals"),
      requestContentType = contentType,
      useHttpsConnectionPool = config.as[Boolean]("use-https-connection-pool"),
      logRequestBody = config.as[Boolean]("log-request-body")
    )
  }

  override def buildTask(
      config: Config
  )(implicit appContext: AppContext): HttpSink =
    try {
      new HttpSink(createHttpSinkSettings(config))
    } catch {
      case ex: Throwable =>
        logger.error(s"Build HttpSink failed with error: ${ex.getMessage}")
        throw ex
    }
}
