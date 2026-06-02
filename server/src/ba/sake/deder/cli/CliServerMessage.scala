package ba.sake.deder.cli

import ba.sake.deder.{ServerNotification, cli, DederGlobals}
import ba.sake.tupson.JsonRW
import ba.sake.deder.RequestContext

enum LogLevel derives JsonRW:
  case ERROR, WARNING, INFO, DEBUG, TRACE

enum CliServerMessage derives JsonRW {
  case Output(text: String)
  case Log(text: String, level: LogLevel)
  case RunSubprocess(cmd: Seq[String], envVars: Map[String, String], watch: Boolean)
  case Exit(exitCode: Int, serverShuttingDown: Boolean = false)
}

object CliServerMessage {
  def info(text: String): CliServerMessage.Log =
    log(text, LogLevel.INFO)

  def log(text: String, msgLevel: LogLevel, moduleId: Option[String] = None): CliServerMessage.Log = {
    val msgLevelString = msgLevel.toString.toLowerCase
    val msgLevelAnsi = msgLevel match {
      case LogLevel.ERROR   => fansi.Color.Red(msgLevelString)
      case LogLevel.WARNING => fansi.Color.Yellow(msgLevelString)
      case LogLevel.INFO    => fansi.Color.Green(msgLevelString)
      case LogLevel.DEBUG   => fansi.Color.LightGreen(msgLevelString)
      case LogLevel.TRACE   => fansi.Color.LightGray(msgLevelString)
    }
    val showLevel = RequestContext.clientContext.get().exists(_.logLevel.ordinal >= LogLevel.DEBUG.ordinal)
    val prefix = (showLevel, moduleId) match {
      case (false, None) => ""
      case (true, None)  => s"[${msgLevelAnsi}] "
      case (false, Some(mod)) =>
        "" + fansi.Color.Cyan(s"[${mod}] ")
      case (true, Some(mod)) =>
        val coloredMod = fansi.Color.Cyan(s"[${mod}]")
        s"[${msgLevelAnsi}] ${coloredMod} "
    }
    CliServerMessage.Log(s"${prefix}${text}", msgLevel)
  }

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
      Some(log(m.message, level, m.moduleId))
    case tp: ServerNotification.TaskProgress =>
      None
    case cs: ServerNotification.CompileStarted =>
      None
    case cd: ServerNotification.CompileDiagnostic =>
      val level = cd.problem.severity() match
        case xsbti.Severity.Error => LogLevel.ERROR
        case xsbti.Severity.Warn  => LogLevel.WARNING
        case _                    => LogLevel.INFO
      val location = formatDiagnosticLocation(cd.problem)
      val text = location match
        case Some(loc) => s"$loc: ${cd.problem.message()}"
        case None      => cd.problem.message()
      Some(log(text, level, Some(cd.moduleId)))
    case cs: ServerNotification.CompileFinished =>
      None
    case cf: ServerNotification.CompileFailed =>
      None
    case rs: ServerNotification.RunSubprocess =>
      Some(CliServerMessage.RunSubprocess(rs.cmd, rs.envVars, rs.watch))
    case ServerNotification.RequestFinished(success) =>
      Some(CliServerMessage.Exit(if success then 0 else 1))
  }

  private def formatDiagnosticLocation(problem: xsbti.Problem): Option[String] =
    val pos = problem.position()
    val srcOpt = pos.sourceFile()
    if srcOpt.isPresent then
      val f = srcOpt.get()
      val abs = os.Path(f.toPath.toAbsolutePath())
      val relPath =
        if abs.startsWith(DederGlobals.projectRootDir) then abs.relativeTo(DederGlobals.projectRootDir).toString()
        else abs.toString()
      val line = pos.startLine().orElse(0)
      val col = pos.startColumn().orElse(0)
      if line > 0 && col > 0 then Some(s"$relPath:$line:$col")
      else if line > 0 then Some(s"$relPath:$line")
      else Some(relPath)
    else None
}
