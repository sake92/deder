package ba.sake.deder

import java.time.Instant
import java.util.UUID

case class ServerNotification(
    id: UUID,
    level: ServerNotification.Level,
    timestamp: Instant,
    message: String,
    moduleId: Option[String]
)

object ServerNotification {
  def make(level: ServerNotification.Level, message: String,  moduleId: Option[String] = None): ServerNotification = ServerNotification(
    UUID.randomUUID(),
    level,
    Instant.now(),
    message,
    moduleId
  )
  enum Level:
    case ERROR, WARNING, INFO, DEBUG
}

/*
TODO
data_kind	String	The type of structured payload (e.g., Diagnostic, TaskProgress).
payload	Object	The specific, structured data (e.g., line number, file path, progress percentage).
 */
