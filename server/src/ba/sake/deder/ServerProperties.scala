package ba.sake.deder

import java.util as ju

case class ServerProperties(
    logLevel: String,
    maxInactiveSeconds: Int,
    workerThreads: Int,
    maxActiveCompilers: Int,
    bspEnabled: Boolean,
    maxConcurrentTestForks: Int,
    forkTestFlushIntervalMs: Long,
    watchDebounceMillis: Int
)

object ServerProperties {
  def from(props: ju.Properties): ServerProperties = {
    val logLevel = props.getProperty("logLevel", "INFO").toUpperCase
    val maxInactiveSeconds = props.getProperty("maxInactiveSeconds", "1800").toInt
    val workerThreads = props.getProperty("workerThreads", "16").toInt
    val maxActiveCompilers = Option(props.getProperty("maxActiveCompilers"))
      .filter(_.nonEmpty)
      .flatMap(_.toIntOption)
      .filter(_ > 0)
      .getOrElse(workerThreads)
    val bspEnabled = props.getProperty("bspEnabled", "true").toBoolean
    val maxConcurrentTestForks = Option(props.getProperty("maxConcurrentTestForks"))
      .filter(_.nonEmpty)
      .map(_.toInt)
      .getOrElse(Runtime.getRuntime.availableProcessors())
    val forkTestFlushIntervalMs = props.getProperty("forkTestFlushIntervalMs", "1000").toLong
    val watchDebounceMillis = props.getProperty("watchDebounceMillis", "300").toInt

    ServerProperties(
      logLevel,
      maxInactiveSeconds,
      workerThreads,
      maxActiveCompilers,
      bspEnabled,
      maxConcurrentTestForks,
      forkTestFlushIntervalMs,
      watchDebounceMillis
    )
  }
}
