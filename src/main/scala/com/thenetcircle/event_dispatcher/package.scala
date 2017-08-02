package com.thenetcircle

import akka.util.ByteString

package object event_dispatcher {

  trait SourceAdapter[In] {
    def fit(message: In): RawEvent
  }
  trait SinkAdapter[Out] {
    def unfit(event: RawEvent): Out
  }

  sealed trait EventFmt
  object EventFmt {
    object Plain extends EventFmt
    object Json extends EventFmt
    object ActivityStreams extends EventFmt
  }

  case class RawEvent(
      body: ByteString,
      context: Map[String, Any],
      channel: Option[String] = None
  )

  case class BizData(
      sessionId: Option[String] = None,
      provider: Option[String] = None,
      category: Option[String] = None,
      actorId: Option[String] = None,
      actorType: Option[String] = None
  )

  case class Event(
      uuid: String,
      timestamp: Long,
      rawEvent: RawEvent,
      bizData: BizData,
      format: EventFmt
  )

}
