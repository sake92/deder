package ba.sake.deder

// per-request logger
class ServerNotificationsLogger(callback: ServerNotification => Unit) {
  def add(serverNotification: ServerNotification): Unit = {
    // TODO log per client id?
    // how to know which shell run which client.. parent_pid??
    serverNotification match
      case ServerNotification.Output(text) => 
      case ServerNotification.Log(level, timestamp, message, moduleId) =>
        if level.ordinal <= ServerNotification.Level.INFO.ordinal then
          println(s"[${level.toString}] $message")
      case ServerNotification.RunSubprocess(cmd) =>
      case ServerNotification.RequestFinished(success) =>
    
    callback(serverNotification)
  }
}
