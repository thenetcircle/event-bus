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

package com.thenetcircle.event_bus.story

import com.thenetcircle.event_bus.misc.DaoFactory
import com.thenetcircle.event_bus.story.StoryDAO.StoryInfo

class StoryManager(
    daoFactory: DaoFactory,
    taskBuilderFactory: TaskBuilderFactory,
    taskContextFactory: TaskContextFactory
)(implicit environment: ExecutionEnvironment) {

  val storyDAO = daoFactory.getStoryDAO()

  def execute(): Unit = {}

  def getStories(): List[Story] = {
    val availableStories: List[StoryInfo] =
      storyDAO.getAvailableStories(environment.getExecutorGroup())

    availableStories.map(info => {
      implicit val taskContext: TaskContext = taskContextFactory.newTaskExecutingContext()
      new Story(
        info.name,
        StorySettings(),
        taskBuilderFactory.buildTaskA(info.source).get,
        info.transforms.flatMap(_v => taskBuilderFactory.buildTaskB(_v)),
        info.sink.flatMap(_v => taskBuilderFactory.buildTaskC(_v)),
        info.fallbacks.flatMap(_v => taskBuilderFactory.buildFallbacks(_v)),
        StoryStatus(info.status)
      )
    })
  }

}

object StoryManager {
  def apply(
      daoFactory: DaoFactory,
      taskBuilderFactory: TaskBuilderFactory,
      taskContextFactory: TaskContextFactory
  )(implicit environment: ExecutionEnvironment): StoryManager =
    new StoryManager(daoFactory, taskBuilderFactory, taskContextFactory)
}
