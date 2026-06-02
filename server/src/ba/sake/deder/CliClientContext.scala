package ba.sake.deder

case class CliClientContext(
    clientId: String,
    requestId: String,
    envVars: Map[String, String] = Map.empty,
    outputFormat: OutputFormat = OutputFormat.PlainText,
    logLevel: cli.LogLevel = cli.LogLevel.INFO
)
