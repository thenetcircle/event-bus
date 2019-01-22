package com.thenetcircle.event_bus.story.interfaces

import com.thenetcircle.event_bus.misc.Logging
import com.typesafe.scalalogging.Logger

trait ITaskLogging extends Logging { _: ITask =>
  protected lazy val taskLoggingPrefix  = s"[${getStoryName()}]"
  protected lazy val taskLogger: Logger = logger
}
