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

package com.thenetcircle.event_bus.base

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

abstract class AkkaStreamTest(_system: ActorSystem)
    extends TestKit(_system)
    with ImplicitSender
    with UnitTest {

  implicit val materializer: ActorMaterializer = ActorMaterializer(
    ActorMaterializerSettings(_system).withInputBuffer(initialSize = 1, maxSize = 1)
  )

  implicit val defaultTimeOut: FiniteDuration = 3.seconds

  implicit val executionContext: ExecutionContext =
    materializer.executionContext

  def this() =
    this(ActorSystem("eventbus-test", ConfigFactory.load("application-test.conf")))

  override def beforeAll(): Unit = {
    /*PipelinePool.init(
      _system.settings.config
        .as[List[Config]]("event-bus.runtime.pipeline-pool")
    )
    Tracer.init(_system)*/
  }

  override def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

}
