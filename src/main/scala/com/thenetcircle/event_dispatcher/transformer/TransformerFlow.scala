package com.thenetcircle.event_dispatcher.transformer
import akka.NotUsed
import akka.stream.javadsl.BidiFlow
import akka.stream.scaladsl.Flow
import com.thenetcircle.event_dispatcher.{Event, RawEvent}

object TransformerFlow {
  def apply[In, Out](extractor: Extractor)(implicit adapter: Adapter[In, Out])
    : BidiFlow[In, Event, Event, Out, NotUsed] = {

    // Transform raw data to Event
    val flow1 = Flow[In].map(adapter.adapt).map(extractor.extract)

    // Transform Event to raw data
    val flow2 =
      Flow[Event].map(_.rawEvent).map(adapter.deAdapt)

    BidiFlow.fromFlows(flow1, flow2)

  }
}
