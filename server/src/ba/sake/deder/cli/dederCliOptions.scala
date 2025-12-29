package ba.sake.deder.cli

import mainargs.*
import ba.sake.deder.ServerNotification.LogLevel

given TokensReader.Simple[LogLevel]{
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
    @arg(doc = "Output result as JSON")
    json: Flag,
    @arg(doc = "Output result as ASCII graph")
    ascii: Flag,
    @arg(doc = "Output result as DOT graph")
    dot: Flag
)

@main("tasks command", "List tasks per module")
case class DederCliTasksOptions(
    @arg(doc = "Filter tasks by Module ID", short = 'm')
    module: Option[String],
    @arg(doc = "Output result as JSON")
    json: Flag,
    @arg(doc = "Output result as ASCII graph")
    ascii: Flag,
    @arg(doc = "Output result as DOT graph")
    dot: Flag
    // TODO filtering? by module? but deps must be included..
    // by task name?
    // print first deps levels only?
)

@main("plan command", "Plan for task execution in a module")
case class DederCliPlanOptions(
    @arg(doc = "Module ID to plan", short = 'm')
    module: String, // Seq[String] unsupported, would be weird/hard
    @arg(doc = "The task to plan", short = 't')
    task: String,
    @arg(doc = "Output result as JSON")
    json: Flag,
    @arg(doc = "Output result as ASCII graph")
    ascii: Flag,
    @arg(doc = "Output result as DOT graph")
    dot: Flag
)

@main("clean command", "Clean build artifacts for module(s)")
case class DederCliCleanOptions(
    @arg(doc = "Module IDs to clean", short = 'm')
    modules: Seq[String], // cant have a default.. :/
)

@main("exec command", "Execute a task in module(s)")
case class DederCliExecOptions(
    @arg(doc = "The task to execute", short = 't')
    task: String = "compile",
    @arg(doc = "Module IDs to execute", short = 'm')
    modules: Seq[String], // cant have a default.. :/
    @arg(doc = "Log level", short = 'l')
    logLevel: LogLevel = LogLevel.INFO,
    @arg(doc = "Output result as JSON")
    json: Flag,
    @arg(doc = "Watch mode - re-execute task on source changes")
    watch: Flag,
    args: Leftover[String]
)
