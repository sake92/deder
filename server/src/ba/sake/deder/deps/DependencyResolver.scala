package ba.sake.deder.deps

import scala.jdk.CollectionConverters.*
import coursierapi.Fetch
import coursierapi.Dependency
import ba.sake.deder.ServerNotificationsLogger
import ba.sake.deder.ServerNotification

object DependencyResolver {

  def fetch(dependencies: Seq[Dependency], notifications: Option[ServerNotificationsLogger] = None): Seq[os.Path] = {
    val cache = coursierapi.Cache
      .create()
      .withLogger(notifications.map(new DederCoursierLogger(_)).orNull)
    val files = Fetch
      .create()
      .withCache(cache)
      .withDependencies(dependencies*)
      .fetch()
      .asScala
      .toSeq
    files.map(f => os.Path(f))
  }

  def fetchOne(dependency: Dependency): os.Path =
    fetch(Seq(dependency)).head
}

class DederCoursierLogger(notifications: ServerNotificationsLogger) extends coursierapi.SimpleLogger {
  override def starting(url: String): Unit =
    notifications.add(ServerNotification.message(ServerNotification.Level.INFO, s"Download started: $url"))

  override def progress(url: String, downloaded: Long): Unit = {
    notifications.add(
      ServerNotification.message(ServerNotification.Level.INFO, s"Downloading: $url $downloaded / 100%")
    )
  }

  override def done(url: String, success: Boolean): Unit = {
    val status = if success then "completed" else "failed"
    notifications.add(ServerNotification.message(ServerNotification.Level.INFO, s"Download $status: $url"))
  }

}
