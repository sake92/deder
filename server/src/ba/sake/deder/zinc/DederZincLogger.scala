package ba.sake.deder.zinc

import java.util.function.Supplier
import ba.sake.deder.{ServerNotification, ServerNotificationsLogger}

class DederZincLogger(notifications: ServerNotificationsLogger, moduleId: String) extends xsbti.Logger {

  override def error(msg: Supplier[String]): Unit =
    notifications.add(ServerNotification.logError(msg.get(), Some(moduleId)))

  override def warn(msg: Supplier[String]): Unit =
    notifications.add(ServerNotification.logWarning(msg.get(), Some(moduleId)))

  override def info(msg: Supplier[String]): Unit =
    notifications.add(ServerNotification.logInfo(msg.get(), Some(moduleId)))

  override def debug(msg: Supplier[String]): Unit =
    notifications.add(ServerNotification.logDebug(msg.get(), Some(moduleId)))

  override def trace(exception: Supplier[Throwable]): Unit =
    notifications.add(ServerNotification.logTrace(exception.get().getMessage, Some(moduleId)))
}
