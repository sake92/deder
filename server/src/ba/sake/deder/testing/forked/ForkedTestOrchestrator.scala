package ba.sake.deder.testing.forked

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.{Callable, Executors}
import scala.util.control.NonFatal
import ba.sake.deder.*
import ba.sake.deder.testing.*
import ba.sake.tupson.{*, given}

object ForkedTestOrchestrator {

  private val MaxTestTimeMs = 30L * 60 * 1000
  private val RunIdFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(java.time.ZoneId.systemDefault())

  def run(
      discoveredTests: Seq[DiscoveredFrameworkTests],
      runtimeClasspath: Seq[os.Path],
      jvmOptions: Seq[String],
      envVars: Map[String, String],
      javaHome: Option[String],
      testOptions: DederTestOptions,
      notifications: ServerNotificationsLogger,
      moduleId: String,
      outDir: os.Path,
      testParallelism: Int,
      maxTestForks: Int
  ): DederTestResults = {

    if maxTestForks > 1 && jvmOptions.exists(_.contains("-agentlib:jdwp=")) then {
      notifications.add(
        ServerNotification.logError(
          s"maxTestForks > 1 cannot be combined with a fixed JDWP port in jvmOptions. " +
            s"Set maxTestForks=1 or remove the JDWP option.",
          Some(moduleId)
        )
      )
      return DederTestResults(total = 0, passed = 0, failed = 0, errors = 1, skipped = 0, duration = 0)
    }

    val testsToRun =
      if testOptions.testSelectors.isEmpty then discoveredTests
      else
        discoveredTests.flatMap { dft =>
          val testClassNames = dft.testClasses.map(_.className)
          val matchedNames = testOptions.testSelectors.flatMap { ts =>
            val classSelector = ts.split("#") match {
              case Array(classNameSelector, _) => classNameSelector
              case _                           => ts
            }
            WildcardUtils.getMatches(testClassNames, classSelector)
          }.toSet
          val filtered = dft.testClasses.filter(tc => matchedNames.contains(tc.className))
          Option.when(filtered.nonEmpty)(dft.copy(testClasses = filtered))
        }

    val history = TestHistory.load(outDir)
    val buckets = TestDistribution.distribute(testsToRun, history, maxTestForks)
    if buckets.isEmpty then {
      notifications.add(ServerNotification.logWarning("No tests found on the classpath.", Some(moduleId)))
      return DederTestResults.empty
    }

    val effectiveForks = buckets.size
    val runId = generateRunId()
    val runDir = outDir / s"run-$runId"
    os.makeDir.all(runDir)
    if effectiveForks > 1 then
      notifications.add(
        ServerNotification.logDebug(
          s"Spawning $effectiveForks test forks for $moduleId (run $runId)",
          Some(moduleId)
        )
      )

    val javaBinary = resolveJavaBinary(javaHome)
    val fullClasspath = buildClasspath(runtimeClasspath)
    val requestId = RequestContext.id.get()

    val showForkTag = effectiveForks > 1
    val forkExecutor = Executors.newFixedThreadPool(effectiveForks)
    try {
      val callables: Seq[Callable[Option[ForkedTestResultsPayload]]] =
        buckets.zipWithIndex.map { case (slice, forkId) =>
          new Callable[Option[ForkedTestResultsPayload]] {
            def call(): Option[ForkedTestResultsPayload] =
              runForkWithPermit(
                forkId = forkId,
                slice = slice,
                requestId = requestId,
                javaBinary = javaBinary,
                fullClasspath = fullClasspath,
                jvmOptions = jvmOptions,
                envVars = envVars,
                testOptions = testOptions,
                testParallelism = testParallelism,
                runDir = runDir,
                showForkTag = showForkTag,
                notifications = notifications,
                moduleId = moduleId
              )
          }
        }
      val futures = callables.map(forkExecutor.submit)
      val payloads = futures.flatMap { f =>
        try f.get()
        catch { case NonFatal(_) => None }
      }

      val aggregated = aggregate(payloads.map(_.results))
      val perClassStats = payloads.flatMap(_.perClassStats).toMap
      TestHistory.save(outDir, history.merge(perClassStats))
      aggregated
    } finally {
      forkExecutor.shutdownNow()
    }
  }

  private def runForkWithPermit(
      forkId: Int,
      slice: Seq[DiscoveredFrameworkTests],
      requestId: String,
      javaBinary: String,
      fullClasspath: String,
      jvmOptions: Seq[String],
      envVars: Map[String, String],
      testOptions: DederTestOptions,
      testParallelism: Int,
      runDir: os.Path,
      showForkTag: Boolean,
      notifications: ServerNotificationsLogger,
      moduleId: String
  ): Option[ForkedTestResultsPayload] = {
    val cancelled = () =>
      requestId != null && {
        val tok = DederGlobals.cancellationTokens.get(requestId)
        tok != null && tok.get()
      }
    if cancelled() then return None
    val sem = DederGlobals.testForkSemaphore
    sem.acquire()
    try {
      if cancelled() then return None
      spawnAndRun(
        forkId = forkId,
        slice = slice,
        javaBinary = javaBinary,
        fullClasspath = fullClasspath,
        jvmOptions = jvmOptions,
        envVars = envVars,
        testOptions = testOptions,
        testParallelism = testParallelism,
        forkDir = runDir / s"fork-$forkId",
        showForkTag = showForkTag,
        notifications = notifications,
        moduleId = moduleId
      )
    } finally {
      sem.release()
    }
  }

  private def generateRunId(): String = {
    val ts = RunIdFormatter.format(Instant.now())
    val suffix = java.util.UUID.randomUUID().toString.take(4)
    s"$ts-$suffix"
  }

  private def spawnAndRun(
      forkId: Int,
      slice: Seq[DiscoveredFrameworkTests],
      javaBinary: String,
      fullClasspath: String,
      jvmOptions: Seq[String],
      envVars: Map[String, String],
      testOptions: DederTestOptions,
      testParallelism: Int,
      forkDir: os.Path,
      showForkTag: Boolean,
      notifications: ServerNotificationsLogger,
      moduleId: String
  ): Option[ForkedTestResultsPayload] = {
    val tag = if showForkTag then s"[fork-$forkId] " else ""
    os.makeDir.all(forkDir)
    val argsFilePath = forkDir / "fork-args.json"
    val resultsFilePath = forkDir / s"fork-results-${java.util.UUID.randomUUID()}.json"
    val stdoutLog = forkDir / "stdout.log"
    val stderrLog = forkDir / "stderr.log"
    if os.exists(stdoutLog) then os.remove(stdoutLog)
    if os.exists(stderrLog) then os.remove(stderrLog)

    val args = ForkedTestArgs(
      forkId = forkId,
      discoveredTests = slice,
      testSelectors = testOptions.testSelectors,
      testParallelism = testParallelism,
      resultsFile = resultsFilePath.toString
    )
    os.write.over(argsFilePath, args.toJson)

    val cmd = Seq(javaBinary) ++ jvmOptions ++ Seq(
      "-cp",
      fullClasspath,
      "ba.sake.deder.testing.forked.ForkedTestMain",
      argsFilePath.toString
    )
    val proc = os
      .proc(cmd)
      .spawn(
        cwd = DederGlobals.projectRootDir,
        env = envVars,
        stdout = os.Pipe,
        stderr = os.Pipe
      )

    val stdoutThread = new Thread(
      () => streamStdout(forkId, proc, stdoutLog, tag, notifications, moduleId),
      s"fork-$forkId-stdout"
    )
    stdoutThread.setDaemon(true)
    stdoutThread.start()

    val stderrThread = new Thread(
      () => streamStderr(proc, stderrLog, tag, notifications, moduleId),
      s"fork-$forkId-stderr"
    )
    stderrThread.setDaemon(true)
    stderrThread.start()

    val finished = proc.waitFor(MaxTestTimeMs)
    if !finished then {
      proc.wrapped.destroyForcibly()
      notifications.add(
        ServerNotification.logError(
          s"${tag}forked test process timed out after ${MaxTestTimeMs / 60000} minutes, killed.",
          Some(moduleId)
        )
      )
    }
    val exitCode = proc.exitCode()
    //stdoutThread.join(2000)
    //stderrThread.join(2000)

    if os.exists(resultsFilePath) then
      try Some(os.read(resultsFilePath).parseJson[ForkedTestResultsPayload])
      catch {
        case NonFatal(e) =>
          notifications.add(
            ServerNotification.logError(
              s"${tag}failed to parse results: ${e.getMessage}",
              Some(moduleId)
            )
          )
          None
      }
    else {
      notifications.add(
        ServerNotification.logError(
          s"${tag}forked test process crashed (exit $exitCode)",
          Some(moduleId)
        )
      )
      None
    }
  }

  private def streamStdout(
      forkId: Int,
      proc: os.SubProcess,
      logFile: os.Path,
      tag: String,
      notifications: ServerNotificationsLogger,
      moduleId: String
  ): Unit = {
    val reader = new java.io.BufferedReader(new java.io.InputStreamReader(proc.stdout.wrapped))
    try {
      var line = reader.readLine()
      while (line != null) {
        if line.startsWith(ForkedTestEnvelope.LinePrefix) then {
          val json = line.substring(ForkedTestEnvelope.LinePrefix.length)
          try {
            val env = json.parseJson[ForkedTestEnvelope]
            renderEnvelope(env, logFile, tag, notifications, moduleId)
          } catch {
            case NonFatal(_) =>
              os.write.append(logFile, line + "\n", createFolders = true)
              notifications.add(ServerNotification.logInfo(s"$tag$line", Some(moduleId)))
          }
        } else {
          os.write.append(logFile, line + "\n", createFolders = true)
          notifications.add(ServerNotification.logInfo(s"$tag$line", Some(moduleId)))
        }
        line = reader.readLine()
      }
    } catch {
      case _: java.io.IOException => ()
      case NonFatal(e) =>
        notifications.add(
          ServerNotification.logError(s"${tag}stdout error: ${e.getMessage}", Some(moduleId))
        )
    }
  }

  private def streamStderr(
      proc: os.SubProcess,
      logFile: os.Path,
      tag: String,
      notifications: ServerNotificationsLogger,
      moduleId: String
  ): Unit = {
    val reader = new java.io.BufferedReader(new java.io.InputStreamReader(proc.stderr.wrapped))
    try {
      var line = reader.readLine()
      while (line != null) {
        os.write.append(logFile, line + "\n", createFolders = true)
        notifications.add(ServerNotification.logError(s"$tag$line", Some(moduleId)))
        line = reader.readLine()
      }
    } catch {
      case _: java.io.IOException => ()
      case NonFatal(e) =>
        notifications.add(
          ServerNotification.logError(s"${tag}stderr error: ${e.getMessage}", Some(moduleId))
        )
    }
  }

  private def renderEnvelope(
      env: ForkedTestEnvelope,
      logFile: os.Path,
      tag: String,
      notifications: ServerNotificationsLogger,
      moduleId: String
  ): Unit = env match {
    case ForkedTestEnvelope.ForkStarted(_) =>
      notifications.add(ServerNotification.logDebug(s"${tag}started", Some(moduleId)))
    case ForkedTestEnvelope.SuiteStarted(name, _) =>
      notifications.add(ServerNotification.logDebug(s"${tag}suite started: $name", Some(moduleId)))
    case ForkedTestEnvelope.SuiteCompleted(name, _, output) =>
      val header = s"${tag}${name} completed"
      os.write.append(logFile, s"$header\n$output\n", createFolders = true)
      notifications.add(ServerNotification.logInfo(header, Some(moduleId)))
      output.linesIterator.foreach { l =>
        notifications.add(ServerNotification.logInfo(l, Some(moduleId)))
      }
    case ForkedTestEnvelope.UnattributedOutput(text) =>
      os.write.append(logFile, text, createFolders = true)
      text.linesIterator.foreach { l =>
        if l.nonEmpty then notifications.add(ServerNotification.logInfo(s"$tag$l", Some(moduleId)))
      }
    case ForkedTestEnvelope.ForkCompleted(_, totals) =>
      notifications.add(
        ServerNotification.logDebug(
          s"${tag}completed: ${totals.total} tests, ${totals.passed} passed, ${totals.failed} failed",
          Some(moduleId)
        )
      )
  }

  private def aggregate(perForkResults: Seq[DederTestResults]): DederTestResults =
    if perForkResults.isEmpty then DederTestResults.empty
    else
      DederTestResults(
        total = perForkResults.map(_.total).sum,
        passed = perForkResults.map(_.passed).sum,
        failed = perForkResults.map(_.failed).sum,
        errors = perForkResults.map(_.errors).sum,
        skipped = perForkResults.map(_.skipped).sum,
        duration = perForkResults.map(_.duration).sum,
        failedTestNames = perForkResults.flatMap(_.failedTestNames)
      )

  private def buildClasspath(runtimeClasspath: Seq[os.Path]): String = {
    val testRunnerClasspath = Seq(DederGlobals.projectRootDir / ".deder/test-runner.jar")
    (testRunnerClasspath ++ runtimeClasspath).map(_.toString).mkString(java.io.File.pathSeparator)
  }

  private def resolveJavaBinary(javaHome: Option[String]): String =
    javaHome
      .orElse(Option(System.getenv("JAVA_HOME")).filter(_.nonEmpty))
      .map(home => s"$home/bin/java")
      .getOrElse("java")
}
