package com.thenetcircle.event_bus

import akka.util.ByteString

import scala.concurrent.Future

sealed trait EventFmt
object EventFmt {
  case class Plain() extends EventFmt
  case class Json() extends EventFmt
  case class ActivityStreams() extends EventFmt
}

sealed trait EventSource
object EventSource {
  object Redis extends EventSource
  object AMQP extends EventSource
  object Kafka extends EventSource
  object Http extends EventSource
}

case class RawEvent(
    body: ByteString,
    channel: String,
    context: Map[String, Any],
    source: EventSource
) {
  def hasContext(key: String): Boolean = context.isDefinedAt(key)
  def addContext(key: String, value: Any): RawEvent = copy(context = context + (key -> value))
}

trait EventCommitter[+A] {
  def commit(): Future[A]
}

/**
 * deliveredTimes?
 */
case class Event(
    uuid: String,
    timestamp: Long,
    rawEvent: RawEvent,
    bizData: BizData,
    format: EventFmt,
    committer: Option[EventCommitter[_]] = None
) {
  def withCommitter[A](committerBuilder: => Future[A]): Event =
    copy(committer = Some(new EventCommitter[A] {
      override def commit(): Future[A] = committerBuilder
    }))
}

case class BizData(
    sessionId: Option[String] = None,
    provider: Option[String] = None,
    category: Option[String] = None,
    actorId: Option[String] = None,
    actorType: Option[String] = None
)
