package ba.sake.deder.cli

import ba.sake.deder.ServerNotification
import ba.sake.tupson.JsonRW


enum CliServerMessage derives JsonRW {
  case Output(text: String)
  case Log(text: String, level: LogLevel)
  case RunSubprocess(cmd: Seq[String], watch: Boolean)
  case Exit(exitCode: Int)
}

object CliServerMessage {
  def fromServerNotification(sn: ServerNotification): Option[CliServerMessage] = sn match {
    case m: ServerNotification.Output =>
      Some(CliServerMessage.Output(m.text))
    case m: ServerNotification.Log =>
      val level = m.level match {
        case ServerNotification.LogLevel.ERROR   => LogLevel.ERROR
        case ServerNotification.LogLevel.WARNING => LogLevel.WARNING
        case ServerNotification.LogLevel.INFO    => LogLevel.INFO
        case ServerNotification.LogLevel.DEBUG   => LogLevel.DEBUG
        case ServerNotification.LogLevel.TRACE   => LogLevel.TRACE
      }
      Some(CliServerMessage.Log(s"[${m.level.toString.toLowerCase}] ${m.message}", level))
    case tp: ServerNotification.TaskProgress =>
      None
    case cs: ServerNotification.CompileStarted =>
      None
    case cd: ServerNotification.CompileDiagnostic =>
      None
    case cs: ServerNotification.CompileFinished =>
      None
    case cf: ServerNotification.CompileFailed =>
      None
    case rs: ServerNotification.RunSubprocess =>
      Some(CliServerMessage.RunSubprocess(rs.cmd, rs.watch))
    case ServerNotification.RequestFinished(success) =>
      Some(CliServerMessage.Exit(if success then 0 else 1))
  }
}
