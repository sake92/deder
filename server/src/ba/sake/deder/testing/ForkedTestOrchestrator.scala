package ba.sake.deder.testing

import ba.sake.deder.*
import ba.sake.tupson.{*, given}

private val TestClassLoaderSharedPrefixes = Seq("sbt.testing.")

object ForkedTestOrchestrator {

  def run(
      classesDir: os.Path,
      runtimeClasspath: Seq[os.Path],
      frameworkClassNames: Seq[String],
      jvmOptions: Seq[String],
      javaHome: Option[String],
      testOptions: DederTestOptions,
      notifications: ServerNotificationsLogger,
      moduleId: String,
      outDir: os.Path,
      workerThreads: Int
  ): DederTestResults = {

    // Step 1: In-process test discovery
    val discoveredTests: Seq[SerializableTest] =
      ClassLoaderUtils.withIsolatedClassLoader(runtimeClasspath, TestClassLoaderSharedPrefixes) { classLoader =>
        val logger = DederTestLogger(notifications, moduleId)
        val testDiscovery = DederTestDiscovery(
          classLoader = classLoader,
          testClassesDir = classesDir,
          testClasspath = runtimeClasspath,
          frameworkClassNames = frameworkClassNames,
          logger = logger
        )
        testDiscovery.discover().flatMap { case (framework, tests) =>
          tests.map { case (className, fingerprint) =>
            SerializableTest(className, SerializableFingerprint.from(fingerprint))
          }
        }
      }

    // Step 2: Write args file
    os.makeDir.all(outDir)
    val argsFilePath = outDir / "fork-args.json"
    val resultsFilePath = outDir / s"fork-results-${java.util.UUID.randomUUID()}.json"
    val args = ForkedTestArgs(
      runtimeClasspath = runtimeClasspath.map(_.toString),
      frameworks = frameworkClassNames,
      tests = discoveredTests,
      testSelectors = testOptions.testSelectors,
      workerThreads = workerThreads,
      resultsFile = resultsFilePath.toString
    )
    os.write.over(argsFilePath, args.toJson)

    // Step 3: Resolve java binary
    val javaBinary = resolveJavaBinary(javaHome)

    // Step 4: Resolve harness classpath (Deder server classes for ForkedTestMain)
    val harness = resolveHarnessClasspath()

    // Step 5: Spawn subprocess
    val fullClasspath =
      (runtimeClasspath ++ harness).map(_.toString).mkString(java.io.File.pathSeparator)
    val cmd = Seq(javaBinary) ++ jvmOptions ++ Seq(
      "-cp",
      fullClasspath,
      "ba.sake.deder.testing.ForkedTestMain",
      argsFilePath.toString
    )
    val proc = os.proc(cmd).spawn(
      cwd = DederGlobals.projectRootDir,
      stdout = os.Pipe,
      stderr = os.Pipe
    )

    // Step 6: Stream stdout/stderr in daemon threads
    val stderrLines = new java.util.concurrent.CopyOnWriteArrayList[String]()

    val stdoutThread = new Thread(() => {
      val reader = new java.io.BufferedReader(new java.io.InputStreamReader(proc.stdout.wrapped))
      try {
        var line = reader.readLine()
        while (line != null) {
          notifications.add(ServerNotification.logInfo(line, Some(moduleId)))
          line = reader.readLine()
        }
      } catch {
        case _: java.io.IOException => () // pipe closed normally
        case e: Exception =>
          notifications.add(ServerNotification.logError(s"[fork stdout error] ${e.getMessage}", Some(moduleId)))
      }
    })
    stdoutThread.setDaemon(true)
    stdoutThread.start()

    val stderrThread = new Thread(() => {
      val reader = new java.io.BufferedReader(new java.io.InputStreamReader(proc.stderr.wrapped))
      try {
        var line = reader.readLine()
        while (line != null) {
          stderrLines.add(line)
          notifications.add(ServerNotification.logError(line, Some(moduleId)))
          line = reader.readLine()
        }
      } catch {
        case _: java.io.IOException => () // pipe closed normally
        case e: Exception =>
          notifications.add(ServerNotification.logError(s"[fork stderr error] ${e.getMessage}", Some(moduleId)))
      }
    })
    stderrThread.setDaemon(true)
    stderrThread.start()

    // Step 7: Wait for process (max 30 minutes) and collect results
    val MaxTestTimeMs = 30L * 60 * 1000
    val finished = proc.waitFor(MaxTestTimeMs)
    if !finished then
      proc.wrapped.destroyForcibly()
      notifications.add(
        ServerNotification.logError(
          s"Forked test process timed out after 30 minutes, killed.",
          Some(moduleId)
        )
      )
    val exitCode = proc.exitCode()
    stdoutThread.join(2000)
    stderrThread.join(2000)

    val results =
      if os.exists(resultsFilePath) then
        os.read(resultsFilePath).parseJson[DederTestResults]
      else
        notifications.add(
          ServerNotification.logError(
            s"Forked test process crashed (exit $exitCode)",
            Some(moduleId)
          )
        )
        DederTestResults(total = 0, passed = 0, failed = 0, errors = 1, skipped = 0, duration = 0)

    // Step 8: Cleanup
    os.remove(argsFilePath)
    if os.exists(resultsFilePath) then os.remove(resultsFilePath)

    results
  }

  private def resolveJavaBinary(javaHome: Option[String]): String =
    javaHome
      .orElse(Option(System.getenv("JAVA_HOME")).filter(_.nonEmpty))
      .map(home => s"$home/bin/java")
      .getOrElse("java")

  private def resolveHarnessClasspath(): Seq[os.Path] = {
    val serverJar = DederGlobals.projectRootDir / ".deder" / "server.jar"
    if os.exists(serverJar) then Seq(serverJar)
    else {
      getClass.getClassLoader match {
        case urlCl: java.net.URLClassLoader =>
          urlCl.getURLs.toSeq.map(url => os.Path(java.nio.file.Paths.get(url.toURI)))
        case _ =>
          System.getProperty("java.class.path", "")
            .split(java.io.File.pathSeparator)
            .filter(_.nonEmpty)
            .map(os.Path(_))
            .toSeq
      }
    }
  }
}
