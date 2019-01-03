/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Beineng Ma <baineng.ma@gmail.com>
 */

package com.thenetcircle.event_bus.event.extractor

import java.text.SimpleDateFormat

import com.thenetcircle.event_bus.TestBase
import com.thenetcircle.event_bus.event.extractor.DataFormat.DataFormat
import com.thenetcircle.event_bus.event.extractor._
import com.thenetcircle.event_bus.event.{EventBody, EventTransportMode}

import scala.concurrent.Await
import scala.concurrent.duration._

class ActivityStreamsExtractorTest extends TestBase {

  private val activityStreamsExtractor: EventExtractor =
    EventExtractorFactory.getExtractor(DataFormat.ACTIVITYSTREAMS)

  private val unknownFormatExtractor: EventExtractor =
    new ActivityStreamsEventExtractor {
      override def getFormat(): DataFormat = DataFormat.UNKNOWN
    }

  behavior of "EventExtractor"

  it should "be failed, because it's not a json formatted data" in {
    var testdata = """
        |abc
      """.stripMargin

    assertThrows[EventExtractingException] {
      Await.result(activityStreamsExtractor.extract(testdata.getBytes), 3.seconds)
    }
  }

  it should "be failed, because the required field \"title\" is missed." in {
    val testdata = """
        |{
        |  "verb": "login"
        |}
      """.stripMargin

    val event = Await.result(activityStreamsExtractor.extract(testdata.getBytes), 3.seconds)
    event.getExtra("verb") shouldEqual Some("login")
  }

  it should "be succeeded if there is a title" in {
    var testdata = s"""
         |{
         |  "title": "user.login"
         |}
      """.stripMargin

    val testevent =
      Await.result(activityStreamsExtractor.extract(testdata.getBytes), 3.seconds)

    testevent.body shouldEqual EventBody(testdata, DataFormat.ACTIVITYSTREAMS)
    testevent.metadata.name shouldEqual Some("user.login")
  }

  it should "be succeeded as well if there are proper data" in {
    val time     = "2017-08-15T13:49:55Z"
    var testdata = s"""
        |{
        |  "version": "1.0",
        |  "id": "ED-providerId-message.send-actorId-59e704843e9cb",
        |  "title": "message.send",
        |  "verb": "send",
        |  "actor": {"id": "actorId", "objectType": "actorType"},
        |  "provider": {"id": "providerId", "objectType": "providerType"},
        |  "published": "$time"
        |}
      """.stripMargin

    val testevent =
      Await.result(activityStreamsExtractor.extract(testdata.getBytes), 3.seconds)

    testevent.body shouldEqual EventBody(testdata, DataFormat.ACTIVITYSTREAMS)
    testevent.createdAt shouldEqual new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").parse(time)
    testevent.metadata.name shouldEqual Some("message.send")
    testevent.getExtra("providerId") shouldEqual Some("providerId")
    testevent.getExtra("actorType") shouldEqual Some("actorType")
  }

  it should "be succeeded as well with complete ActivityStreams data" in {
    val time     = "2017-08-15T13:49:55Z"
    var testdata = s"""
         |{
         |  "version": "1.0",
         |  "content": "{\\"a\\": \\"testa\\", \\"b\\": \\"testb\\"}",
         |  "actor": {
         |    "id": "actorId",
         |    "objectType": "actorType",
         |    "content": "{\\"a\\": 1, \\"b\\": 2}",
         |    "attachments": [
         |      {
         |        "id": "attachmentId1",
         |        "objectType": "attachmentType1",
         |        "content": "abc",
         |        "attachments": [
         |          {
         |            "id": "subAttachmentId1",
         |            "objectType": "subAttachmentType1",
         |            "content": "subcontent"
         |          }
         |        ]
         |      },
         |      {
         |        "id": "attachmentId2",
         |        "objectType": "attachmentType2",
         |        "content": "def"
         |      }
         |    ]
         |  },
         |  "object": {
         |    "id": "objectId",
         |    "objectType": "objectType"
         |  },
         |  "target": {
         |    "id": "targetId",
         |    "objectType": "targetType"
         |  },
         |  "provider": {
         |    "id": "providerId",
         |    "objectType": "providerType"
         |  },
         |  "published": "$time",
         |  "title": "message.send",
         |  "verb": "send",
         |  "id": "ED-providerId-message.send-actorId-59e704843e9cb",
         |  "generator": {
         |    "id": "tnc-event-dispatcher",
         |    "objectType": "library",
         |    "content": "{\\"mode\\":\\"async\\",\\"class\\":\\"dfEvent_Profile_Visit\\"}"
         |  }
         |}
      """.stripMargin

    val testevent =
      Await.result(activityStreamsExtractor.extract(testdata.getBytes), 3.seconds)

    testevent.uuid shouldEqual "ED-providerId-message.send-actorId-59e704843e9cb"
    testevent.body shouldEqual EventBody(testdata, DataFormat.ACTIVITYSTREAMS)
    testevent.createdAt shouldEqual new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").parse(time)
    testevent.metadata.name shouldEqual Some("message.send")
    testevent.metadata.channel shouldEqual None
    testevent.getExtra("verb") shouldEqual Some("send")
    testevent.getExtra("providerId") shouldEqual Some("providerId")
    testevent.getExtra("providerType") shouldEqual Some("providerType")
    testevent.getExtra("actorId") shouldEqual Some("actorId")
    testevent.getExtra("actorType") shouldEqual Some("actorType")
    testevent.getExtra("objectId") shouldEqual Some("objectId")
    testevent.getExtra("objectType") shouldEqual Some("objectType")
    testevent.getExtra("targetId") shouldEqual Some("targetId")
    testevent.getExtra("targetType") shouldEqual Some("targetType")
    testevent.getExtra("generatorId") shouldEqual Some("tnc-event-dispatcher")
    testevent.getExtra("generatorType") shouldEqual Some("library")

  }

  it should "be succeeded with another EventFormat" in {
    var testdata = s"""
         |{
         |  "title": "user.login"
         |}
      """.stripMargin

    val testevent =
      Await.result(unknownFormatExtractor.extract(testdata.getBytes), 3.seconds)

    testevent.body shouldEqual EventBody(testdata, DataFormat.UNKNOWN)
  }

  it should "support channel field and millisecond" in {
    val time     = "2018-09-11T23:52:27.111+02:00"
    var testdata = s"""
         |{
         |  "actor": {
         |    "content": "{\\"hasMembership\\":\\"0\\",\\"membership\\":1}",
         |    "id": "7707608",
         |    "objectType": "user"
         |  },
         |  "generator": {
         |    "content": "{\\"mode\\":\\"async\\",\\"class\\":\\"dfEvent_Profile_Visit\\",\\"channel\\":\\"mychannel\\"}",
         |    "id": "tnc-event-dispatcher",
         |    "objectType": "library"
         |  },
         |  "id": "ED-poppen-profile.visit-7707608-5b98391b4890f",
         |  "object": {
         |    "content": "{\\"hasMembership\\":\\"0\\",\\"membership\\":1}",
         |    "id": "7690320",
         |    "objectType": "user"
         |  },
         |  "provider": {
         |    "attachments": [
         |      {
         |        "content": "{\\"session_user_id\\":7707608,\\"ip\\":\\"2a02:810a:86c0:5f73:e1b3:1d7b:577f:c471\\",\\"user-agent\\":\\"Mozilla\\\\/5.0 (Linux; Android 8.0.0; SAMSUNG SM-G950F Build\\\\/R16NW) AppleWebKit\\\\/537.36 (KHTML, like Gecko) SamsungBrowser\\\\/7.4 Chrome\\\\/59.0.3071.125 Mobile Safari\\\\/537.36\\",\\"referer\\":\\"https:\\\\/\\\\/www.poppen.de\\\\/p\\\\/harzp%c3%a4rchen_1234\\"}",
         |        "objectType": "context"
         |      }
         |    ],
         |    "id": "poppen",
         |    "objectType": "community"
         |  },
         |  "published": "$time",
         |  "title": "profile.visit",
         |  "verb": "visit",
         |  "version": "2.0"
         |}
      """.stripMargin

    val testevent =
      Await.result(activityStreamsExtractor.extract(testdata.getBytes), 3.seconds)

    testevent.uuid shouldEqual "ED-poppen-profile.visit-7707608-5b98391b4890f"
    testevent.body shouldEqual EventBody(testdata, DataFormat.ACTIVITYSTREAMS)
    testevent.createdAt shouldEqual new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX").parse(time)
    testevent.metadata.name shouldEqual Some("profile.visit")
    testevent.metadata.channel shouldEqual Some("mychannel")

  }

  it should "support transport mode field" in {
    val time = "2018-09-11T23:52:27.111+02:00"

    val testdata1 = s"""
         |{
         |  "generator": {
         |    "content": "{\\"mode\\":\\"async\\",\\"class\\":\\"dfEvent_Profile_Visit\\",\\"channel\\":\\"mychannel\\"}",
         |    "id": "tnc-event-dispatcher"
         |  },
         |  "id": "ED-poppen-profile.visit-7707608-5b98391b4890f",
         |  "published": "$time",
         |  "title": "mode.test"
         |}
      """.stripMargin
    val testevent1 =
      Await.result(activityStreamsExtractor.extract(testdata1.getBytes), 3.seconds)
    testevent1.uuid shouldEqual "ED-poppen-profile.visit-7707608-5b98391b4890f"
    testevent1.body shouldEqual EventBody(testdata1, DataFormat.ACTIVITYSTREAMS)
    testevent1.createdAt shouldEqual new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX").parse(time)
    testevent1.metadata.name shouldEqual Some("mode.test")
    testevent1.metadata.channel shouldEqual Some("mychannel")
    testevent1.metadata.transportMode shouldEqual Some(EventTransportMode.ASYNC)

    val testdata2 = s"""
         |{
         |  "generator": {
         |    "content": "{\\"mode\\":\\"sync\\",\\"class\\":\\"dfEvent_Profile_Visit\\",\\"channel\\":\\"mychannel\\"}",
         |    "id": "tnc-event-dispatcher"
         |  }
         |}
      """.stripMargin
    val testevent2 =
      Await.result(activityStreamsExtractor.extract(testdata2.getBytes), 3.seconds)
    testevent2.metadata.transportMode shouldEqual Some(EventTransportMode.OTHERS("sync"))

    val testdata3 = s"""
         |{
         |  "generator": {
         |    "content": "{\\"mode\\":\\"sync_plus\\",\\"class\\":\\"dfEvent_Profile_Visit\\",\\"channel\\":\\"mychannel\\"}",
         |    "id": "tnc-event-dispatcher"
         |  }
         |}
      """.stripMargin
    val testevent3 =
      Await.result(activityStreamsExtractor.extract(testdata3.getBytes), 3.seconds)
    testevent3.metadata.transportMode shouldEqual Some(EventTransportMode.SYNC_PLUS)

    val testdata4 = s"""
         |{
         |  "generator": {
         |    "content": "{\\"mode\\":\\"both\\",\\"class\\":\\"dfEvent_Profile_Visit\\",\\"channel\\":\\"mychannel\\"}",
         |    "id": "tnc-event-dispatcher"
         |  }
         |}
      """.stripMargin
    val testevent4 =
      Await.result(activityStreamsExtractor.extract(testdata4.getBytes), 3.seconds)
    testevent4.metadata.transportMode shouldEqual Some(EventTransportMode.BOTH)

    val testdata5 = s"""
         |{
         |  "generator": {
         |    "content": "{\\"class\\":\\"dfEvent_Profile_Visit\\",\\"channel\\":\\"mychannel\\"}",
         |    "id": "tnc-event-dispatcher"
         |  }
         |}
      """.stripMargin
    val testevent5 =
      Await.result(activityStreamsExtractor.extract(testdata5.getBytes), 3.seconds)
    testevent5.metadata.transportMode shouldEqual None
  }

}
