package ba.sake.deder

object OutputCaptureContext {
  val currentNotificationsLogger: ThreadLocal[AnyRef] = new ThreadLocal[AnyRef]()
  val currentModuleId: ThreadLocal[String] = new ThreadLocal[String]()

  def withCapture[T](notificationsLogger: AnyRef, moduleId: String)(body: => T): T = {
    currentNotificationsLogger.set(notificationsLogger)
    currentModuleId.set(moduleId)
    try body
    finally {
      currentNotificationsLogger.remove()
      currentModuleId.remove()
    }
  }
}
