package ba.sake.deder.testing.inmemory

import ba.sake.deder.ServerNotificationsLogger

/** Context for capturing server stdout/stderr to be sent as server notifications to client. Used by TeePrintStream.
  *
  * We only want to tee output when we're executing *some* code, e.g in-process test execution, and not when the server
  * is doing other work.
  */
object OutputCaptureContext {
  private[deder] val currentNotificationsLogger = new ThreadLocal[ServerNotificationsLogger]()
  private[deder] val currentModuleId = new ThreadLocal[String]()

  def withCapture[T](logger: ServerNotificationsLogger, moduleId: String)(body: => T): T = {
    currentNotificationsLogger.set(logger)
    currentModuleId.set(moduleId)
    try body
    finally {
      currentNotificationsLogger.remove()
      currentModuleId.remove()
    }
  }
}
