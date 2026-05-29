package ba.sake.deder

class ServerPropertiesSuite extends munit.FunSuite {

  test("maxActiveCompilers uses explicit override") {
    val props = mkProps(
      "maxActiveCompilers" -> "3"
    )
    val cfg = ServerProperties.from(props)
    assertEquals(cfg.maxActiveCompilers, 3)
  }

  test("maxActiveCompilers falls back to available processors when invalid") {
    val props = mkProps(
      "maxActiveCompilers" -> "abc"
    )
    val cfg = ServerProperties.from(props)
    assertEquals(cfg.maxActiveCompilers, Runtime.getRuntime.availableProcessors())
  }

  test("ServerMain compile semaphore uses maxActiveCompilers") {
    val cfg = ServerProperties(
      logLevel = "INFO",
      maxInactiveSeconds = 1800,
      taskLockTimeoutSeconds = 600,
      maxActiveCompilers = 2,
      bspEnabled = true,
      maxConcurrentTestForks = Runtime.getRuntime.availableProcessors(),
      forkTestFlushIntervalMs = 1000L,
      watchDebounceMillis = 300
    )
    val sem = ServerMain.newCompileSemaphore(cfg)
    assertEquals(sem.availablePermits(), 2)
  }

  private def mkProps(entries: (String, String)*): java.util.Properties = {
    val p = new java.util.Properties()
    entries.foreach { case (k, v) => p.setProperty(k, v) }
    p
  }
}
