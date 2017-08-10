package com.thenetcircle.event_bus.pipeline
import akka.kafka.ConsumerSettings
import com.thenetcircle.event_bus.AkkaTestCase

class KafkaPipelineTest extends AkkaTestCase {

  val consumerSettings = ConsumerSettings()

  val plSettings = KafkaPipelineSettings(
    )
  val pl = KafkaPipeline()

}
