package ba.sake.deder.scalanative

import ba.sake.deder.{ServerNotification, ServerNotificationsLogger}
import scala.scalanative.build.Logger

class DederScalaNativeLogger(
    notifications: ServerNotificationsLogger,
    moduleId: String
) extends Logger {
  def trace(t: Throwable): Unit =
    notifications.add(ServerNotification.logTrace(t.getMessage, Some(moduleId), source = Some("scalanative")))

  def debug(msg: String): Unit =
    notifications.add(ServerNotification.logDebug(msg, Some(moduleId), source = Some("scalanative")))

  def info(msg: String): Unit =
    notifications.add(ServerNotification.logInfo(msg, Some(moduleId), source = Some("scalanative")))

  def warn(msg: String): Unit =
    notifications.add(ServerNotification.logWarning(msg, Some(moduleId), source = Some("scalanative")))

  def error(msg: String): Unit =
    notifications.add(ServerNotification.logError(msg, Some(moduleId), source = Some("scalanative")))
}
