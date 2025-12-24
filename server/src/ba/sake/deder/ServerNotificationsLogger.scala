package ba.sake.deder

import com.typesafe.scalalogging.StrictLogging

// TODO rename to ServerNotificationsHandler
class ServerNotificationsLogger(callback: ServerNotification => Unit) extends StrictLogging {
  def add(serverNotification: ServerNotification): Unit = {
    serverNotification match {
      case ServerNotification.Output(text) =>
      case ServerNotification.Log(level, timestamp, message, moduleId) =>
        level match {
          case ServerNotification.LogLevel.DEBUG   => logger.debug(message)
          case ServerNotification.LogLevel.INFO    => logger.info(message)
          case ServerNotification.LogLevel.WARNING => logger.warn(message)
          case ServerNotification.LogLevel.ERROR   => logger.error(message)
          case ServerNotification.LogLevel.TRACE   => logger.trace(message)
        }
      case _: ServerNotification.TaskProgress      =>
      case _: ServerNotification.CompileStarted    =>
      case _: ServerNotification.CompileDiagnostic =>
      case _: ServerNotification.CompileFinished   =>
      case _: ServerNotification.RunSubprocess     =>
      case _: ServerNotification.RequestFinished   =>
    }
    callback(serverNotification)
  }
}
