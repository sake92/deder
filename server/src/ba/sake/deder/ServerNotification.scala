package ba.sake.deder

import java.time.Instant

enum ServerNotification {
  case Output(text: String)
  
  case Log(
      level: ServerNotification.Level,
      timestamp: Instant,
      message: String,
      moduleId: Option[String]
  )
  
  // tell client to run this subprocess
  case RunSubprocess(cmd: Seq[String])
  
  // tell client to exit
  case RequestFinished(success: Boolean)
}

object ServerNotification {
  def log(level: ServerNotification.Level, message: String, moduleId: Option[String] = None): ServerNotification =
    ServerNotification.Log(
      level,
      Instant.now(),
      message,
      moduleId
    )

  def logError(message: String, moduleId: Option[String] = None): ServerNotification =
    log(ServerNotification.Level.ERROR, message, moduleId)

  enum Level:
    case ERROR, WARNING, INFO, DEBUG, TRACE

}
