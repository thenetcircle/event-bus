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
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import com.thenetcircle.event_bus.context.{AppContext, TaskBuildingContext, TaskRunningContext}
import com.thenetcircle.event_bus.event.EventImpl
import com.thenetcircle.event_bus.event.extractor.DataFormat
import com.thenetcircle.event_bus.interfaces.{Event, EventBody, EventMetaData}
import com.thenetcircle.event_bus.story.{StoryBuilder, StorySettings, TaskBuilderFactory}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import akka.testkit.{ImplicitSender, TestActors, TestKit}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

abstract class BaseTest(_appContext: AppContext)
    extends TestKit(ActorSystem(_appContext.getAppName(), _appContext.getSystemConfig()))
    with ImplicitSender
    with FlatSpecLike
    with Matchers
    with BeforeAndAfterAll
    with StrictLogging {

  implicit val defaultTimeOut: FiniteDuration = 3.seconds
  implicit val appContext: AppContext         = _appContext

  val decider: Supervision.Decider = {
    case ex: Throwable =>
      logger.error(s"materializer supervision triggered by exception: $ex")
      Supervision.Stop
  }
  implicit val materializer: ActorMaterializer = ActorMaterializer(
    ActorMaterializerSettings(system)
      .withSupervisionStrategy(decider) //.withInputBuffer(initialSize = 1, maxSize = 1)
  )

  implicit val testExecutionContext: ExecutionContext = materializer.executionContext

  implicit val runningContext: TaskRunningContext =
    new TaskRunningContext(
      appContext,
      system,
      materializer,
      testExecutionContext,
      "TestStoryRunner",
      system.actorOf(TestActors.blackholeProps),
      StorySettings("teststory")
    )

  implicit val taskBuildingContext: TaskBuildingContext = new TaskBuildingContext(appContext)
  val storyBuilder: StoryBuilder                        = StoryBuilder(TaskBuilderFactory(appContext.getSystemConfig()))

  def this() = {
    this(new AppContext("event-bus-test", "2.x", "test", true, ConfigFactory.load()))
  }

  override def beforeAll(): Unit = {}

  override def afterAll(): Unit = {
    appContext.shutdown()
    TestKit.shutdownActorSystem(system)
  }

  def createTestEvent(name: String = "TestEvent", body: String = "body"): Event =
    EventImpl(
      uuid = "TestEvent-" + java.util.UUID.randomUUID().toString,
      metadata = EventMetaData(name = Some(name)),
      body = EventBody(body, DataFormat.UNKNOWN)
    )

}
