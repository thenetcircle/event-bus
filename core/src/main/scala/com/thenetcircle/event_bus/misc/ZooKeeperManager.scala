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

class ZooKeeperManager private (client: CuratorFramework, rootPath: String)(implicit appContext: AppContext)
    extends StrictLogging {

  assert(
    rootPath.startsWith(s"/event-bus/${appContext.getAppName()}"),
    s"the zookeeper root path $rootPath is not allowed"
  )

  logger.debug(s"new ZookeeperManager instance created with rootPath $rootPath")

  def assertPermission(path: String): Unit =
    assert(path.startsWith(rootPath), s"the zookeeper path $path is not allowed")

  def close(): Unit = if (client.getState == CuratorFrameworkState.STARTED) client.close()

  def withNewRootPath(_rootpath: String): ZooKeeperManager = new ZooKeeperManager(client, _rootpath)
  def getRootPath(): String                                = rootPath

  def getAbsPath(relativePath: String): String = s"$rootPath/$relativePath"

  def ensurePath(relativePath: String, data: String = ""): Unit = {
    val absPath = getAbsPath(relativePath)
    if (client.checkExists().forPath(absPath) == null) {
      client.create().creatingParentsIfNeeded().forPath(absPath, data.getBytes())
    }
  }

  def getClient(): CuratorFramework = client

  def getData(relativePath: String): Option[String] = {
    val absPath = getAbsPath(relativePath)
    try {
      Some(new String(client.getData.forPath(absPath), "UTF-8"))
    } catch {
      case e: Throwable =>
        logger.info(s"getData from $absPath failed with error: ${e.getMessage}")
        None
    }
  }

  def delete(relativePath: String): Boolean = {
    val absPath = getAbsPath(relativePath)
    try {
      client.delete().deletingChildrenIfNeeded().forPath(absPath)
      true
    } catch {
      case e: Throwable =>
        logger.info(s"delete node $absPath failed with error: ${e.getMessage}")
        false
    }
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

  def createInstance(appendEnv: Boolean = true)(implicit appContext: AppContext): ZooKeeperManager = {
    val config = appContext.getSystemConfig()
    var rootPath: String =
      config.getString("app.zookeeper.rootpath") + s"/${appContext.getAppName()}"
    if (appendEnv) rootPath += s"/${appContext.getAppEnv()}"
    createInstance(config.getString("app.zookeeper.servers"), rootPath)(appContext)
  }

  def createInstance(rootPath: String)(implicit appContext: AppContext): ZooKeeperManager =
    createInstance(appContext.getSystemConfig().getString("app.zookeeper.servers"), rootPath)(appContext)

  def createInstance(connectString: String, rootPath: String)(implicit appContext: AppContext): ZooKeeperManager = {
    val client: CuratorFramework =
      CuratorFrameworkFactory.newClient(connectString, new ExponentialBackoffRetry(1000, 3))
    client.start()
    appContext.addShutdownHook(if (client.getState == CuratorFrameworkState.STARTED) client.close())
    val zkManager = new ZooKeeperManager(client, rootPath)
    appContext.setZKManager(zkManager)
    zkManager
  }

}
