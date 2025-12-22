package ba.sake.deder

// per-request logger
class ServerNotificationsLogger(callback: ServerNotification => Unit) {
  def add(serverNotification: ServerNotification): Unit = {
    serverNotification match {
      case ServerNotification.Output(text) =>
      case ServerNotification.Log(level, timestamp, message, moduleId) =>
        if level.ordinal <= ServerNotification.LogLevel.INFO.ordinal then println(s"[${level.toString}] $message")
      case _: ServerNotification.TaskProgress    =>
      case _: ServerNotification.CompileStarted =>
      case _: ServerNotification.CompileDiagnostic =>
      case _: ServerNotification.CompileFinished =>
      case _: ServerNotification.RunSubprocess   =>
      case _: ServerNotification.RequestFinished =>
    }
    callback(serverNotification)
  }
}
