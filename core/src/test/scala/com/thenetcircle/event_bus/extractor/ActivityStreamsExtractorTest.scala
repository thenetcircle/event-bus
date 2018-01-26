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

package com.thenetcircle.event_bus.extractor

import java.text.SimpleDateFormat

import com.thenetcircle.event_bus.TestBase
import com.thenetcircle.event_bus.event.extractor.DataFormat.DataFormat
import com.thenetcircle.event_bus.event.extractor._
import com.thenetcircle.event_bus.interfaces.EventBody

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
      Await.result(activityStreamsExtractor.extract(testdata.getBytes), 500.millisecond)
    }
  }

  it should "be failed, because the required field \"title\" is missed." in {
    val testdata = """
        |{
        |  "verb": "login"
        |}
      """.stripMargin

    val event = Await.result(activityStreamsExtractor.extract(testdata.getBytes), 500.millisecond)
    event.getExtra("verb") shouldEqual Some("login")
  }

  it should "be succeeded if there is a title" in {
    var testdata = s"""
         |{
         |  "title": "user.login"
         |}
      """.stripMargin

    val testevent =
      Await.result(activityStreamsExtractor.extract(testdata.getBytes), 500.millisecond)

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
      Await.result(activityStreamsExtractor.extract(testdata.getBytes), 500.millisecond)

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
         |  "content": {
         |    "a": "testa",
         |    "b": "testb"
         |  },
         |  "actor": {
         |    "id": "actorId",
         |    "objectType": "actorType",
         |    "content": {
         |      "a": 1,
         |      "b": 2
         |    },
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
         |    "content": {
         |      "mode": "async",
         |      "class": "TNC\\\\EventDispatcher\\\\Interfaces\\\\Event\\\\TransportableEvent"
         |    },
         |    "url": "http://www.callback.com"
         |  }
         |}
      """.stripMargin

    val testevent =
      Await.result(activityStreamsExtractor.extract(testdata.getBytes), 500.millisecond)

    testevent.uuid shouldEqual "ED-providerId-message.send-actorId-59e704843e9cb"
    testevent.body shouldEqual EventBody(testdata, DataFormat.ACTIVITYSTREAMS)
    testevent.createdAt shouldEqual new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").parse(time)
    testevent.metadata.name shouldEqual Some("message.send")
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
    testevent.getExtra("generatorUrl") shouldEqual Some("http://www.callback.com")

  }

  it should "be succeeded with another EventFormat" in {
    var testdata = s"""
         |{
         |  "title": "user.login"
         |}
      """.stripMargin

    val testevent =
      Await.result(unknownFormatExtractor.extract(testdata.getBytes), 500.millisecond)

    testevent.body shouldEqual EventBody(testdata, DataFormat.UNKNOWN)
  }

}
