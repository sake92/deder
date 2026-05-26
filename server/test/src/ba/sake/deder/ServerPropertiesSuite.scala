package ba.sake.deder

class ServerPropertiesSuite extends munit.FunSuite {

  test("maxActiveCompilers defaults to workerThreads") {
    val props = mkProps("workerThreads" -> "7")
    val cfg = ServerProperties.from(props)
    assertEquals(cfg.maxActiveCompilers, 7)
  }

  test("maxActiveCompilers uses explicit override") {
    val props = mkProps(
      "workerThreads" -> "7",
      "maxActiveCompilers" -> "3"
    )
    val cfg = ServerProperties.from(props)
    assertEquals(cfg.maxActiveCompilers, 3)
  }

  test("maxActiveCompilers falls back to workerThreads when <= 0") {
    val zeroProps = mkProps(
      "workerThreads" -> "7",
      "maxActiveCompilers" -> "0"
    )
    val negativeProps = mkProps(
      "workerThreads" -> "7",
      "maxActiveCompilers" -> "-2"
    )
    assertEquals(ServerProperties.from(zeroProps).maxActiveCompilers, 7)
    assertEquals(ServerProperties.from(negativeProps).maxActiveCompilers, 7)
  }

  test("maxActiveCompilers falls back to workerThreads when invalid") {
    val props = mkProps(
      "workerThreads" -> "7",
      "maxActiveCompilers" -> "abc"
    )
    val cfg = ServerProperties.from(props)
    assertEquals(cfg.maxActiveCompilers, 7)
  }

  test("ServerMain compile semaphore uses maxActiveCompilers") {
    val cfg = ServerProperties(
      logLevel = "INFO",
      maxInactiveSeconds = 1800,
      workerThreads = 16,
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
