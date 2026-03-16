package ba.sake.deder.testing

import ba.sake.deder.ServerNotificationsLogger

class ForkedTestLogger(moduleId: String) extends DederTestLogger(
  serverNotificationsLogger = new ServerNotificationsLogger(_ => ()), // no-op logger for forked tests
  moduleId = moduleId
) {
  // Overrides all parent methods to write to stdout/stderr instead of ServerNotificationsLogger
  // This is necessary because forked tests run in a separate process with independent output streams

  override def error(msg: String): Unit =
    System.err.println(s"[$moduleId] $msg")

  override def warn(msg: String): Unit =
    System.out.println(s"[$moduleId] $msg")

  override def info(msg: String): Unit =
    System.out.println(s"[$moduleId] $msg")

  override def debug(msg: String): Unit =
    System.out.println(s"[$moduleId] $msg")

  override def trace(t: Throwable): Unit =
    error(s"${t.toString}\n${t.getStackTrace.mkString("\n")}")
}
