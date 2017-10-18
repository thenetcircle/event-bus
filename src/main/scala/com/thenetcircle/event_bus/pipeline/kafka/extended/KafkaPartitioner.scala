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

package com.thenetcircle.event_bus.pipeline.kafka.extended

import java.util

import com.thenetcircle.event_bus.Event
import org.apache.kafka.clients.producer.Partitioner
import org.apache.kafka.common.utils.Utils
import org.apache.kafka.common.{Cluster, PartitionInfo}

import scala.util.Random

class KafkaPartitioner extends Partitioner {
  override def partition(topic: String,
                         key: scala.Any,
                         keyBytes: Array[Byte],
                         value: scala.Any,
                         valueBytes: Array[Byte],
                         cluster: Cluster): Int = {
    val partitions: util.List[PartitionInfo] = cluster.partitionsForTopic(topic)
    val numPartitions: Int                   = partitions.size

    val event = value.asInstanceOf[Event]
    val keyBytes = event.metadata.actor
      .map(actor => s"${actor.objectType}-${actor.id}")
      .getOrElse(Random.nextString(6))
      .getBytes("UTF-8")

    Utils.toPositive(Utils.murmur2(keyBytes)) % numPartitions
  }

  override def configure(configs: util.Map[String, _]): Unit = {}
  override def close(): Unit                                 = {}
}
