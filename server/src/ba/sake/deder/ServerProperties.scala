package ba.sake.deder

import java.util as ju

final case class ServerProperties(
    logLevel: String,
    maxInactiveSeconds: Int,
    taskLockTimeoutSeconds: Int,
    maxActiveCompilers: Int,
    bspEnabled: Boolean,
    maxConcurrentTestForks: Int,
    forkTestFlushIntervalMs: Long,
    watchDebounceMillis: Int
) {
  require(taskLockTimeoutSeconds > 0, "taskLockTimeoutSeconds must be > 0")
}

object ServerProperties {
  def from(props: ju.Properties): ServerProperties = {
    val logLevel = props.getProperty("logLevel", "INFO").toUpperCase
    val maxInactiveSeconds = props.getProperty("maxInactiveSeconds", "1800").toInt
    val taskLockTimeoutSeconds = props.getProperty("taskLockTimeoutSeconds", "600").toInt
    val maxActiveCompilers = Option(props.getProperty("maxActiveCompilers"))
      .filter(_.nonEmpty)
      .flatMap(_.toIntOption)
      .filter(_ > 0)
      .getOrElse(Runtime.getRuntime.availableProcessors())
    val bspEnabled = props.getProperty("bspEnabled", "true").toBoolean
    val maxConcurrentTestForks = Option(props.getProperty("maxConcurrentTestForks"))
      .filter(_.nonEmpty)
      .flatMap(_.toIntOption)
      .getOrElse(Runtime.getRuntime.availableProcessors())
    val forkTestFlushIntervalMs = props.getProperty("forkTestFlushIntervalMs", "1000").toLong
    val watchDebounceMillis = props.getProperty("watchDebounceMillis", "300").toInt

    ServerProperties(
      logLevel,
      maxInactiveSeconds,
      taskLockTimeoutSeconds,
      maxActiveCompilers,
      bspEnabled,
      maxConcurrentTestForks,
      forkTestFlushIntervalMs,
      watchDebounceMillis
    )
  }
}
