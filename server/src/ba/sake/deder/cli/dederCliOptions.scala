package ba.sake.deder.cli

import mainargs.*
import ba.sake.deder.ServerNotification.LogLevel

given TokensReader.Simple[LogLevel]{
    def shortName = "logLevel"
    def read(strs: Seq[String]) = Right(LogLevel.valueOf(strs.head.toUpperCase))
}

@main
case class DederCliModulesOptions(
    @arg(doc = "Output result as ASCII graph")
    ascii: Flag,
    @arg(doc = "Output result as DOT graph")
    dot: Flag
)

@main
case class DederCliTasksOptions(
    @arg(doc = "Output result as ASCII graph")
    ascii: Flag,
    @arg(doc = "Output result as DOT graph")
    dot: Flag
)

@main
case class DederCliPlanOptions(
    @arg(doc = "Module ID to plan", short = 'm')
    module: String,
    @arg(doc = "The task to plan", short = 't')
    task: String,
    @arg(doc = "Output result as ASCII graph")
    ascii: Flag,
    @arg(doc = "Output result as DOT graph")
    dot: Flag
)

@main
case class DederCliExecOptions(
    @arg(doc = "The task to execute", short = 't')
    task: String = "compile",
    @arg(doc = "Module IDs to execute", short = 'm')
    modules: Seq[String], // cant have a default.. :/
    @arg(doc = "Log level", short = 'l')
    logLevel: LogLevel = LogLevel.INFO,
    @arg(doc = "Output result as JSON")
    json: Flag,
    args: Leftover[String]
)
