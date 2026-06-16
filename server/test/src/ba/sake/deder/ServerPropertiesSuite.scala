package ba.sake.deder

class ServerPropertiesSuite extends munit.FunSuite {

  test("parses properties with defaults") {
    val props = mkProps()
    val cfg = ServerProperties.from(props)
    assertEquals(cfg.logLevel, "INFO")
    assertEquals(cfg.maxInactiveSeconds, 1800)
    assertEquals(cfg.taskLockTimeoutSeconds, 600)
    assertEquals(cfg.forkTestFlushIntervalMs, 1000L)
    assertEquals(cfg.watchDebounceMillis, 300)
    assertEquals(cfg.logRolloverPattern, "%d{yyyy-MM-dd-HH}")
    assertEquals(cfg.logMaxHistory, 7)
    assertEquals(cfg.logTotalSizeCap, "1GB")
  }

  test("parses property overrides") {
    val props = mkProps(
      "logLevel" -> "DEBUG",
      "maxInactiveSeconds" -> "300",
      "taskLockTimeoutSeconds" -> "1200",
      "forkTestFlushIntervalMs" -> "500",
      "watchDebounceMillis" -> "100",
      "logRolloverPattern" -> "%d{yyyy-MM-dd}",
      "logMaxHistory" -> "14",
      "logTotalSizeCap" -> "500MB"
    )
    val cfg = ServerProperties.from(props)
    assertEquals(cfg.logLevel, "DEBUG")
    assertEquals(cfg.maxInactiveSeconds, 300)
    assertEquals(cfg.taskLockTimeoutSeconds, 1200)
    assertEquals(cfg.forkTestFlushIntervalMs, 500L)
    assertEquals(cfg.watchDebounceMillis, 100)
    assertEquals(cfg.logRolloverPattern, "%d{yyyy-MM-dd}")
    assertEquals(cfg.logMaxHistory, 14)
    assertEquals(cfg.logTotalSizeCap, "500MB")
  }

  test("silently ignores removed keys (bspEnabled, maxActiveCompilers, maxConcurrentTestForks)") {
    val props = mkProps(
      "bspEnabled" -> "false",
      "maxActiveCompilers" -> "4",
      "maxConcurrentTestForks" -> "2"
    )
    val cfg = ServerProperties.from(props)
    // Defaults are used — removed keys have no effect
    assertEquals(cfg.logLevel, "INFO")
    assertEquals(cfg.maxInactiveSeconds, 1800)
    assertEquals(cfg.taskLockTimeoutSeconds, 600)
  }

  private def mkProps(entries: (String, String)*): java.util.Properties = {
    val p = new java.util.Properties()
    entries.foreach { case (k, v) => p.setProperty(k, v) }
    p
  }
}
