package ba.sake.deder.deps

import scala.jdk.CollectionConverters.*
import coursierapi.Fetch
import coursierapi.FetchResult
import coursierapi.Dependency
import ba.sake.deder.ServerNotificationsLogger
import ba.sake.deder.ServerNotification

object DependencyResolver {

  def fetch(dependencies: Seq[Dependency], notifications: Option[ServerNotificationsLogger] = None): FetchResult = {
    val cache = coursierapi.Cache
      .create()
      .withLogger(notifications.map(new DederCoursierLogger(_)).orNull)
    Fetch
      .create()
      .withCache(cache)
      .withDependencies(dependencies*)
      .fetchResult()
  }

  def fetchOne(dependency: Dependency): os.Path =
    os.Path(fetch(Seq(dependency)).getFiles().asScala.head.toPath())
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
