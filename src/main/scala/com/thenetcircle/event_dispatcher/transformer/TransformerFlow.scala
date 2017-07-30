package com.thenetcircle.event_dispatcher.transformer
import akka.NotUsed
import akka.stream.javadsl.BidiFlow
import akka.stream.scaladsl.Flow
import com.thenetcircle.event_dispatcher.Event

object TransformerFlow
{
  def apply[T](adapter: Adapter[T], extractor: Extractor): BidiFlow[T, Event, Event, T, NotUsed] =
  {
    // Transform source data to Event
    val flow1 = Flow[T].map(adapter.adapt _).map(extractor.extract _)

    // Transform Event to sink data
    val flow2 = Flow[Event].map(extractor.deExtract _).map(adapter.deAdapt _)

    BidiFlow.fromFlows(flow1, flow2)
  }
}
