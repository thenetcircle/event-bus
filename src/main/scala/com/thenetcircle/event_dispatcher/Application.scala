package com.thenetcircle.event_dispatcher
import com.thenetcircle.event_dispatcher.source.RedisPubSubSource

object Application extends App {
  implicit val akkaSystem = akka.actor.ActorSystem()

}
