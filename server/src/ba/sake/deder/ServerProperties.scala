package ba.sake.deder

import java.util as ju

final case class ServerProperties(
    logLevel: String,
    maxInactiveSeconds: Int,
    taskLockTimeoutSeconds: Int,
    forkTestFlushIntervalMs: Long,
    watchDebounceMillis: Int,
    logRolloverPattern: String,
    logMaxHistory: Int,
    logTotalSizeCap: String
) {
  require(taskLockTimeoutSeconds > 0, "taskLockTimeoutSeconds must be > 0")
  require(logMaxHistory >= 0, "logMaxHistory must be >= 0")
}

object ServerProperties {
  def from(props: ju.Properties): ServerProperties = {
    val logLevel = props.getProperty("logLevel", "INFO").toUpperCase
    val maxInactiveSeconds = props.getProperty("maxInactiveSeconds", "1800").toInt
    val taskLockTimeoutSeconds = props.getProperty("taskLockTimeoutSeconds", "600").toInt
    val forkTestFlushIntervalMs = props.getProperty("forkTestFlushIntervalMs", "1000").toLong
    val watchDebounceMillis = props.getProperty("watchDebounceMillis", "300").toInt
    val logRolloverPattern = props.getProperty("logRolloverPattern", "%d{yyyy-MM-dd-HH}")
    val logMaxHistory = props.getProperty("logMaxHistory", "7").toInt
    val logTotalSizeCap = props.getProperty("logTotalSizeCap", "1GB")
    ServerProperties(
      logLevel,
      maxInactiveSeconds,
      taskLockTimeoutSeconds,
      forkTestFlushIntervalMs,
      watchDebounceMillis,
      logRolloverPattern,
      logMaxHistory,
      logTotalSizeCap
    )
  }
}
