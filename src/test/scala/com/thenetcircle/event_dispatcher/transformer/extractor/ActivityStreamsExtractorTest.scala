package com.thenetcircle.event_dispatcher.transformer.extractor

import com.thenetcircle.event_dispatcher.{TestCase, UnExtractedEvent}

class ActivityStreamsExtractorTest extends TestCase {

  val json =
    """
      |{
      |  "id": "user-1008646-1500290771-820",
      |  "verb": "user.login",
      |  "provider": {
      |    "id": "COMM1",
      |    "objectType": "community"
      |  },
      |  "actor": {
      |    "id": "1008646",
      |    "objectType": "user"
      |  },
      |  "published": "2017-07-17T13:26:11+02:00",
      |  "context": {
      |    "ip": "79.198.111.108",
      |    "user-agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 9_3_5 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Version/9.0 Mobile/13G36 Safari/601.1",
      |    "hasMembership": "0",
      |    "membership": 1
      |  },
      |  "version": "1.0",
      |  "extra": {
      |    "name": "user.login",
      |    "group": "user_1008646",
      |    "mode": "sync_plus",
      |    "propagationStopped": false,
      |    "class": "dfEvent_User"
      |  }
      |}
    """.stripMargin

  test("json parser") {
    import spray.json._
    import ActivityStreamsProtocol._

    val jsonAst = json.parseJson
    val activity = jsonAst.convertTo[FatActivity]

    activity.actor.id.get shouldEqual "1008646"
    activity.id.get shouldEqual "user-1008646-1500290771-820"
    activity.verb.get shouldEqual "user.login"

    val context = activity.context.get
    context("ip") shouldEqual JsString("79.198.111.108")

    val extra = activity.extra.get
    extra("name") shouldEqual JsString("user.login")
    extra("propagationStopped") shouldEqual JsBoolean(false)
  }

}
