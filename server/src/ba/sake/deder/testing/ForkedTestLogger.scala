package ba.sake.deder.testing

import ba.sake.deder.ServerNotificationsLogger

class ForkedTestLogger(moduleId: String)
    extends DederTestLogger(
      serverNotificationsLogger = new ServerNotificationsLogger(_ => ()), // no-op logger for forked tests
      moduleId = moduleId
    ) {

  // get original stdout/stderr before Junit5 engine hijacks them
  // otherwise, we might get StackOverflowError when writing to stdout/stderr due to JUnit5's output capture mechanism
  private val out = System.out
  private val err = System.err

  override def error(msg: String): Unit = err.println(msg)

  override def warn(msg: String): Unit = out.println(msg)

  override def info(msg: String): Unit = out.println(msg)

  override def debug(msg: String): Unit = out.println(msg)

  override def trace(t: Throwable): Unit = error(s"${t.toString}\n${t.getStackTrace.mkString("\n")}")
}
