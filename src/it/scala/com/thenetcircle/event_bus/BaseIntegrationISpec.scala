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

package com.thenetcircle.event_bus

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.testkit.{ImplicitSender, TestKit}
import com.thenetcircle.event_bus.pipeline.PipelinePool
import com.thenetcircle.event_bus.tracing.Tracer
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest._
import net.ceedubs.ficus.Ficus._

import scala.concurrent.duration.{FiniteDuration, _}

abstract class BaseIntegrationISpec(_system: ActorSystem)
    extends TestKit(_system)
    with ImplicitSender
    with AsyncFlatSpecLike
    with Matchers
    with BeforeAndAfter
    with BeforeAndAfterAll
    with Inside {

  implicit val materializer: ActorMaterializer = ActorMaterializer(
    ActorMaterializerSettings(_system).withInputBuffer(initialSize = 1,
                                                       maxSize = 1)
  )

  implicit val defaultTimeOut: FiniteDuration = 3.seconds

  /*implicit val executionContext: ExecutionContext =
    materializer.executionContext*/

  def this() =
    this(
      ActorSystem("eventbus-integration-test",
                  ConfigFactory.load("application-integration-test.conf")))

  override protected def beforeAll(): Unit = {
    PipelinePool.init(
      _system.settings.config
        .as[List[Config]]("event-bus-runtime.pipeline-pool"))
    Tracer.init(_system)
  }

  override protected def afterAll(): Unit = {
    // TODO: terminate host-connection-pool and all transporters and dispatchers
    TestKit.shutdownActorSystem(system)
  }

}
