package ba.sake.deder

import java.util as ju

final case class ServerProperties(
    logLevel: String,
    maxInactiveSeconds: Int,
    taskLockTimeoutSeconds: Int,
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
    val forkTestFlushIntervalMs = props.getProperty("forkTestFlushIntervalMs", "1000").toLong
    val watchDebounceMillis = props.getProperty("watchDebounceMillis", "300").toInt

    ServerProperties(
      logLevel,
      maxInactiveSeconds,
      taskLockTimeoutSeconds,
      forkTestFlushIntervalMs,
      watchDebounceMillis
    )
  }
}
