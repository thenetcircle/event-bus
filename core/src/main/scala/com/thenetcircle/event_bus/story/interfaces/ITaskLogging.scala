package com.thenetcircle.event_bus.story.interfaces

import com.thenetcircle.event_bus.misc.Logging
import com.thenetcircle.event_bus.story.TaskLogger

trait ITaskLogging extends Logging { _: ITask =>
  protected lazy val taskLogger: TaskLogger = TaskLogger(getStoryName(), logger)
}
