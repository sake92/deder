package ba.sake.deder.testing

import sbt.testing.Logger
import ba.sake.deder.ServerNotificationsLogger
import ba.sake.deder.ServerNotification

class DederTestLogger(
    serverNotificationsLogger: ServerNotificationsLogger,
    moduleId: String
) extends Logger {

  def ansiCodesSupported(): Boolean = true

  def showStackTraces: Boolean = true

  def test(msg: String): Unit = info(msg)

  def error(msg: String): Unit =
    serverNotificationsLogger.add(ServerNotification.logError(msg, Some(moduleId)))

  def warn(msg: String): Unit =
    serverNotificationsLogger.add(ServerNotification.logWarning(msg, Some(moduleId)))

  def info(msg: String): Unit =
    serverNotificationsLogger.add(ServerNotification.logInfo(msg, Some(moduleId)))

  def debug(msg: String): Unit =
    serverNotificationsLogger.add(ServerNotification.logDebug(msg, Some(moduleId)))

  def trace(t: Throwable): Unit =
    error(t.getStackTrace().mkString("\n"))
}

