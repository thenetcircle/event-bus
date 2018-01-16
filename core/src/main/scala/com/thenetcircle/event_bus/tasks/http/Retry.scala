package com.thenetcircle.event_bus.tasks.http

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import akka.NotUsed
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL}
import akka.stream.stage._
import com.thenetcircle.event_bus.event.Event

import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object Retry {

  def apply(maxRetryTimes: Int, checkFunc: (HttpResponse) => Future[Boolean])(
      logic: Flow[(HttpRequest, Event), (Try[HttpResponse], Event), NotUsed]
  ): Flow[(HttpRequest, Event), (Try[HttpResponse], Event), NotUsed] = {
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val retry = builder.add(new RetryStage[Event](maxRetryTimes, checkFunc))

      // workflow (check if the result is expected, otherwise will retry)
      // format: off

            retry.ready ~> logic ~> retry.checkpoint

            // format: on

      FlowShape(retry.input, retry.output)

    })
  }

  final class RetryStage[I, O, P](maxRetryTimes: Int, checkFunc: (O) => Future[Boolean])(
      implicit executionContext: ExecutionContext
  ) extends GraphStage[RetryShape[(I, P), (Try[O], P), (I, P), (Try[O], P)]] {

    val input: Inlet[(I, P)] = Inlet("input")
    val checkpoint: Inlet[(Try[O], P)] = Inlet("checkpoint")
    val ready: Outlet[(I, P)] = Outlet("ready")
    val output: Outlet[(Try[O], P)] = Outlet("output")

    override def shape: RetryShape[(I, P), (Try[O], P), (I, P), (Try[O], P)] =
      RetryShape(input, checkpoint, ready, output)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with StageLogging {
        val retryTimes: AtomicInteger = new AtomicInteger(0)

        val isPending: AtomicBoolean = new AtomicBoolean(false)
        var isWaitingPull: Boolean = false

        setHandler(
          input,
          new InHandler {
            override def onPush() = {
              log.debug(s"onPush incoming -> push ready")
              push(ready, grab(input))
              isPending.set(true)
            }

            override def onUpstreamFinish() = {
              log.debug("onUpstreamFinish")
              if (!isPending.get()) {
                log.debug("completeStage")
                completeStage()
              }
            }
          }
        )

        setHandler(
          ready,
          new OutHandler {
            override def onPull() = {
              log.debug("onPull ready")
              if (!isPending.get()) {
                log.debug("tryPull incoming")
                tryPull(input)
              } else {
                log.debug("set waitingPull to true")
                isWaitingPull = true
              }
            }
          }
        )

        setHandler(
          checkpoint,
          new InHandler {
            override def onPush() = {
              log.debug("onPush result")
              grab(checkpoint) match {
                case (resultTry, payload) =>
                  resultTry match {
                    case Success(result) =>
                      checkFunc(result).onComplete(
                        getAsyncCallback(checkCallback(resultTry, payload)).invoke
                      )
                    case Failure(ex) =>
                      log.error(s"check failed with error: $ex.")
                      processFailure(payload)
                  }
              }
            }
          }
        )

        setHandler(output, new OutHandler {
          override def onPull() = {
            log.debug("onPull output")
            if (!hasBeenPulled(checkpoint)) {
              log.debug("tryPull checker")
              tryPull(checkpoint)
            }
          }
        })

        def checkCallback(resultTry: Try[O], payload: P): (Try[Boolean]) => Unit = {
          case Success(true) =>
            pushResult((resultTry, payload))
          case Success(false) =>
            processFailure(payload)
          case Failure(ex) =>
            log.warning(s"Parse response error: ${ex.getMessage}")
            pushResult((resultTry, payload))
        }

        def processFailure(payload: P): Unit = {
          if (retryTimes.incrementAndGet() >= maxRetryTimes) {
            log.warning(s"Event sent failed after retried $maxRetryTimes times.")
            pushResult(payload)
          } else {
            log.debug("emit ready & tryPull result")
            emit(ready, payload)
            tryPull(checkpoint)
          }
        }

        def pushResult(result: (Try[O], P)): Unit = {
          push(output, result)
          isPending.set(false)
          retryTimes.set(0)
          if (isClosed(input)) completeStage()
          else if (isWaitingPull) tryPull(input)
        }
      }
  }

  final case class RetryShape[A, B, C, D](input: Inlet[A],
                                          checkpoint: Inlet[B],
                                          ready: Outlet[C],
                                          output: Outlet[D])
      extends Shape {

    override val inlets: Seq[Inlet[_]] = input :: checkpoint :: Nil
    override val outlets: Seq[Outlet[_]] = ready :: output :: Nil

    override def deepCopy(): Shape = RetryShape(input, checkpoint, ready, output)
  }

}
