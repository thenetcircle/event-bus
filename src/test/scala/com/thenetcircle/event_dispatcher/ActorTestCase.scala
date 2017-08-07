package com.thenetcircle.event_dispatcher

import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.testkit.{ ImplicitSender, TestKit }

import scala.concurrent.duration._

abstract class ActorTestCase(val _system: ActorSystem) extends TestKit(_system) with ImplicitSender with TestCase {

  implicit val materializer: ActorMaterializer = ActorMaterializer(
    ActorMaterializerSettings(_system).withInputBuffer(initialSize = 1, maxSize = 1)
  )

  implicit val defaultTimeOut: FiniteDuration = 3.seconds

  def this() = this(ActorSystem("beineng-test"))

  override def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

}
