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

package com.thenetcircle.event_bus.misc

import com.thenetcircle.event_bus.base.AkkaStreamTest

class ZKManagerTest extends AkkaStreamTest {

  behavior of "ZKManager"

  it should "do proper initialization" in {

    val zkmanager = new ZKManager("maggie-zoo-1:2181,maggie-zoo-2:2181")

    zkmanager.init()

    zkmanager.registerStoryExecutor("test")

    println(zkmanager.fetchStories())

    Thread.sleep(10000)

  }

}
