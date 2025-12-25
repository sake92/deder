package ba.sake.deder

import com.typesafe.scalalogging.StrictLogging

// TODO rename to ServerNotificationsHandler
class ServerNotificationsLogger(callback: ServerNotification => Unit) extends StrictLogging {
  def add(serverNotification: ServerNotification): Unit = {
    serverNotification match {
      case ServerNotification.Output(text) =>
      case ServerNotification.Log(level, timestamp, message, moduleId) =>
        val msgWithModule = moduleId match {
          case Some(id) => s"[$id] $message"
          case None     => message
        }
        level match {
          case ServerNotification.LogLevel.DEBUG   => logger.debug(msgWithModule)
          case ServerNotification.LogLevel.INFO    => logger.info(msgWithModule)
          case ServerNotification.LogLevel.WARNING => logger.warn(msgWithModule)
          case ServerNotification.LogLevel.ERROR   => logger.error(msgWithModule)
          case ServerNotification.LogLevel.TRACE   => logger.trace(msgWithModule)
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
