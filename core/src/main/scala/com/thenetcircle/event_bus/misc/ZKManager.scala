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
import org.apache.curator.RetryPolicy
import org.apache.curator.framework.imps.CuratorFrameworkState
import org.apache.curator.framework.recipes.cache.PathChildrenCache.StartMode
import org.apache.curator.framework.recipes.cache._
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.ExponentialBackoffRetry

import scala.collection.JavaConverters._

class ZKManager(
    connectString: String,
    private var rootPath: String,
    retryPolicy: RetryPolicy = new ExponentialBackoffRetry(1000, 3)
)(
    implicit appContext: AppContext
) extends Logging {

  private val client: CuratorFramework =
    CuratorFrameworkFactory.newClient(connectString, retryPolicy)

  logger.info(s"A ZKManager has benn created with rootPath $rootPath")

  client.start()

  logger.info(s"ZooKeeper client has benn started.")

  def assertPermission(path: String): Unit =
    assert(path.startsWith(rootPath), s"The ZooKeeper path $path is not allowed")

  def close(): Unit = if (client.getState == CuratorFrameworkState.STARTED) client.close()

  def setRootPath(_rootpath: String): Unit     = rootPath = _rootpath
  def getRootPath(): String                    = rootPath
  def getAbsPath(relativePath: String): String = s"$rootPath/$relativePath"

  def ensurePath(relativePath: String, data: String = ""): Unit = {
    val absPath = getAbsPath(relativePath)
    if (client.checkExists().forPath(absPath) == null) {
      client.create().creatingParentsIfNeeded().forPath(absPath, data.getBytes())
    }
  }

  def createOrUpdatePath(relativePath: String, data: String = ""): Unit = {
    val absPath = getAbsPath(relativePath)
    if (client.checkExists().forPath(absPath) == null) {
      client.create().creatingParentsIfNeeded().forPath(absPath, data.getBytes())
    } else {
      client.setData().forPath(absPath, data.getBytes())
    }
  }

  def deletePath(relativePath: String): Unit = {
    val absPath = getAbsPath(relativePath)
    try {
      client.delete().deletingChildrenIfNeeded().forPath(absPath)
    } catch {
      case ex: Throwable =>
        logger.error(s"Deleting ZooKeeper path: $absPath failed with error: $ex")
        throw ex
    }
  }

  def getClient(): CuratorFramework = client

  def getData(relativePath: String): Option[String] = {
    val absPath = getAbsPath(relativePath)
    try {
      Some(new String(client.getData.forPath(absPath), "UTF-8"))
    } catch {
      case ex: Throwable =>
        logger.error(s"Getting data from ZooKeeper path: $absPath failed with error: $ex")
        None
    }
  }

  def setData(relativePath: String, data: String): Unit = {
    val absPath = getAbsPath(relativePath)
    try {
      client.setData().forPath(absPath, data.getBytes())
    } catch {
      case ex: Throwable =>
        logger.error(s"Set data to ZooKeeper path: $absPath failed with error: $ex")
        throw ex
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
      case ex: Throwable =>
        logger.error(s"Getting children from ZooKeeper path: $relativePath failed with error: $ex")
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
          s"Getting a new event from ZooKeeper. type: ${event.getType}, path: ${if (event.getData != null) event.getData.getPath
          else ""}"
        )
        callback(event, watcher)
      }
    })

    appContext.addShutdownHook(watcher.close())

    watcher
  }

  def watchData(relativePath: String)(callback: (Option[String]) => Unit): NodeCache = {
    val zkPath = getAbsPath(relativePath)
    logger.debug(s"Going to watch ZooKeeper node $zkPath")
    val watcher = new NodeCache(client, zkPath)
    watcher.start()

    watcher.getListenable.addListener(new NodeCacheListener {
      override def nodeChanged(): Unit = {
        val data   = watcher.getCurrentData.getData
        val result = if (data == null) None else Some(Util.makeUTF8String(data))
        logger.debug(
          s"Watching zookeeper node $zkPath changed, new data: $result"
        )
        callback(result)
      }
    })

    appContext.addShutdownHook(watcher.close())

    watcher
  }
}
