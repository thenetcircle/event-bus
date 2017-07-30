package com.thenetcircle.event_dispatcher.event
import akka.NotUsed
import akka.stream.scaladsl.Flow

object EventExtractFlow {
  def apply(extracter: EventExtracter): Flow[UnExtractedEvent, Event, NotUsed] =
    Flow[UnExtractedEvent].map(event => extracter.extract(event))

  def apply(settings: EventExtracterSettings): Flow[UnExtractedEvent, Event, NotUsed] =
    apply(EventExtracterFactory.fromSettings(settings))
}
