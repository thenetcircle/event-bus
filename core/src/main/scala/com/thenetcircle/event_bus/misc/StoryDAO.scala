package com.thenetcircle.event_bus.misc

import com.thenetcircle.event_bus.context.AppContext
import com.thenetcircle.event_bus.story.StoryInfo
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._

trait StoryDAO {
  def getRunnableStories(runnerName: String): List[String]
  def getStoryInfo(storyName: String): StoryInfo
}

class StoryZookeeperDAO(zkManager: ZKManager)(implicit appContext: AppContext) extends StoryDAO {
  // TODO: watch new stories, and story changes
  def getRunnableStories(runnerName: String): List[String] = {
    zkManager
      .getChildren(s"runners/$runnerName/stories")
      .getOrElse(List.empty[String])
  }

  def getStoryInfo(storyName: String): StoryInfo = {
    val storyRootPath = s"stories/$storyName"

    val status: String = zkManager.getData(s"$storyRootPath/status").getOrElse("INIT")
    val settings: String = zkManager.getData(s"$storyRootPath/settings").getOrElse("")
    val source: String = zkManager.getData(s"$storyRootPath/source").get
    val sink: String = zkManager.getData(s"$storyRootPath/sink").get
    val transforms: Option[String] = zkManager.getData(s"$storyRootPath/transforms")
    val fallback: Option[String] = zkManager.getData(s"$storyRootPath/fallback")

    StoryInfo(storyName, status, settings, source, sink, transforms, fallback)
  }
}

object StoryZookeeperDAO {
  def apply(zkManager: ZKManager)(implicit appContext: AppContext): StoryZookeeperDAO =
    new StoryZookeeperDAO(zkManager)
}

/*
 * config example:
 * ```
 * [
 *   {
 *     # "name": "..."
 *     # "status": "..."
 *     # "settings": "..."
 *     # "source": "..."
 *     # "sink": "..."
 *     # "transformTasks": [
 *     #   ["op-type", "settings"],
 *     #   ...
 *     # ]
 *     # "sinkTask": ["sinkTask-type", "settings"]
 *     # "fallbackTask": [
 *       ["sinkTask-type", "settings"]
 *     ]
 *   },
 *   ...
 * ]
 * ```
 */
class StoryConfigDAO(config: Config) extends StoryDAO {
  config.checkValid(ConfigFactory.defaultReference, "story")

  val storyConfigs: List[Config] = config.as[List[Config]]("story")
  val stories: List[StoryInfo] = storyConfigs.map(
    cf =>
      StoryInfo(
        cf.as[String]("name"),
        cf.as[String]("status"),
        cf.as[String]("settings"),
        cf.as[String]("source"),
        cf.as[String]("sink"),
        cf.as[Option[String]]("transforms"),
        cf.as[Option[String]]("fallback")
    )
  )

  override def getRunnableStories(runnerName: String): List[String] = stories.map(_.name)

  override def getStoryInfo(storyName: String): StoryInfo = {
    stories.find(info => info.name == storyName).get
  }

}

object StoryConfigDAO {
  def apply(config: Config): StoryConfigDAO = new StoryConfigDAO(config)
}