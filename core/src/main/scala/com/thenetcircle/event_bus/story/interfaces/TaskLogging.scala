package com.thenetcircle.event_bus.story.interfaces
import com.thenetcircle.event_bus.misc.Logging
import com.thenetcircle.event_bus.story.StoryLogger

trait TaskLogging extends Logging { _: ITask =>
  protected lazy val storyLogger: StoryLogger =
    StoryLogger(getStoryName())
}
