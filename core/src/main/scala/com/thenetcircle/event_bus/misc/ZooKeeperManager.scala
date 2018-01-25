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

import com.thenetcircle.event_bus.context.AppContext
import com.typesafe.scalalogging.StrictLogging
import org.apache.curator.framework.imps.CuratorFrameworkState
import org.apache.curator.framework.recipes.cache.PathChildrenCache.StartMode
import org.apache.curator.framework.recipes.cache.{PathChildrenCache, PathChildrenCacheEvent, PathChildrenCacheListener}
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.ExponentialBackoffRetry

import scala.collection.JavaConverters._

class ZooKeeperManager private (connectString: String, rootPath: String)(implicit appContext: AppContext)
    extends StrictLogging {

  private var client: CuratorFramework =
    CuratorFrameworkFactory.newClient(connectString, new ExponentialBackoffRetry(1000, 3))

  def start(): Unit = if (client.getState != CuratorFrameworkState.STARTED) {
    client.start()
    appContext.addShutdownHook(if (client.getState == CuratorFrameworkState.STARTED) client.close())

    // Check and Create root nodes
    ensurePath("stories")
    ensurePath("runners")
  }

  def close(): Unit = if (client.getState == CuratorFrameworkState.STARTED) client.close()

  def getAbsPath(relativePath: String): String = s"$rootPath/$relativePath"

  def ensurePath(relativePath: String): Unit =
    if (client.checkExists().forPath(getAbsPath(relativePath)) == null) {
      client.create().creatingParentsIfNeeded().forPath(getAbsPath(relativePath))
    }

  def getClient(): CuratorFramework = client

  def getData(relativePath: String): Option[String] =
    try {
      Some(new String(client.getData.forPath(getAbsPath(relativePath)), "UTF-8"))
    } catch {
      case e: Throwable =>
        logger.info(s"getData from $relativePath failed with error: ${e.getMessage}")
        None
    }

  def getChildren(relativePath: String): Option[List[String]] =
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

  def getChildrenData(relativePath: String): Option[Map[String, String]] =
    getChildren(relativePath)
      .map(children => {
        children
          .map(childName => getData(s"$relativePath/$childName").map(data => childName -> data))
          .filter(_.isDefined)
          .map(_.get)
          .toMap
      })

  def watchChildren(
      relativePath: String,
      startMode: StartMode = StartMode.NORMAL,
      fetchData: Boolean = true
  )(callback: (PathChildrenCacheEvent, PathChildrenCache) => Unit): PathChildrenCache = {
    val watcher =
      new PathChildrenCache(client, getAbsPath(relativePath), fetchData)
    watcher.start(startMode)

    watcher.getListenable.addListener(new PathChildrenCacheListener {
      override def childEvent(client: CuratorFramework, event: PathChildrenCacheEvent): Unit = {
        logger.debug(
          s"get new event from zookeeper type: ${event.getType}, path: ${if (event.getData != null) event.getData.getPath
          else ""}"
        )
        callback(event, watcher)
      }
    })

    appContext.addShutdownHook(watcher.close())

    watcher
  }
}

object ZooKeeperManager {
  def apply(connectString: String, rootPath: String)(implicit appContext: AppContext): ZooKeeperManager = {
    if (connectString.isEmpty || rootPath.isEmpty) {
      throw new IllegalArgumentException("parameters are not enough for creating ZooKeeperManager.")
    }
    new ZooKeeperManager(connectString, rootPath)
  }
}
