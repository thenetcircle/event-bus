package com.thenetcircle.event_bus.misc

import com.thenetcircle.event_bus.story.StoryDAO

trait DaoFactory {
  def getStoryDAO(): StoryDAO
}

object DaoFactory {
  def apply(zKManager: ZKManager): DaoFactory = new ZKDaoFactory(zKManager)
}

class ZKDaoFactory(zkManager: ZKManager) extends DaoFactory {

  override def getStoryDAO(): StoryDAO = StoryDAO(zkManager)

}
