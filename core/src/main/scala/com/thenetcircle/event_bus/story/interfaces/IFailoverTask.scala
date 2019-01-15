package com.thenetcircle.event_bus.story.interfaces

import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.story.{Payload, StoryMat, TaskRunningContext}

trait IFailoverTask extends ITask {

  def failoverFlow()(
      implicit runningContext: TaskRunningContext
  ): Flow[Payload, Payload, StoryMat]

}
