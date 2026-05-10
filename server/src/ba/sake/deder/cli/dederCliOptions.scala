package ba.sake.deder.cli

import mainargs.*
import ba.sake.deder.ServerNotification.LogLevel

given TokensReader.Simple[LogLevel] with {
  def shortName = "logLevel"
  def read(strs: Seq[String]) = Right(LogLevel.valueOf(strs.head.toUpperCase))
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
    @arg(doc = "Output result as JSON")
    json: Flag,
    @arg(doc = "Output result as DOT graph")
    dot: Flag,
    @arg(doc = "Output result as Mermaid graph")
    mermaid: Flag
)

@main("tasks command", "List tasks per module")
case class DederCliTasksOptions(
    @arg(doc = "Filter tasks by Module ID", short = 'm')
    module: Option[String],
    @arg(doc = "Output result as JSON")
    json: Flag,
    @arg(doc = "Output result as DOT graph")
    dot: Flag,
    @arg(doc = "Output result as Mermaid graph")
    mermaid: Flag
)

@main("plan command", "Plan for task execution in a module")
case class DederCliPlanOptions(
    @arg(doc = "Module IDs to plan", short = 'm')
    modules: Seq[String], // cant have a default... :/
    @arg(doc = "The task to plan", short = 't')
    task: String,
    @arg(doc = "Output result as JSON")
    json: Flag,
    @arg(doc = "Output result as DOT graph")
    dot: Flag,
    @arg(doc = "Output result as Mermaid graph")
    mermaid: Flag
)

@main("deps command", "Show dependency reports for module dependencies")
case class DederCliDepsOptions(
    @arg(doc = "Module IDs to inspect", short = 'm')
    modules: Seq[String],
    @arg(doc = "Report type: tree, list, why, stats, html", short = 'r')
    report: String = "tree",
    @arg(doc = "Alias for --report dot")
    dot: Flag,
    @arg(doc = "Alias for --report mermaid")
    mermaid: Flag,
    @arg(doc = "Alias for --report html")
    html: Flag,
    @arg(doc = "Output depGraph JSON (internal model)")
    json: Flag,
    @arg(doc = "Max depth (0 = module only, 1 = direct only, 2+ = include transitive)")
    maxDepth: Int = Int.MaxValue,
    @arg(doc = "Show only direct dependencies")
    directOnly: Flag,
    @arg(doc = "Exclude transitive dependencies (same as --direct-only)")
    noTransitive: Flag,
    @arg(doc = "Include dependency pattern (supports % wildcard). Repeatable.")
    include: Seq[String],
    @arg(doc = "Exclude dependency pattern (supports % wildcard). Repeatable.")
    exclude: Seq[String],
    @arg(doc = "Reverse lookup selector for why report: org:name or org:name:version")
    why: Option[String],
    @arg(doc = "Write output to file")
    outputFile: Option[String]
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
    @arg(doc = "Output result as JSON")
    json: Flag,
    @arg(doc = "Watch mode - re-execute task on source changes", short = 'w')
    watch: Flag,
    args: Leftover[String]
)

enum ImportBuildTool:
  case sbt

given TokensReader.Simple[ImportBuildTool] with {
  def shortName = "buildTool"
  def read(strs: Seq[String]) = {
    val strValue = strs.head.toLowerCase
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

@main("import command", "Import from another build tool")
case class DederCliImportOptions(
    @arg(doc = "Build tool to import from")
    from: ImportBuildTool
)

enum ShellType:
  case bash, zsh, fish, powershell

given TokensReader.Simple[ShellType] with {
  def shortName = "shellType"
  def read(strs: Seq[String]) = {
    val strValue = strs.head.toLowerCase
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

@main("complete command", "Generate shell completion script and provide completions")
case class DederCliCompleteOptions(
    @arg(doc = "Shell type: bash, zsh, or powershell", short = 's')
    shell: ShellType,
    @arg(doc = "Current command line", short = 'c')
    commandLine: Option[String],
    @arg(doc = "Current cursor position", short = 'p')
    cursorPos: Option[Int],
    @arg(doc = "Outputs completion script to stdout", short = 'o')
    output: Flag
)
