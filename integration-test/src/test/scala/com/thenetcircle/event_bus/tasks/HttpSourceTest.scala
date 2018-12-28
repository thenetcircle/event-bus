package com.thenetcircle.event_bus.tasks

import akka.NotUsed
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.IntegrationTestBase
import com.thenetcircle.event_bus.interfaces.EventStatus.{FAIL, NORM}
import com.thenetcircle.event_bus.interfaces.{Event, EventStatus}
import com.thenetcircle.event_bus.tasks.http.{HttpSource, HttpSourceSettings}
import org.scalatest.BeforeAndAfter

import scala.concurrent.Await
import scala.concurrent.duration._

class HttpSourceTest extends IntegrationTestBase with BeforeAndAfter {

  behavior of "HttpSource"

  def request(
      handler: Flow[(EventStatus, Event), (EventStatus, Event), NotUsed],
      requestData: String,
      port: Int = 55661
  ): (StatusCode, String) = {
    val settings   = HttpSourceSettings(interface = "127.0.0.1", port = port, succeededResponse = "ok")
    val httpSource = new HttpSource(settings)
    httpSource.runWith(handler)
    val result = Await.result(
      Http()
        .singleRequest(
          HttpRequest(
            method = HttpMethods.POST,
            uri = Uri(s"http://127.0.0.1:$port"),
            entity = HttpEntity(requestData)
          )
        )
        .map(r => {
          (r.status, Await.result(r.entity.toStrict(100.millisecond), 200.millisecond).data.utf8String)
        }),
      10.seconds
    )
    httpSource.shutdown()
    result
  }

  it should "responds error when get non json request" in {

    val testHandler = Flow[(EventStatus, Event)]

    val (status, body) = request(testHandler, "abc")

    status shouldEqual StatusCodes.BadRequest

  }

  it should "gives proper response when get proper request" in {

    val testHandler = Flow[(EventStatus, Event)]

    val (status, body) = request(testHandler, "{}")

    status shouldEqual StatusCodes.OK
    body shouldEqual "ok"

  }

  it should "responds error when processing failed" in {

    val testHandler =
      Flow[(EventStatus, Event)]
        .map {
          case (_, event) =>
            (FAIL(new RuntimeException("failed")), event)
        }

    // TODO check here
    // note that if not change the port here,
    // will get Success response which from last test, could be caused by cache under Akka http binding
    val (status, body) = request(testHandler, "{}", 55663)

    status shouldEqual StatusCodes.InternalServerError
    body shouldEqual "failed"

  }

  it should "responds error when processing failed with exception" in {

    val testHandler =
      Flow[(EventStatus, Event)]
        .map {
          case (_, event) =>
            if (event.uuid == "a")
              throw new RuntimeException("processing failed")
            else
              (NORM, event)
        }

    val (status, body) = request(testHandler, "{\"id\":\"a\"}", 55664)
    status shouldEqual StatusCodes.InternalServerError

    val (status2, body2) = request(testHandler, "{\"id\":\"b\"}", 55664)
    status2 shouldEqual StatusCodes.OK
    body2 shouldEqual "ok"

  }

}
