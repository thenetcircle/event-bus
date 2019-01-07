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
import akka.testkit.{ImplicitSender, TestActors, TestKit}
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.event.extractor.EventExtractorFactory
import com.thenetcircle.event_bus.misc.ZKManager
import com.thenetcircle.event_bus.story.{StoryBuilder, TaskRunningContext}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

abstract class IntegrationTestBase(_appContext: AppContext)
    extends TestKit(ActorSystem(_appContext.getAppName(), _appContext.getSystemConfig()))
    with ImplicitSender
    with FlatSpecLike
    with Matchers
    with BeforeAndAfterAll
    with StrictLogging {

  // implicit val defaultTimeOut: FiniteDuration = 3.seconds
  implicit val appContext: AppContext = _appContext
  val config: Config                  = appContext.getSystemConfig()

  private val _decider: Supervision.Decider = {
    case ex: Throwable =>
      logger.error(s"materializer supervision triggered by exception: $ex")
      Supervision.Stop
  }
  lazy implicit val materializer: ActorMaterializer = ActorMaterializer(
    ActorMaterializerSettings(system)
      .withSupervisionStrategy(_decider) //.withInputBuffer(initialSize = 1, maxSize = 1)
  )

  lazy implicit val executionContext: ExecutionContext = materializer.executionContext

  lazy implicit val runningContext: TaskRunningContext =
    new TaskRunningContext(
      appContext,
      system,
      materializer,
      executionContext,
      system.actorOf(TestActors.blackholeProps)
    )

  lazy val storyBuilder: StoryBuilder = new StoryBuilder()

  private val _config = appContext.getSystemConfig()
  lazy val zkManager: ZKManager = {
    val connectString = _config.getString("app.zookeeper.servers")
    val rootPath      = _config.getString("app.zookeeper.rootpath") + s"/${appContext.getAppName()}/${appContext.getAppEnv()}"
    val _zkManager    = new ZKManager(connectString, rootPath)
    appContext.addShutdownHook(_zkManager.close())
    appContext.setZKManager(_zkManager)
    _zkManager
  }

  def this() = {
    this(new AppContext("integration-test", "test", ConfigFactory.load()))
  }

  override def afterAll(): Unit = {
    appContext.shutdown()
    TestKit.shutdownActorSystem(system)
  }

  def createTestEvent(
      body: String = s"""{
          |"id": "TestEvent-${java.util.UUID.randomUUID().toString}"
          |}""".stripMargin,
      passThrough: Option[Any] = None
  ): Event =
    Await.result(EventExtractorFactory.defaultExtractor.extract(body.getBytes(), passThrough), 1.second)

}
