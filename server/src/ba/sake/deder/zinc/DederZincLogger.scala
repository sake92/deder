package ba.sake.deder.zinc

import java.util.function.Supplier
import ba.sake.deder.ServerNotification.Level
import ba.sake.deder.{ServerNotification, ServerNotificationsLogger}

class DederZincLogger(notifications: ServerNotificationsLogger) extends xsbti.Logger {

  override def error(msg: Supplier[String]): Unit =
    notifications.add(ServerNotification.message(Level.ERROR, msg.get()))

  override def warn(msg: Supplier[String]): Unit =
    notifications.add(ServerNotification.message(Level.WARNING, msg.get()))

  override def info(msg: Supplier[String]): Unit =
    notifications.add(ServerNotification.message(Level.INFO, msg.get()))

  // too noisy.. so trace
  override def debug(msg: Supplier[String]): Unit =
    notifications.add(ServerNotification.message(Level.TRACE, msg.get()))

  override def trace(exception: Supplier[Throwable]): Unit =
    notifications.add(ServerNotification.message(Level.TRACE, exception.get().getMessage))
}
