package com.thenetcircle.event_bus
import com.thenetcircle.event_bus.source.RedisPubSubSource

object Application extends App {
  implicit val akkaSystem = akka.actor.ActorSystem()

}
