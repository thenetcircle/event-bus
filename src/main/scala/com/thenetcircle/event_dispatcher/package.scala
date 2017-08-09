package com.thenetcircle

import akka.util.ByteString

import scala.concurrent.Future

package object event_dispatcher {

  sealed trait EventFmt
  object EventFmt {
    case class Plain() extends EventFmt
    case class Json() extends EventFmt
    case class ActivityStreams() extends EventFmt
  }

  case class RawEvent(
      body: ByteString,
      channel: String,
      context: Map[String, Any]
  ) {
    def hasContext(key: String): Boolean = context.isDefinedAt(key)
    def addContext(key: String, value: Any): RawEvent = copy(context = context + (key -> value))
  }

  trait EventCommitter {
    def commit(): Future[_]
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
      committer: Option[EventCommitter] = None
  )

  case class BizData(
      sessionId: Option[String] = None,
      provider: Option[String] = None,
      category: Option[String] = None,
      actorId: Option[String] = None,
      actorType: Option[String] = None
  )

}
