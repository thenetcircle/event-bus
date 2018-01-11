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

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.zookeeper.CreateMode

import scala.collection.JavaConverters._

class ZKManager(connectString: String)(implicit environment: Environment) extends StrictLogging {

  val client: CuratorFramework =
    CuratorFrameworkFactory.newClient(connectString, new ExponentialBackoffRetry(1000, 3))
  val rootPath = s"/event-bus/${environment.getAppName()}"

  // register itself to be a runner
  // update stories status

  def init(): Unit = {
    client.start()
    environment.addShutdownHook(client.close())

    // Check and Create root nodes
    if (client.checkExists().forPath(getAbsPath("stories")) == null) {
      client.create().creatingParentsIfNeeded().forPath(getAbsPath("stories"))
    }
    if (client.checkExists().forPath(getAbsPath("executors")) == null) {
      client.create().creatingParentsIfNeeded().forPath(getAbsPath("executors"))
    }
  }

  def getAbsPath(path: String): String = s"$rootPath/$path"

  def registerStoryExecutor(executorGroup: String): String = {
    val payload = ""
    client
      .create()
      .creatingParentsIfNeeded()
      .withProtection()
      .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
      .forPath(getAbsPath(s"executors/$executorGroup/member"), payload.getBytes())
  }

  def getClient(): CuratorFramework = client

  def getData(relativePath: String): Option[String] = {
    try {
      Some(client.getData.forPath(relativePath).mkString)
    } catch {
      case e: Throwable =>
        logger.info(s"getData from $relativePath failed with error: ${e.getMessage}")
        None
    }
  }

  def getChildren(relativePath: String): Option[List[String]] = {
    try {
      Some(
        client.getChildren
          .forPath(getAbsPath(relativePath))
          .asScala
          .toList
      )
    } catch {
      case e: Throwable =>
        logger.info(s"getChildren from $relativePath failed with error: ${e.getMessage}")
        None
    }
  }

}

object ZKManager {

  def apply(config: Config)(implicit environment: Environment): ZKManager = {
    config.checkValid(ConfigFactory.defaultReference, "app.zookeeper-server")
    apply(config.getString("app.zookeeper-server"))
  }

  def apply(connectString: String)(implicit environment: Environment): ZKManager = {
    if (connectString.isEmpty) {
      throw new IllegalArgumentException("ConnectString is empty.")
    }
    new ZKManager(connectString)
  }

}
