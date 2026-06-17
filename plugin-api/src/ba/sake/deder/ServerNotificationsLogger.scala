package ba.sake.deder

import com.typesafe.scalalogging.StrictLogging

class ServerNotificationsLogger(callback: ServerNotification => Unit) extends StrictLogging {
  def add(serverNotification: ServerNotification): Unit = {
    serverNotification match {
      case ServerNotification.Output(text) =>
      case ServerNotification.Log(level, timestamp, message, moduleId, source) =>
        val prefix = source match {
          case Some(s) => Some(s)
          case None    => moduleId
        }
        val msgWithPrefix = prefix match {
          case Some(p) => s"[$p] $message"
          case None     => message
        }
        level match {
          case ServerNotification.LogLevel.DEBUG   => logger.debug(msgWithPrefix)
          case ServerNotification.LogLevel.INFO    => logger.info(msgWithPrefix)
          case ServerNotification.LogLevel.WARNING => logger.warn(msgWithPrefix)
          case ServerNotification.LogLevel.ERROR   => logger.error(msgWithPrefix)
          case ServerNotification.LogLevel.TRACE   => logger.trace(msgWithPrefix)
        }
      case _: ServerNotification.TaskProgress      =>
      case _: ServerNotification.CompileStarted    =>
      case _: ServerNotification.CompileDiagnostic =>
      case _: ServerNotification.CompileFinished   =>
      case _: ServerNotification.CompileFailed   =>
      case _: ServerNotification.RunSubprocess     =>
      case _: ServerNotification.RequestFinished   =>
    }
    callback(serverNotification)
  }
}
