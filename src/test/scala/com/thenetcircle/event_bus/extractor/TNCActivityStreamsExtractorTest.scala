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

import akka.util.ByteString
import com.thenetcircle.event_bus.EventFormat.{ DefaultFormat, TestFormat }
import com.thenetcircle.event_bus._
import com.thenetcircle.event_bus.base.AsyncTestCase
import org.scalatest.Succeeded
import spray.json.{ DeserializationException, JsonParser }

class TNCActivityStreamsExtractorTest extends AsyncTestCase {

  /*val json =
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
    """.stripMargin*/

  private val defaultFormatExtractor: Extractor[DefaultFormat] =
    new TNCActivityStreamsExtractor with Extractor[DefaultFormat] {
      override val dataFormat: DefaultFormat = DefaultFormat
      override def getEventBody(data: ByteString): EventBody[DefaultFormat] =
        EventBody(data, DefaultFormat)
    }

  private val testFormatExtractor: Extractor[TestFormat] =
    new TNCActivityStreamsExtractor with Extractor[TestFormat] {
      override val dataFormat: TestFormat = TestFormat
      override def getEventBody(data: ByteString): EventBody[TestFormat] =
        EventBody(data, TestFormat)
    }

  test("test invalid data") {
    var data = ByteString(
      """
        |abc
      """.stripMargin
    )
    recoverToSucceededIf[JsonParser.ParsingException] {
      defaultFormatExtractor.extract(data)
    }.map(r => assert(r == Succeeded))

    data = ByteString(
      """
        |{
        |  "verb": "user.login"
        |}
      """.stripMargin
    )
    // Object is missing required member 'actor'
    recoverToSucceededIf[DeserializationException] {
      defaultFormatExtractor.extract(data)
    }.map(r => assert(r == Succeeded))
  }

  test("test valid data") {
    val time = "2017-08-15T13:49:55Z"
    var data = ByteString(
      s"""
        |{
        |  "id": "123",
        |  "verb": "user.login",
        |  "actor": {"id": "123", "objectType": "user"},
        |  "published": "$time"
        |}
      """.stripMargin
    )
    defaultFormatExtractor.extract(data) map { d =>
      inside(d) {
        case ExtractedData(body, metadata, _, _) =>
          body shouldEqual EventBody(data, DefaultFormat)
          metadata shouldEqual EventMetaData("123",
                                             "user.login",
                                             new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").parse(time).getTime,
                                             "",
                                             "123" -> "user")
      }
    }
  }

  test("test another data format") {
    var data = ByteString(
      s"""
         |{
         |  "verb": "user.login",
         |  "actor": {"id": "123", "objectType": "user"}
         |}
      """.stripMargin
    )
    testFormatExtractor.extract(data) map { d =>
      inside(d) {
        case ExtractedData(body, metadata, _, _) =>
          body shouldEqual EventBody(data, TestFormat)
      }
    }
  }

}
