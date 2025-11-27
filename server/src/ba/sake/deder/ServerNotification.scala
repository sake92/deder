package ba.sake.deder

import java.time.Instant

enum ServerNotification {
  case Message(
      level: ServerNotification.Level,
      timestamp: Instant,
      message: String,
      moduleId: Option[String]
  )
  
  // tell client to run this subprocess
  case RunSubprocess(cmd: Seq[String])
  
  // tell client to exit
  case RequestFinished(success: Boolean = true)
}

object ServerNotification {
  def message(level: ServerNotification.Level, message: String, moduleId: Option[String] = None): ServerNotification =
    ServerNotification.Message(
      level,
      Instant.now(),
      message,
      moduleId
    )
  enum Level:
    case ERROR, WARNING, INFO, DEBUG, TRACE

}
