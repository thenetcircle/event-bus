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

package com.thenetcircle.event_bus.story.tasks.operators

import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.AppContext
import com.thenetcircle.event_bus.misc.Logging
import com.thenetcircle.event_bus.story.interfaces.{IFailoverTask, ITaskBuilder, IUndiOperator}
import com.thenetcircle.event_bus.story.{Payload, StoryMat, TaskRunningContext}
import com.typesafe.config.{Config, ConfigFactory}

case class FileStageSettings(contactPoints: List[String], port: Int = 9042, parallelism: Int = 2)

class FileStageOperator(val settings: FileStageSettings) extends IUndiOperator with IFailoverTask with Logging {

  override def flow()(
      implicit runningContext: TaskRunningContext
  ): Flow[Payload, Payload, StoryMat] = {}

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit = {}
}

class FileStageOperatorBuilder() extends ITaskBuilder[FileStageOperator] {

  override val taskType: String = "file-stage"

  override val defaultConfig: Config =
    ConfigFactory.parseString(
      """{
        |  contact-points = []
        |  port = 9042
        |  parallelism = 3
        |}""".stripMargin
    )

  override def buildTask(
      config: Config
  )(implicit appContext: AppContext): FileStageOperator = {}

}
