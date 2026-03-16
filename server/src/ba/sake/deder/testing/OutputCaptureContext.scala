package ba.sake.deder.testing

import ba.sake.deder.ServerNotificationsLogger

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
