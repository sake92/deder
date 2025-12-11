package ba.sake.deder.deps

import scala.jdk.CollectionConverters.*
import coursierapi.Fetch
import coursierapi.FetchResult
import coursierapi.Dependency as CoursierDependency
import dependency.api.ops.*
import ba.sake.deder.ServerNotificationsLogger
import ba.sake.deder.ServerNotification

object DependencyResolver {

  def doFetch(
      coursierDependencies: Seq[CoursierDependency],
      notifications: Option[ServerNotificationsLogger] = None
  ): FetchResult = {
    val cache = coursierapi.Cache
      .create()
      .withLogger(notifications.map(new DederCoursierLogger(_)).orNull)
    Fetch
      .create()
      .withCache(cache)
      .withDependencies(coursierDependencies*)
      .fetchResult()
  }

  def doFetchOne(dependency: CoursierDependency): os.Path =
    os.Path(doFetch(Seq(dependency)).getFiles.asScala.head.toPath)

  /* deder deps */

  def fetch(dependencies: Seq[Dependency], notifications: Option[ServerNotificationsLogger] = None): FetchResult =
    val coursierDeps = dependencies.map(_.applied.toCs)
    doFetch(coursierDeps, notifications)

  def fetchFiles(dependencies: Seq[Dependency], notifications: Option[ServerNotificationsLogger] = None): Seq[os.Path] =
    fetch(dependencies, notifications).getFiles.asScala.map(f => os.Path(f.toPath)).toSeq

  def fetchFile(dependency: Dependency): os.Path =
    fetchFiles(Seq(dependency)).head
}

class DederCoursierLogger(notifications: ServerNotificationsLogger) extends coursierapi.SimpleLogger {
  override def starting(url: String): Unit =
    notifications.add(ServerNotification.logInfo(s"Download started: $url"))

  override def progress(url: String, downloaded: Long): Unit = {
    notifications.add(
      ServerNotification.logInfo(s"Downloading: $url $downloaded / 100%")
    )
  }

  override def done(url: String, success: Boolean): Unit = {
    val status = if success then "completed" else "failed"
    notifications.add(ServerNotification.logInfo(s"Download $status: $url"))
  }

}
