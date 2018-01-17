package com.thenetcircle.event_bus.story

import com.thenetcircle.event_bus.context.AppContext
import com.thenetcircle.event_bus.helper.ZookeeperManager

case class StoryInfo(name: String,
                     status: String,
                     settings: String,
                     source: String,
                     sink: String,
                     transforms: Option[List[String]],
                     fallbacks: Option[List[String]])

trait StoryDAO {
  def getStoriesByRunnerName(runnerName: String): List[StoryInfo]
  def getStoryInfo(storyName: String): StoryInfo
}

class StoryZookeeperDAO(zkManager: ZookeeperManager)(implicit appContext: AppContext)
    extends StoryDAO {
  def getStoriesByRunnerName(runnerName: String): List[StoryInfo] = {
    zkManager
      .getChildren("stories")
      .map(_.filter(storyName => {
        zkManager
          .getData(s"stories/$storyName/runner")
          .getOrElse(appContext.getDefaultRunnerName()) == runnerName
      }).map(getStoryInfo))
      .getOrElse(List.empty[StoryInfo])
  }

  def getStoryInfo(storyName: String): StoryInfo = {
    val storyRootPath = s"stories/$storyName"

    val status: String = zkManager.getData(s"$storyRootPath/status").get
    val settings: String = zkManager.getData(s"$storyRootPath/settings").get
    val source: String = zkManager.getData(s"$storyRootPath/source").get
    val sink: String = zkManager.getData(s"$storyRootPath/sink").get
    val transforms: Option[List[String]] =
      zkManager.getChildrenData(s"$storyRootPath/transforms").map(_.map(_._2))
    val fallbacks: Option[List[String]] =
      zkManager.getChildrenData(s"$storyRootPath/fallbacks").map(_.map(_._2))

    StoryInfo(storyName, status, settings, source, sink, transforms, fallbacks)
  }
}

object StoryZookeeperDAO {
  def apply(zkManager: ZookeeperManager)(implicit appContext: AppContext): StoryZookeeperDAO =
    new StoryZookeeperDAO(zkManager)
}
