package ba.sake.deder

// per-request logger
class ServerNotificationsLogger(callback: ServerNotification => Unit) {
  def add(serverNotification: ServerNotification): Unit = {
    // TODO log per client id?
    // how to know which shell run which client.. parent_pid??
    //println(serverNotification)
    callback(serverNotification)
  }
}
