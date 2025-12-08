package ba.sake.deder.cli

import mainargs.*
import ba.sake.deder.ServerNotification.Level

given TokensReader.Simple[Level]{
    def shortName = "logLevel"
    def read(strs: Seq[String]) = Right(Level.valueOf(strs.head))
}

@main
case class DederCliOptions(
    @arg(doc = "The task to execute", short = 't')
    task: String = "compile",
    @arg(doc = "Module IDs to execute", short = 'm')
    modules: Seq[String], // cant have a default.. :/
    @arg(doc = "Log level", short = 'l')
    logLevel: Level = Level.INFO,
    args: Leftover[String]
)
