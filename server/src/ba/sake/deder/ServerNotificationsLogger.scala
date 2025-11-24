package ba.sake.deder

// per-request logger
class ServerNotificationsLogger(callback: ServerNotification => Unit) {
  def add(serverNotification: ServerNotification): Unit = {
    // TODO log to server_log_file once when request finishes?
    callback(serverNotification)
  }
}
