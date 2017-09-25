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

package com.thenetcircle.event_bus

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._
import scala.util.Random

class MainSimulation extends Simulation {

  val httpConf = http
    .baseURL("http://thin:8086") // Here is the root for all relative URLs
    .acceptHeader(
      "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8") // Here are the common headers
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .userAgentHeader(
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0")

  val feeder = Iterator.continually(
    Map("uuid"    -> Random.alphanumeric.take(20).mkString,
        "actorId" -> Random.nextInt(65020100)))

  val scn = scenario("EventBus Stress Test")
    .feed(feeder)
    .repeat(5, "MainRequest") {
      exec(
        http("main")
          .post("/")
          .body(StringBody(s"""
        |{
        |  "id":"$${uuid}",
        |  "verb":"user.login",
        |  "provider":{"id":"POPP","objectType":"community"},
        |  "actor":{"id":"$${actorId}","objectType":"user"},
        |  "published":"2017-07-17T13:26:11+02:00",
        |  "context":{
        |    "ip":"79.199.117.108",
        |    "user-agent":"Mozilla/5.0 (iPhone; CPU iPhone OS 9_3_5 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Version/9.0 Mobile/13G36 Safari/601.1",
        |    "referer":"https://www.poppen.de/inbox","hasMembership":"0","membership":1
        |  },
        |  "version":"1.0",
        |  "extra":{
        |    "name":"user.login",
        |    "group":"user_1008646",
        |    "mode":"sync_plus",
        |    "propagationStopped":false,
        |    "class":"dfEvent_User"
        |  }
        |}
      """.stripMargin)))
    }

  setUp(
    scn.inject(
      rampUsers(60000) over (1 minute)
    )).protocols(httpConf)

}
