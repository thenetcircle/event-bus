package com.thenetcircle.event_dispatcher.pipeline
import akka.kafka.ConsumerSettings
import com.thenetcircle.event_dispatcher.AkkaTestCase

class KafkaPipelineTest extends AkkaTestCase {

  val consumerSettings = ConsumerSettings()

  val plSettings = KafkaPipelineSettings(
    )
  val pl = KafkaPipeline()

}
