package ba.sake.deder.testing.forked

import ba.sake.tupson.{*, given}
import ba.sake.deder.testing.*

object ForkedTestMain {
  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      System.err.println("Usage: ForkedTestMain <args-file-path>")
      System.exit(2)
    }
    val argsFilePath = args(0)
    val forkedArgs = os.read(os.Path(argsFilePath)).parseJson[ForkedTestArgs]

    // Install capturing stdout BEFORE any logger captures System.out so logger writes flow through
    // the per-suite capture path.
    val (reporter, capture) = ForkedTestReporter.install()
    reporter.emit(ForkedTestEnvelope.ForkStarted(forkedArgs.forkId))

    // forked JVM already has the full classpath on -cp; use the system classloader directly.
    val classLoader = ClassLoader.getSystemClassLoader

    val logger = new ForkedTestLogger

    try {
      val testRunner = new DederTestRunner(
        testParallelism = forkedArgs.testParallelism,
        discoveredTests = forkedArgs.discoveredTests,
        frameworkOverrides = Map.empty,
        classLoader = classLoader,
        logger = logger,
        forkHooks = Some(ForkRunnerHooks(capture, reporter))
      )
      val results = testRunner.run(DederTestOptions(forkedArgs.testSelectors))
      val payload = ForkedTestResultsPayload(results, testRunner.perClassStats)
      os.write.over(os.Path(forkedArgs.resultsFile), payload.toJson, createFolders = true)
      reporter.emit(ForkedTestEnvelope.ForkCompleted(forkedArgs.forkId, results))
      System.exit(if results.success then 0 else 1)
    } catch {
      case e: Throwable =>
        // On any crash during initialization or execution (e.g. NoSuchMethodError
        // from a framework with a mismatched Scala version), write an error payload
        // so the orchestrator doesn't keep retrying forever.
        val errorResults = DederTestResults(
          total = forkedArgs.discoveredTests.flatMap(_.testClasses).size,
          passed = 0,
          failed = 0,
          errors = forkedArgs.discoveredTests.flatMap(_.testClasses).size,
          skipped = 0,
          duration = 0L,
          failedTestNames = forkedArgs.discoveredTests.flatMap(_.testClasses.map(tc => s"${tc.className}.${tc.className}")),
          suites = forkedArgs.discoveredTests.flatMap(_.testClasses).map { tc =>
            DederTestSuiteReport(
              name = tc.className,
              testCases = Seq(DederTestCaseReport(
                name = tc.className,
                classname = tc.className,
                status = DederTestStatus.Error,
                duration = 0L,
                failure = Some(DederTestFailure(
                  message = Some(s"Forked JVM crashed before tests could run: ${e.getMessage}"),
                  stackTrace = None
                ))
              )),
              duration = 0L
            )
          }
        )
        try {
          val payload = ForkedTestResultsPayload(errorResults, Map.empty)
          os.write.over(os.Path(forkedArgs.resultsFile), payload.toJson, createFolders = true)
        } catch {
          case _: Throwable => // best effort
        }
        System.exit(1)
    }
  }
}
