package ba.sake.deder.scalajs

import ba.sake.deder.{ServerNotification, ServerNotificationsLogger}
import org.scalajs.logging.Logger
import org.scalajs.logging.Level

class DederScalaJsLogger(
    notifications: ServerNotificationsLogger,
    moduleId: String
) extends Logger {
  def log(level: Level, message: => String): Unit = level match {
    case Level.Error =>
      notifications.add(ServerNotification.logError(message, Some(moduleId)))
    case Level.Warn =>
      notifications.add(ServerNotification.logWarning(message, Some(moduleId)))
    case Level.Info =>
      notifications.add(ServerNotification.logInfo(message, Some(moduleId)))
    case Level.Debug =>
      notifications.add(ServerNotification.logDebug(message, Some(moduleId)))
  }

  def trace(t: => Throwable): Unit =
    notifications.add(ServerNotification.logTrace(t.getMessage, Some(moduleId)))
}
