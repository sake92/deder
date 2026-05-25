package ba.sake.deder.testing.forked

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.{Callable, Executors}
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal
import ba.sake.deder.*
import ba.sake.deder.testing.*
import ba.sake.tupson.{*, given}
import com.typesafe.scalalogging.StrictLogging

/** Result of a forked test run, including the directory where fork data was written. */
case class ForkedTestRun(results: DederTestResults, runDir: os.Path)

/** Outcome of a single fork attempt (not the retry loop). */
private enum ForkOutcome {
  case Success(payload: ForkedTestResultsPayload)
  case Crashed(
      exitCode: Int,
      inProgressSuiteNames: Set[String],
      allStartedSuiteNames: Set[String]
  )
  case TimedOut(
      inProgressSuiteNames: Set[String],
      allStartedSuiteNames: Set[String]
  )
}

object ForkedTestOrchestrator extends StrictLogging {

  private val MaxTestTimeMs = 30L * 60 * 1000
  private val MaxRetries = 3
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
  ): ForkedTestRun = {

    if maxTestForks > 1 && jvmOptions.exists(_.contains("-agentlib:jdwp=")) then {
      notifications.add(
        ServerNotification.logError(
          s"maxTestForks > 1 cannot be combined with a fixed JDWP port in jvmOptions. " +
            s"Set maxTestForks=1 or remove the JDWP option.",
          Some(moduleId)
        )
      )
      return ForkedTestRun(
        DederTestResults(total = 0, passed = 0, failed = 0, errors = 1, skipped = 0, duration = 0),
        outDir
      )
    }

    val testsToRun =
      if testOptions.testSelectors.isEmpty then discoveredTests
      else
        discoveredTests.flatMap { dft =>
          val testClassNames = dft.testClasses.map(_.className)
          val classSelectors = testOptions.testSelectors.map { ts =>
            ts.split("#") match {
              case Array(classNameSelector, _) => classNameSelector
              case _                           => ts
            }
          }
          val matchedNames = WildcardUtils.getMatches(testClassNames, classSelectors).toSet
          val filtered = dft.testClasses.filter(tc => matchedNames.contains(tc.className))
          Option.when(filtered.nonEmpty)(dft.copy(testClasses = filtered))
        }

    val history = TestHistory.load(outDir)
    val buckets = TestDistribution.distribute(testsToRun, history, maxTestForks)
    if buckets.isEmpty then {
      notifications.add(ServerNotification.logWarning("No tests found on the classpath.", Some(moduleId)))
      return ForkedTestRun(DederTestResults.empty, outDir)
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
            def call(): Option[ForkedTestResultsPayload] = {
              RequestContext.id.set(requestId)
              try runForkWithPermit(
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
              finally RequestContext.id.remove()
            }
          }
        }
      val futures = callables.map(forkExecutor.submit)
      val payloads = futures.flatMap { f =>
        try f.get()
        catch {
          case _: InterruptedException => Thread.currentThread().interrupt(); None
          case NonFatal(_) => None
        }
      }

      val aggregated = aggregate(payloads.map(_.results))
      val perClassStats = payloads.flatMap(_.perClassStats).toMap
      TestHistory.save(outDir, history.merge(perClassStats))
      ForkedTestRun(aggregated, runDir)
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
      spawnAndRunWithRetry(
        forkId = forkId,
        originalSlice = slice,
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
    } finally {
      sem.release()
    }
  }

  /** Runs a fork with retry: on crash, marks in-progress suites as ERROR and retries
    * the remaining (not-yet-attempted) suites. Loops until all suites are attempted.
    */
  private def spawnAndRunWithRetry(
      forkId: Int,
      originalSlice: Seq[DiscoveredFrameworkTests],
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

    var currentSlice = originalSlice
    var crashErrorSuites = Vector.empty[DederTestSuiteReport]
    var retryAttempt = 0

    while (currentSlice.nonEmpty) {
      // Safety net: don't retry forever if every attempt fails the same way.
      if (retryAttempt > MaxRetries) {
        val tag = if showForkTag then s"[fork-$forkId] " else ""
        notifications.add(
          ServerNotification.logError(
            s"${tag}giving up after ${retryAttempt} attempts. Reporting remaining suites as errors.",
            Some(moduleId)
          )
        )
        for dft <- currentSlice; tc <- dft.testClasses do
          crashErrorSuites = crashErrorSuites :+ makeErrorSuite(
            tc.className, -1, retryAttempt,
            "Max retries exceeded"
          )
        currentSlice = Seq.empty
      } else {
        val forkDir =
          if (retryAttempt == 0) runDir / s"fork-$forkId"
          else runDir / s"fork-$forkId-retry-$retryAttempt"

        spawnAndRun(
          forkId = forkId,
          slice = currentSlice,
          javaBinary = javaBinary,
          fullClasspath = fullClasspath,
          jvmOptions = jvmOptions,
          envVars = envVars,
          testOptions = testOptions,
          testParallelism = testParallelism,
          forkDir = forkDir,
          showForkTag = showForkTag,
          notifications = notifications,
          moduleId = moduleId
        ) match {
          case ForkOutcome.Success(payload) =>
            return Some(mergeCrashSuites(payload, crashErrorSuites))

          case ForkOutcome.Crashed(exitCode, inProgress, allStarted) =>
            val tag = if showForkTag then s"[fork-$forkId] " else ""
            if (allStarted.isEmpty) {
              // Framework initialization or JVM-level crash before any suite started.
              // Mark ALL current suites as errors — retrying would hit the same crash.
              for dft <- currentSlice; tc <- dft.testClasses do
                crashErrorSuites = crashErrorSuites :+ makeErrorSuite(
                  tc.className, exitCode, retryAttempt,
                  s"Fork crashed (exit $exitCode) before any suite could start"
                )
              notifications.add(
                ServerNotification.logError(
                  s"${tag}crashed on attempt $retryAttempt (exit $exitCode) before any suite started. " +
                    s"Reporting all ${currentSlice.flatMap(_.testClasses).size} suites as errors.",
                  Some(moduleId)
                )
              )
              currentSlice = Seq.empty
            } else {
              for name <- inProgress do
                crashErrorSuites = crashErrorSuites :+ makeErrorSuite(name, exitCode, retryAttempt)
              val remainingCount =
                currentSlice.flatMap(_.testClasses).count(tc => !allStarted.contains(tc.className))
              notifications.add(
                ServerNotification.logError(
                  s"${tag}crashed on attempt $retryAttempt (exit $exitCode). " +
                    s"Interrupted suites: ${inProgress.mkString(", ")}. " +
                    s"Retrying $remainingCount remaining suites...",
                  Some(moduleId)
                )
              )
              currentSlice = removeSuiteNames(currentSlice, allStarted)
            }
            retryAttempt += 1

          case ForkOutcome.TimedOut(inProgress, allStarted) =>
            val tag = if showForkTag then s"[fork-$forkId] " else ""
            if (allStarted.isEmpty) {
              for dft <- currentSlice; tc <- dft.testClasses do
                crashErrorSuites = crashErrorSuites :+ makeErrorSuite(
                  tc.className, -1, retryAttempt,
                  "Fork timed out before any suite could start"
                )
              notifications.add(
                ServerNotification.logError(
                  s"${tag}timed out on attempt $retryAttempt before any suite started. " +
                    s"Reporting all ${currentSlice.flatMap(_.testClasses).size} suites as errors.",
                  Some(moduleId)
                )
              )
              currentSlice = Seq.empty
            } else {
              for name <- inProgress do
                crashErrorSuites = crashErrorSuites :+ makeErrorSuite(name, -1, retryAttempt)
              val remainingCount =
                currentSlice.flatMap(_.testClasses).count(tc => !allStarted.contains(tc.className))
              notifications.add(
                ServerNotification.logError(
                  s"${tag}timed out on attempt $retryAttempt. " +
                    s"Interrupted suites: ${inProgress.mkString(", ")}. " +
                    s"Retrying $remainingCount remaining suites...",
                  Some(moduleId)
                )
              )
              currentSlice = removeSuiteNames(currentSlice, allStarted)
            }
            retryAttempt += 1
        }
      }
    }

    if (crashErrorSuites.nonEmpty) Some(makeErrorPayload(crashErrorSuites))
    else None
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
  ): ForkOutcome = {
    val tag = if showForkTag then s"[fork-$forkId] " else ""
    os.makeDir.all(forkDir)
    val argsFilePath = forkDir / "fork-args.json"
    val resultsFilePath = forkDir / s"fork-results-${java.util.UUID.randomUUID()}.json"
    val stdoutLog = forkDir / "stdout.log"
    val stderrLog = forkDir / "stderr.log"
    val suiteOutputs = new java.util.concurrent.ConcurrentHashMap[String, StringBuilder]()
    val startedSuites = java.util.concurrent.ConcurrentHashMap.newKeySet[String]()
    val completedSuites = java.util.concurrent.ConcurrentHashMap.newKeySet[String]()
    if os.exists(stdoutLog) then os.remove(stdoutLog)
    if os.exists(stderrLog) then os.remove(stderrLog)

    val args = ForkedTestArgs(
      forkId = forkId,
      discoveredTests = slice,
      testSelectors = testOptions.testSelectors,
      testParallelism = testParallelism,
      resultsFile = resultsFilePath.toString
    )
    os.write.over(argsFilePath, args.toJson(spaces = 0, sort = false))

    val cmd = Seq(javaBinary) ++ jvmOptions ++ Seq(
      "-cp",
      fullClasspath,
      "shaded.ba.sake.deder.testing.forked.ForkedTestMain",
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

    withProcessCleanup(proc) {
      val stdoutThread = new Thread(
        () =>
          streamStdout(
            forkId, proc, stdoutLog, tag, notifications, moduleId, suiteOutputs,
            startedSuites, completedSuites
          ),
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

      val finished = waitForForkProcess(proc, tag, moduleId, notifications)
      stdoutThread.join(300)
      stderrThread.join(300)

      if (finished) {
        readForkResults(proc, resultsFilePath, suiteOutputs, startedSuites, completedSuites, tag, notifications, moduleId)
      } else {
        val inProgress = startedSuites.asScala.toSet -- completedSuites.asScala.toSet
        ForkOutcome.TimedOut(inProgress, startedSuites.asScala.toSet)
      }
    }
  }

  private def streamStdout(
      forkId: Int,
      proc: os.SubProcess,
      logFile: os.Path,
      tag: String,
      notifications: ServerNotificationsLogger,
      moduleId: String,
      suiteOutputs: java.util.concurrent.ConcurrentHashMap[String, StringBuilder],
      startedSuites: java.util.Set[String],
      completedSuites: java.util.Set[String]
  ): Unit = {
    val reader = new java.io.BufferedReader(new java.io.InputStreamReader(proc.stdout.wrapped))
    try {
      var line = reader.readLine()
      while (line != null) {
        if line.startsWith(ForkedTestEnvelope.LinePrefix) then {
          val json = line.substring(ForkedTestEnvelope.LinePrefix.length)
          try {
            val env = json.parseJson[ForkedTestEnvelope]
            renderEnvelope(env, logFile, tag, notifications, moduleId, suiteOutputs, startedSuites, completedSuites)
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
      moduleId: String,
      suiteOutputs: java.util.concurrent.ConcurrentHashMap[String, StringBuilder],
      startedSuites: java.util.Set[String],
      completedSuites: java.util.Set[String]
  ): Unit = env match {
    case ForkedTestEnvelope.ForkStarted(_) =>
      notifications.add(ServerNotification.logDebug(s"${tag}started", Some(moduleId)))
    case ForkedTestEnvelope.SuiteStarted(name, _) =>
      startedSuites.add(name)
      notifications.add(ServerNotification.logInfo(s"${tag}▶ $name", Some(moduleId)))
    case ForkedTestEnvelope.SuiteCompleted(name, _, output) =>
      completedSuites.add(name)
      val header = s"${tag}${name} completed"
      if output.nonEmpty then {
        val key = DederTestNames.normalizeSuiteName(name)
        val builder = suiteOutputs.computeIfAbsent(key, _ => new StringBuilder())
        builder.synchronized {
          builder.append(output)
        }
      }
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
        failedTestNames = perForkResults.flatMap(_.failedTestNames),
        suites = perForkResults.flatMap(_.suites).sortBy(_.name)
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

  private def withProcessCleanup[T](proc: os.SubProcess)(body: => T): T =
    try body
    finally if (proc.wrapped.isAlive()) proc.wrapped.destroyForcibly()

  private def waitForForkProcess(
      proc: os.SubProcess,
      tag: String,
      moduleId: String,
      notifications: ServerNotificationsLogger
  ): Boolean = {
    val finished =
      try proc.waitFor(MaxTestTimeMs)
      catch case _: InterruptedException => false
    if !finished then {
      proc.wrapped.destroyForcibly()
      notifications.add(
        ServerNotification.logError(
          s"${tag}forked test process timed out after ${MaxTestTimeMs / 60000} minutes, killed.",
          Some(moduleId)
        )
      )
    }
    finished
  }

  private def readForkResults(
      proc: os.SubProcess,
      resultsFilePath: os.Path,
      suiteOutputs: java.util.concurrent.ConcurrentHashMap[String, StringBuilder],
      startedSuites: java.util.Set[String],
      completedSuites: java.util.Set[String],
      tag: String,
      notifications: ServerNotificationsLogger,
      moduleId: String
  ): ForkOutcome =
    if os.exists(resultsFilePath) then
      try {
        val payload = os.read(resultsFilePath).parseJson[ForkedTestResultsPayload]
        ForkOutcome.Success(
          payload.copy(
            results = payload.results.withSuiteStdout(suiteOutputs.asScala.view.mapValues(_.result()).toMap)
          )
        )
      } catch {
        case NonFatal(e) =>
          notifications.add(
            ServerNotification.logError(
              s"${tag}failed to parse results: ${e.getMessage}",
              Some(moduleId)
            )
          )
          val inProgress = startedSuites.asScala.toSet -- completedSuites.asScala.toSet
          ForkOutcome.Crashed(-1, inProgress, startedSuites.asScala.toSet)
      }
    else {
      val exitCode =
        try proc.exitCode()
        catch case _: IllegalThreadStateException => -1
      val inProgress = startedSuites.asScala.toSet -- completedSuites.asScala.toSet
      notifications.add(
        ServerNotification.logError(
          s"${tag}forked test process crashed (exit $exitCode). " +
            s"Interrupted suites: ${if (inProgress.nonEmpty) inProgress.mkString(", ") else "none"}",
          Some(moduleId)
        )
      )
      ForkOutcome.Crashed(exitCode, inProgress, startedSuites.asScala.toSet)
    }

  /** Build a synthetic ERROR suite report for a suite interrupted by a fork crash. */
  private def makeErrorSuite(
      suiteName: String,
      exitCode: Int,
      retryAttempt: Int,
      extraMessage: String = ""
  ): DederTestSuiteReport = {
    val msg = s"Test suite was interrupted by forked JVM exit (code $exitCode) on retry attempt $retryAttempt" +
      (if (extraMessage.isEmpty) "" else s": $extraMessage")
    DederTestSuiteReport(
      name = suiteName,
      testCases = Seq(DederTestCaseReport(
        name = suiteName,
        classname = suiteName,
        status = DederTestStatus.Error,
        duration = 0L,
        failure = Some(DederTestFailure(
          message = Some(msg),
          stackTrace = None
        ))
      )),
      duration = 0L
    )
  }

  /** Remove test classes whose class names are in `namesToRemove` from the slice.
    * Entire framework entries that become empty are dropped.
    */
  private def removeSuiteNames(
      slice: Seq[DiscoveredFrameworkTests],
      namesToRemove: Set[String]
  ): Seq[DiscoveredFrameworkTests] =
    slice.flatMap { dft =>
      val remaining = dft.testClasses.filterNot(tc => namesToRemove.contains(tc.className))
      if remaining.nonEmpty then Some(dft.copy(testClasses = remaining))
      else None
    }

  /** Merge crash error suites into a successful payload's results. */
  private def mergeCrashSuites(
      payload: ForkedTestResultsPayload,
      crashSuites: Seq[DederTestSuiteReport]
  ): ForkedTestResultsPayload =
    if crashSuites.isEmpty then payload
    else {
      val newResults = payload.results.copy(
        total = payload.results.total + crashSuites.map(_.total).sum,
        errors = payload.results.errors + crashSuites.map(_.errors).sum,
        failedTestNames = payload.results.failedTestNames ++
          crashSuites.flatMap(_.testCases.map(tc => s"${tc.classname}.${tc.name}")),
        suites = (payload.results.suites ++ crashSuites).sortBy(_.name)
      )
      payload.copy(results = newResults)
    }

  /** Build a synthetic payload from only crash error suites (when all suites crashed). */
  private def makeErrorPayload(crashSuites: Seq[DederTestSuiteReport]): ForkedTestResultsPayload = {
    val results = DederTestResults(
      total = crashSuites.map(_.total).sum,
      passed = 0,
      failed = 0,
      errors = crashSuites.map(_.errors).sum,
      skipped = 0,
      duration = 0L,
      failedTestNames = crashSuites.flatMap(_.testCases.map(tc => s"${tc.classname}.${tc.name}")),
      suites = crashSuites.sortBy(_.name)
    )
    ForkedTestResultsPayload(results, Map.empty)
  }
}
