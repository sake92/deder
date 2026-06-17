package ba.sake.deder

import java.time.Instant

enum ServerNotification {
  case Output(text: String)

  case Log(
      level: ServerNotification.LogLevel,
      timestamp: Instant,
      message: String,
      moduleId: Option[String],
      source: Option[String] = None
  )

  case CompileStarted(moduleId: String, files: Seq[os.Path])

  case TaskProgress(
      moduleId: String,
      taskName: String,
      progress: Long,
      total: Long
  )

  case CompileDiagnostic(moduleId: String, problem: xsbti.Problem)

  case CompileFinished(moduleId: String, errors: Int, warnings: Int)
  
  case CompileFailed(moduleId: String, errors: Int, warnings: Int)

  // tell client to run this subprocess
  case RunSubprocess(cmd: Seq[String], envVars: Map[String, String], watch: Boolean)

  // tell client to exit
  case RequestFinished(success: Boolean)
}

object ServerNotification {
  def log(
      level: ServerNotification.LogLevel,
      message: String,
      moduleId: Option[String] = None,
      source: Option[String] = None
  ): ServerNotification =
    ServerNotification.Log(level, Instant.now(), message, moduleId, source)

  def logError(
      message: String,
      moduleId: Option[String] = None,
      source: Option[String] = None
  ): ServerNotification =
    log(ServerNotification.LogLevel.ERROR, message, moduleId, source)

  def logWarning(
      message: String,
      moduleId: Option[String] = None,
      source: Option[String] = None
  ): ServerNotification =
    log(ServerNotification.LogLevel.WARNING, message, moduleId, source)

  def logInfo(
      message: String,
      moduleId: Option[String] = None,
      source: Option[String] = None
  ): ServerNotification =
    log(ServerNotification.LogLevel.INFO, message, moduleId, source)

  def logInfo(message: String, moduleId: String): ServerNotification =
    log(ServerNotification.LogLevel.INFO, message, Some(moduleId))

  def logDebug(
      message: String,
      moduleId: Option[String] = None,
      source: Option[String] = None
  ): ServerNotification =
    log(ServerNotification.LogLevel.DEBUG, message, moduleId, source)

  def logTrace(
      message: String,
      moduleId: Option[String] = None,
      source: Option[String] = None
  ): ServerNotification =
    log(ServerNotification.LogLevel.TRACE, message, moduleId, source)

  enum LogLevel:
    case ERROR, WARNING, INFO, DEBUG, TRACE

}
