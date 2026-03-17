package ba.sake.deder.testing

import ba.sake.deder.*
import ba.sake.tupson.{*, given}

private val TestClassLoaderSharedPrefixes = Seq("sbt.testing.")

object ForkedTestOrchestrator {

  def run(
      discoveredTests: Seq[DiscoveredFrameworkTests],
      runtimeClasspath: Seq[os.Path],
      jvmOptions: Seq[String],
      javaHome: Option[String],
      testOptions: DederTestOptions,
      notifications: ServerNotificationsLogger,
      moduleId: String,
      outDir: os.Path,
      workerThreads: Int
  ): DederTestResults = {

    // write args file
    os.makeDir.all(outDir)
    val argsFilePath = outDir / "fork-args.json"
    val resultsFilePath = outDir / s"fork-results-${java.util.UUID.randomUUID()}.json"
    val args = ForkedTestArgs(
      discoveredTests = discoveredTests,
      testSelectors = testOptions.testSelectors,
      workerThreads = workerThreads,
      resultsFile = resultsFilePath.toString
    )
    os.write.over(argsFilePath, args.toJson)

    val javaBinary = resolveJavaBinary(javaHome)
    val serverClasspath = Seq(DederGlobals.projectRootDir / ".deder/server.jar")
    val fullClasspath =
      (serverClasspath ++ runtimeClasspath).map(_.toString).mkString(java.io.File.pathSeparator)
    val cmd = Seq(javaBinary) ++ jvmOptions ++ Seq(
      "-cp",
      fullClasspath,
      "ba.sake.deder.testing.ForkedTestMain",
      argsFilePath.toString
    )
    val proc = os
      .proc(cmd)
      .spawn(
        cwd = DederGlobals.projectRootDir,
        stdout = os.Pipe,
        stderr = os.Pipe
      )

    // stream stdout/stderr in daemon threads
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

    // wait for process (max 30 minutes) and collect results
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
      if os.exists(resultsFilePath) then os.read(resultsFilePath).parseJson[DederTestResults]
      else
        notifications.add(
          ServerNotification.logError(
            s"Forked test process crashed (exit $exitCode)",
            Some(moduleId)
          )
        )
        DederTestResults(total = 0, passed = 0, failed = 0, errors = 1, skipped = 0, duration = 0)

    // Step 8: Cleanup
    // os.remove(argsFilePath)
    // if os.exists(resultsFilePath) then os.remove(resultsFilePath)

    results
  }

  private def resolveJavaBinary(javaHome: Option[String]): String =
    javaHome
      .orElse(Option(System.getenv("JAVA_HOME")).filter(_.nonEmpty))
      .map(home => s"$home/bin/java")
      .getOrElse("java")
}
