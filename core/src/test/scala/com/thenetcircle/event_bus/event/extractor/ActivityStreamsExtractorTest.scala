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

import akka.util.ByteString
import com.thenetcircle.event_bus.base.AsyncUnitTest
import com.thenetcircle.event_bus.event._
import com.thenetcircle.event_bus.event.extractor.DataFormat.DataFormat
import org.scalatest.Succeeded
import spray.json.{DeserializationException, JsonParser}

class ActivityStreamsExtractorTest extends AsyncUnitTest {

  private val activityStreamsExtractor: EventExtractor =
    EventExtractorFactory.getExtractor(DataFormat.ACTIVITYSTREAMS)

  private val unknownFormatExtractor: EventExtractor =
    new ActivityStreamsEventExtractor {
      override def getFormat(): DataFormat = DataFormat.UNKNOWN
    }

  behavior of "EventExtractor"

  it should "be failed, because it's not a json formatted data" in {
    var data = ByteString("""
        |abc
      """.stripMargin)
    recoverToSucceededIf[JsonParser.ParsingException] {
      activityStreamsExtractor.extract(data)
    }.map(r => assert(r == Succeeded))
  }

  it should "be failed, because the required field \"title\" is missed." in {
    val data = ByteString("""
        |{
        |  "verb": "login"
        |}
      """.stripMargin)
    // Object is missing required member 'title'
    recoverToSucceededIf[DeserializationException] {
      activityStreamsExtractor.extract(data)
    }.map(r => assert(r == Succeeded))
  }

  it should "be succeeded if there is a title" in {
    var data = ByteString(s"""
         |{
         |  "title": "user.login"
         |}
      """.stripMargin)

    activityStreamsExtractor.extract(data) map { _data =>
      inside(_data) {
        case Event(metadata, body, _) =>
          body shouldEqual EventBody(data, DataFormat.ACTIVITYSTREAMS)
          metadata.name shouldEqual "user.login"
      }
    }
  }

  it should "be succeeded as well if there are proper data" in {
    val time = "2017-08-15T13:49:55Z"
    var data = ByteString(s"""
        |{
        |  "version": "1.0",
        |  "id": "ED-providerId-message.send-actorId-59e704843e9cb",
        |  "title": "message.send",
        |  "verb": "send",
        |  "actor": {"id": "actorId", "objectType": "actorType"},
        |  "provider": {"id": "providerId", "objectType": "providerType"},
        |  "published": "$time"
        |}
      """.stripMargin)

    activityStreamsExtractor.extract(data) map { _data =>
      inside(_data) {
        case Event(metadata, body, _) =>
          body shouldEqual EventBody(data, DataFormat.ACTIVITYSTREAMS)
          metadata shouldEqual EventMetaData(
            "ED-providerId-message.send-actorId-59e704843e9cb",
            "message.send",
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
              .parse(time)
              .getTime,
            Some("providerId"),
            Some("actorId")
          )
      }
    }
  }

  it should "be succeeded as well with complete ActivityStreams data" in {
    val time = "2017-08-15T13:49:55Z"
    var data = ByteString(s"""
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
         |    }
         |  }
         |}
      """.stripMargin)

    activityStreamsExtractor.extract(data) map { _data =>
      inside(_data) {
        case Event(metadata, body, _) =>
          body shouldEqual EventBody(data, DataFormat.ACTIVITYSTREAMS)
          metadata shouldEqual EventMetaData(
            "ED-providerId-message.send-actorId-59e704843e9cb",
            "message.send",
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
              .parse(time)
              .getTime,
            Some("providerId"),
            Some("actorId")
          )
      }
    }
  }

  it should "be succeeded with another EventFormat" in {
    var data = ByteString(s"""
         |{
         |  "title": "user.login"
         |}
      """.stripMargin)
    unknownFormatExtractor.extract(data) map { d =>
      inside(d) {
        case Event(_, body, _) =>
          body shouldEqual EventBody(data, DataFormat.UNKNOWN)
      }
    }
  }

}
