package ba.sake.deder.cli

import mainargs.*
import ba.sake.deder.OutputFormat

given TokensReader.Simple[LogLevel] with {
  def shortName = "logLevel"
  def read(strs: Seq[String]) =
    val v = strs.headOption.getOrElse(throw IllegalArgumentException(s"No value provided for $shortName"))
    Right(LogLevel.valueOf(v.toUpperCase))
}

given TokensReader.Simple[OutputFormat] with {
  def shortName = "outputFormat"
  def read(strs: Seq[String]) =
    val v = strs.headOption.getOrElse(throw IllegalArgumentException(s"No value provided for $shortName"))
    v.toLowerCase match {
    case "plain"     => Right(OutputFormat.PlainText)
    case "json"      => Right(OutputFormat.Json)
    case "densejson" => Right(OutputFormat.DenseJson)
    case "dot"       => Right(OutputFormat.Dot)
    case "mermaid"   => Right(OutputFormat.Mermaid)
    case other       => Left(s"Invalid format '$other'. Must be plain, json, densejson, dot, or mermaid.")
  }
}

@main
case class DederCliHelpOptions(
    @arg(doc = "Command to get help for", short = 'c')
    command: String
)

@main("modules command", "List modules and their dependencies")
case class DederCliModulesOptions(
    @arg(doc = "Filter by Module ID(s) (focal nodes for depth filtering)", short = 'm')
    modules: Seq[String],
    @arg(doc = "Max hops following dependency edges (downstream). Default: unlimited")
    depthDown: Int = Int.MaxValue,
    @arg(doc = "Max hops following reverse-dependency edges (upstream). Default: unlimited")
    depthUp: Int = Int.MaxValue,
    @arg(doc = "Output format: plain, json, densejson, dot, or mermaid", short = 'f')
    format: OutputFormat = OutputFormat.PlainText
)

@main("plugins command", "List configured plugins and their tasks (tasks only shown with --log-level debug)")
case class DederCliPluginsOptions(
    @arg(doc = "Output format: plain, json, densejson", short = 'f') 
    format: OutputFormat = OutputFormat.PlainText
)

@main("tasks command", "List tasks per module")
case class DederCliTasksOptions(
    @arg(doc = "Filter tasks by Module ID", short = 'm')
    module: Option[String],
    @arg(doc = "Output format: plain, json, densejson, dot, or mermaid", short = 'f')
    format: OutputFormat = OutputFormat.PlainText
)

@main("plan command", "Plan for task execution in a module")
case class DederCliPlanOptions(
    @arg(doc = "Module IDs to plan", short = 'm')
    modules: Seq[String], // cant have a default... :/
    @arg(doc = "The task to plan", short = 't')
    task: String,
    @arg(doc = "Output format: plain, json, densejson, dot, or mermaid", short = 'f')
    format: OutputFormat = OutputFormat.PlainText
)

@main("clean command", "Clean build artifacts for module(s)")
case class DederCliCleanOptions(
    @arg(doc = "Module IDs to clean", short = 'm')
    modules: Seq[String], // cant have a default.. :/
    @arg(doc = "The task to clean (if not specified, cleans entire module)", short = 't')
    task: Option[String]
)

@main("exec command", "Execute a task in module(s)")
case class DederCliExecOptions(
    @arg(doc = "The task to execute", short = 't')
    task: String = "compile",
    @arg(doc = "Module IDs to execute", short = 'm')
    modules: Seq[String], // cant have a default... :/
    @arg(doc = "Log level", short = 'l')
    logLevel: LogLevel = LogLevel.INFO,
    @arg(doc = "Output format: plain, json, densejson, dot, or mermaid", short = 'f')
    format: OutputFormat = OutputFormat.PlainText,
    @arg(doc = "Watch mode - re-execute task on source changes", short = 'w')
    watch: Flag,
    args: Leftover[String]
)

enum ImportBuildTool:
  case sbt

given TokensReader.Simple[ImportBuildTool] with {
  def shortName = "buildTool"
  def read(strs: Seq[String]) = {
    val strValue = strs.headOption.getOrElse(throw IllegalArgumentException(s"No value provided for $shortName")).toLowerCase
    try Right(ImportBuildTool.valueOf(strValue))
    catch {
      case e: IllegalArgumentException =>
        throw new IllegalArgumentException(
          s"Build tool '${strValue}' not supported, must be one of: ${ImportBuildTool.values.mkString(", ")}",
          e
        )
    }
  }
}

@main("import command", "Import from another build tool. Autodetects the build tool if --from is omitted.")
case class DederCliImportOptions(
    @arg(doc = "Build tool to import from (e.g. sbt). If omitted, the build tool is autodetected.")
    from: Option[ImportBuildTool] = None
)

enum ShellType:
  case bash, zsh, fish, powershell

given TokensReader.Simple[ShellType] with {
  def shortName = "shellType"
  def read(strs: Seq[String]) = {
    val strValue = strs.headOption.getOrElse(throw IllegalArgumentException(s"No value provided for $shortName")).toLowerCase
    try Right(ShellType.valueOf(strValue))
    catch {
      case e: IllegalArgumentException =>
        throw new IllegalArgumentException(
          s"Shell type '${strValue}' not supported, must be one of: ${ShellType.values.mkString(", ")}",
          e
        )
    }
  }
}

@main("tool command", "Launch a server-level tool")
case class DederCliToolOptions(
    @arg(doc = "Tool name to run (omit to list available tools)")
    toolName: String = "",
    args: Leftover[String] = Leftover()
)

@main("complete command", "Generate shell completion script and provide completions")
case class DederCliCompleteOptions(
    @arg(doc = "Shell type: bash, zsh, fish, or powershell", short = 's')
    shell: ShellType,
    @arg(doc = "Current command line", short = 'c')
    commandLine: Option[String],
    @arg(doc = "Current cursor position", short = 'p')
    cursorPos: Option[Int],
    @arg(doc = "Outputs completion script to stdout", short = 'o')
    output: Flag
)
