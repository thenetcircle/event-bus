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

package com.thenetcircle.event_bus.pipeline

import com.thenetcircle.event_bus.testkit.AkkaTestSpec

class KafkaPipelineSpec extends AkkaTestSpec {

  /*val producerSettings = ProducerSettings[KafkaKey, KafkaValue](
    _system,
    Some(new ByteArraySerializer),
    Some(new ByteArraySerializer)
  )

  val consumerSettings = ConsumerSettings[KafkaKey, KafkaValue](
    _system,
    Some(new ByteArrayDeserializer),
    Some(new ByteArrayDeserializer)
  )

  val plSettings = KafkaPipelineSettings(consumerSettings, producerSettings)
  val pl = KafkaPipeline(plSettings)

  val plInlet = pl.inlet()

  test("push data in kafkapipeline") {
    Source[Int](1 to 5)
      .map(
        i =>
          Event(
            i.toString,
            System.currentTimeMillis(),
            RawEvent(
              ByteString(s"test-body$i"),
              s"test-channel$i",
              Map("uid" -> i),
              EventSourceType.Http
            ),
            BizData(),
            EventFormat.Plain()
        )
      )
      .runWith(plInlet)

    Await.ready(_system.whenTerminated, Duration.Inf)
  }*/

}
