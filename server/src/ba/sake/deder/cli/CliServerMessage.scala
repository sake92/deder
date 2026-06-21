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
    val coloredText = msgLevel match {
      case LogLevel.ERROR   => fansi.Color.Red(text)
      case LogLevel.WARNING => fansi.Color.Yellow(text)
      case LogLevel.INFO    => text
      case LogLevel.DEBUG   => fansi.Color.LightGreen(text)
      case LogLevel.TRACE   => fansi.Color.LightGray(text)
    }
    val showLevel = RequestContext.current.get().logLevel.ordinal >= LogLevel.DEBUG.ordinal
    val prefix = (showLevel, moduleId) match {
      case (false, None) => ""
      case (true, None)  => s"[${msgLevelAnsi}] "
      case (false, Some(mod)) =>
        val mc = moduleColor(mod)
        s"[${mc(mod)}] "
      case (true, Some(mod)) =>
        val mc = moduleColor(mod)
        s"[${msgLevelAnsi}] [${mc(mod)}] "
    }
    CliServerMessage.Log(s"${prefix}${coloredText}", msgLevel)
  }

  private def moduleColor(moduleId: String): fansi.Attr = {
    val base = moduleId.stripSuffix("-test").stripSuffix("-main")
    val hue = spreadHash(base) % 360
    val offset = if moduleId.endsWith("-test") then 15 else if moduleId.endsWith("-main") then -15 else 0
    val h = (hue + offset + 360) % 360
    val (r, g, b) = hslToRgb(h, 0.7, 0.6) // h, s, l
    fansi.Color.True(r, g, b)
  }

  /** Positional-weighted ASCII hash for good hue spread on short strings. */
  private def spreadHash(s: String): Int =
    s.zipWithIndex.map((c, i) => c.toInt * (i + 1)).sum

  /** HSL → RGB. h in [0,360), s,l in [0,1]. Returns (r,g,b) each in [0,255]. */
  private def hslToRgb(h: Int, s: Double, l: Double): (Int, Int, Int) = {
    val c = (1 - math.abs(2 * l - 1)) * s
    val hh = h / 60.0
    val x = c * (1 - math.abs(hh % 2 - 1))
    val m = l - c / 2
    val (r1, g1, b1) = hh.toInt match {
      case 0 => (c, x, 0.0)
      case 1 => (x, c, 0.0)
      case 2 => (0.0, c, x)
      case 3 => (0.0, x, c)
      case 4 => (x, 0.0, c)
      case _ => (c, 0.0, x)
    }
    (((r1 + m) * 255).toInt, ((g1 + m) * 255).toInt, ((b1 + m) * 255).toInt)
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
