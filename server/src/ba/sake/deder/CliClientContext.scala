package ba.sake.deder

// TODO rename to just ClientContext
case class CliClientContext(
    clientId: String = "unknown",
    requestId: String = scala.util.Random.alphanumeric.take(8).mkString,
    envVars: Map[String, String] = Map.empty,
    outputFormat: OutputFormat = OutputFormat.PlainText,
    logLevel: cli.LogLevel = cli.LogLevel.INFO
)
