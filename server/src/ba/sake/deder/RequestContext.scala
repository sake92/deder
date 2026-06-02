package ba.sake.deder

import ox.ForkLocal

case class RequestContext(
    clientId: String = "unknown",
    requestId: String = scala.util.Random.alphanumeric.take(8).mkString,
    envVars: Map[String, String] = Map.empty,
    outputFormat: OutputFormat = OutputFormat.PlainText,
    logLevel: cli.LogLevel = cli.LogLevel.INFO
)

object RequestContext {
  val current: ForkLocal[RequestContext] = ForkLocal(RequestContext())

  // BSP-only; stays ThreadLocal (not on CLI path)
  val traceparent: ThreadLocal[String] = new ThreadLocal()
}
