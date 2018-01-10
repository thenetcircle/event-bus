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
import com.thenetcircle.event_bus.story.BuilderFactory
import com.thenetcircle.event_bus.{Environment, RunningContext}
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

abstract class AkkaStreamTest(_env: Environment)
    extends TestKit(ActorSystem(_env.getAppName(), _env.getConfig()))
    with ImplicitSender
    with UnitTest {

  implicit val defaultTimeOut: FiniteDuration = 3.seconds
  implicit val environment: Environment = _env

  implicit val materializer: ActorMaterializer = ActorMaterializer(
    ActorMaterializerSettings(system).withInputBuffer(initialSize = 1, maxSize = 1)
  )
  implicit val executor: ExecutionContext =
    materializer.executionContext

  implicit val runningContext: RunningContext =
    new RunningContext(
      environment,
      system,
      materializer,
      executor,
      BuilderFactory(environment.getConfig())
    )

  def this() = {
    this(new Environment("event-bus-test", "2.x", "test", true, ConfigFactory.load()))
  }

  override def beforeAll(): Unit = {}

  override def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

}
