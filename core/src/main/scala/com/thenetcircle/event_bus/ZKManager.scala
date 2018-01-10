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
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.zookeeper.CreateMode

class ZKManager(connectString: String)(implicit environment: Environment) {

  val client: CuratorFramework =
    CuratorFrameworkFactory.newClient(connectString, new ExponentialBackoffRetry(1000, 3))
  val rootPath = s"/event-bus/${environment.getAppName()}"

  init()

  // register itself to be a runner
  // update stories status

  private def init(): Unit = {
    client.start()
    environment.addShutdownHook(client.close())

    // Check and Create root nodes
    if (client.checkExists().forPath(rootPath) == null) {
      client.create().creatingParentsIfNeeded().forPath(rootPath)
    }
    if (client.checkExists().forPath(relatedPath("stories")) == null) {
      client.create().forPath(relatedPath("stories"))
    }
    if (client.checkExists().forPath(relatedPath("runners")) == null) {
      client.create().forPath(relatedPath("runners"))
    }
  }

  def relatedPath(path: String): String = s"$rootPath/$path"

  def registerRunner(payload: String): Unit = {
    client
      .create()
      .withProtection()
      .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
      .forPath(relatedPath("runners/runner"), payload.getBytes())
  }

}
