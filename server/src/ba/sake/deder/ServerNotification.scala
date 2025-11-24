package ba.sake.deder

import java.time.Instant
import java.util.UUID

enum ServerNotification {
  case Message(
      id: UUID,
      level: ServerNotification.Level,
      timestamp: Instant,
      message: String,
      moduleId: Option[String]
  )
  // tell client to exit
  case RequestFinished(success: Boolean = true)
}

object ServerNotification {
  def message(level: ServerNotification.Level, message: String, moduleId: Option[String] = None): ServerNotification =
    ServerNotification.Message(
      UUID.randomUUID(),
      level,
      Instant.now(),
      message,
      moduleId
    )
  enum Level:
    case ERROR, WARNING, INFO, DEBUG

}
