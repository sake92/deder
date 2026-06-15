package ba.sake.deder

import java.net.URLClassLoader
import java.time.{Duration, Instant}
import java.util.UUID
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import java.util.concurrent.TimeUnit
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NonFatal
import scala.util.Using
import com.typesafe.scalalogging.StrictLogging
import io.opentelemetry.api.trace.StatusCode
import scala.jdk.CollectionConverters.*
import ba.sake.deder.config.{ConfigParser, DederProject}
import ba.sake.deder.cli.TabCompleter
import ba.sake.deder.deps.{DependencyResolver, DependencyResolverApi}
import ba.sake.deder.plugin.{LoadedPlugin, PluginLoader, PluginLoaderApi}
import ba.sake.deder.toPrettyString
import ba.sake.tupson.JsonRW
import ch.epfl.scala.bsp4j.{BuildTargetEvent, BuildTargetEventKind, BuildTargetIdentifier, DidChangeBuildTarget}
import ba.sake.deder.scalajs.ScalaJsTasks
import ba.sake.deder.scalanative.ScalaNativeTasks
import ba.sake.deder.graalvm.GraalVmNativeImageTasks
import ba.sake.deder.publish.PublishTasks

class DederProjectState(
    coreTasks: CoreTasks,
    runTasks: RunTasks,
    publishTasks: PublishTasks,
    scalaJsTasks: ScalaJsTasks,
    scalaNativeTasks: ScalaNativeTasks,
    graalvmNativeImageTasks: GraalVmNativeImageTasks,
    tasksRegistry: TasksRegistry,
    maxInactiveSeconds: Int,
    taskLockTimeoutSeconds: Int,
    bspFileChangeNotifyCooldownSeconds: Int,
    onShutdown: () => Unit,
    configFile: os.Path,
    val internals: DederProjectInternalsImpl
) extends StrictLogging {

  private val maxInactiveDuration = Duration.ofSeconds(maxInactiveSeconds)
  private val taskLockTimeoutEnabled = taskLockTimeoutSeconds > 0

  @volatile private var shutdownStarted = false

  private val configParser = ConfigParser(writeJson = true)
  private val baseTasks = tasksRegistry.all

  private val stateLock = new AnyRef
  private var current: Either[String, DederProjectStateData] = Left("Project state is uninitialized")
  // used for BSP
  private var lastGood: Either[String, DederProjectStateData] = Left("Project state is uninitialized")

  private val lastRequestEndedAt = new java.util.concurrent.atomic.AtomicReference[Instant](Instant.now())
  private val inFlightRequests = new AtomicInteger(0)

  private val watchedTasksLock = new AnyRef
  private var watchedTasks = Seq.empty[WatchedTaskData]
  private var loadedPlugins = Seq.empty[LoadedPlugin]

  // Track active BSP servers for graceful teardown on CLI shutdown
  private val bspServers = new java.util.concurrent.ConcurrentLinkedQueue[ba.sake.deder.bsp.DederBspServer]()

  // Per-module throttle: last time we sent a buildTarget/didChange for this moduleId
  private val lastBspFileChangeNotify = new java.util.concurrent.ConcurrentHashMap[String, java.time.Instant]()

  // Callback to release the server lock early (before executor shutdown)
  private var releaseServerLockCallback: () => Unit = () => ()
  def setReleaseServerLock(cb: () => Unit): Unit = { releaseServerLockCallback = cb }
  def releaseServerLock(): Unit = releaseServerLockCallback()

  reloadProject()

  scheduleInactiveShutdownChecker()

  def readState(useLastGood: Boolean): Either[String, DederProjectStateData] =
    stateLock.synchronized {
      if useLastGood then
        lastGood match {
          case Left(_)      => current // current has latest error message!
          case Right(value) => Right(value)
        }
      else current
    }

  private def readCurrentOrLastGood: Either[String, DederProjectStateData] =
    stateLock.synchronized { current.orElse(lastGood) }

  /** Returns watch-ignore patterns from the Pkl project config.
    * Uses lastGood state as a fallback (consistent with BSP behavior).
    * Returns an empty Seq if no valid project config is loaded. */
  def getWatchIgnorePatterns(): Seq[String] =
    readCurrentOrLastGood match {
      case Right(data) =>
        val watchIgnore = data.projectConfig.watchIgnore
        if watchIgnore != null then watchIgnore.asScala.toSeq
        else Seq.empty
      case Left(_) => Seq.empty
    }

  def reloadProject(): Unit = stateLock.synchronized {
    // TODO make sure no requests are running
    // because we need to make sure locks are not held while we refresh the state (new locks are instantiated)
    if !os.exists(configFile) || !os.isFile(configFile) then {
      val errorMessage =
        s"No deder.pkl found at '${configFile}'. Create a deder.pkl configuration file in your project root to get started."
      logger.warn(errorMessage)
      current = Left(errorMessage)
    } else
      try {
        val newProjectConfig = configParser.parse(configFile)
        newProjectConfig match {
          case Left(errorMessage) =>
            logger.warn(s"Failed to load project config: $errorMessage")
            current = Left(errorMessage)
          case Right(newConfig) =>
            val userRepoUrls = newConfig.repositories.asScala.map(_.url).toSeq
            val assembledRepos =
              try DependencyResolver.assembleRepositories(userRepoUrls, newConfig.includeDefaultRepos)
              catch
                case e: IllegalArgumentException =>
                  logger.warn(s"Invalid repository configuration: ${e.getMessage}")
                  current = Left(e.getMessage)
                  return
            val dependencyResolver = new DependencyResolver(assembledRepos, internals.registry)

            // Load plugin tasks before TasksResolver so they are included in the execution graph.
            // Plugin tasks are kept separately and the effective registry is rebuilt per reload.
            val coreTasksApi = CoreTasksApiAdapter(coreTasks, runTasks, publishTasks, graalvmNativeImageTasks)
            val scalaJsTasksApi = ScalaJsTasksApiAdapter(scalaJsTasks)
            val scalaNativeTasksApi = ScalaNativeTasksApiAdapter(scalaNativeTasks)
            val pluginLoader =
              PluginLoader(coreTasksApi, scalaJsTasksApi, scalaNativeTasksApi, dependencyResolver, internals)
            val loadResult = pluginLoader.load(loadedPlugins, configFile, newConfig)
            loadedPlugins = loadResult.loadedPlugins

            // Build info for ALL configured plugins, including failed ones with errors.
            val configuredIds = newConfig.plugins.asScala.map(_.id).toSeq
            val loadedMap = loadedPlugins.map(lp => lp.plugin.id ->
              LoadedPluginInfo(lp.plugin.id, lp.tasks.map(_.name))).toMap
            val errorMap = loadResult.errors.map(e => e.pluginId -> e.error).toMap
            val allPluginInfo = configuredIds.map { id =>
              loadedMap.getOrElse(id,
                LoadedPluginInfo(id, Seq.empty, Some(errorMap.getOrElse(id, "Not loaded")))
              )
            }
            internals.setLoadedPlugins(allPluginInfo)
            // TODO prepend plugin id to task name to avoid conflicts? e.g myplugin:compile ?
            val pluginTasks = loadedPlugins.flatMap(_.tasks).map(_.asInstanceOf[Task[?, ?, ?]])
            val effectiveRegistry = TasksRegistry(baseTasks ++ pluginTasks)
            val tasksResolver = TasksResolver(newConfig, effectiveRegistry)
            val executionPlanner =
              ExecutionPlanner(tasksResolver.taskInstancesGraph, tasksResolver.taskInstancesPerModule)

            val goodProjectStateData =
              DederProjectStateData(newConfig, effectiveRegistry, tasksResolver, executionPlanner, dependencyResolver)
            lastGood = Right(goodProjectStateData)
            current = Right(goodProjectStateData)
            // Update global semaphores from project config
            val cpus = Runtime.getRuntime.availableProcessors()
            val activeCompilers = if newConfig.maxActiveCompilers <= 0 then cpus else newConfig.maxActiveCompilers.toInt
            val concurrentForks = if newConfig.maxConcurrentTestForks <= 0 then cpus else newConfig.maxConcurrentTestForks.toInt
            DederGlobals.setCompileSemaphore(activeCompilers)
            DederGlobals.setTestForkSemaphore(concurrentForks)
            triggerConfigWatchedTasks()
        }
      } catch {
        case NonFatal(e) =>
          val errorMessage = s"Error during project load: ${e.getMessage}"
          logger.warn(errorMessage)
          current = Left(errorMessage)
      }
  }

  def executeCLI(
      moduleSelectors: Seq[String],
      taskName: String,
      args: Seq[String],
      serverNotificationsLogger: ServerNotificationsLogger,
      useLastGood: Boolean = false,
      startWatch: Boolean = false,
      exitOnEnd: Boolean = true,
      watch: Boolean = false
  ): Unit = try {
    val ctx = RequestContext.current.get()
    val state = readState(useLastGood) match
      case Left(err) => throw TaskEvaluationException(s"Project state is not available: ${err}")
      case Right(s)  => s

    val allModuleIds = state.tasksResolver.allModules.map(_.id)
    val selectedModuleIds =
      if moduleSelectors.isEmpty then Right(allModuleIds)
      else WildcardUtils.getMatchesOrRecommendations(allModuleIds, moduleSelectors)

    selectedModuleIds match {
      case Left(recommendedModuleIds) =>
        val msg =
          if recommendedModuleIds.isEmpty then s"No modules found for selectors: ${moduleSelectors.mkString(", ")}"
          else s"No modules found, did you mean: ${recommendedModuleIds.mkString(", ")} ?"
        serverNotificationsLogger.add(ServerNotification.logError(msg))
        serverNotificationsLogger.add(ServerNotification.RequestFinished(success = false))
      case Right(moduleIds) =>
        val relevantModuleAndTasks = state.executionPlanner.getTaskInstances(moduleIds, taskName) match {
          case Left(recommendations) =>
            val msg =
              if recommendations.isEmpty then s"No '${taskName}' tasks found"
              else s"No '${taskName}' tasks found, did you mean: ${recommendations.mkString(", ")} ?"
            serverNotificationsLogger.add(ServerNotification.logError(msg))
            serverNotificationsLogger.add(ServerNotification.RequestFinished(success = false))
            Seq.empty
          case Right(values) =>
            val plural = if values.size > 1 then "s" else ""
            val modulesString =
              values.take(5).map(_._1).mkString(", ") + (if values.size > 5 then s", and ${values.size - 5} more"
                                                         else "")
            serverNotificationsLogger.add(
              ServerNotification.logInfo(
                s"Executing '${taskName}' task on module${plural}: ${modulesString}"
              )
            )
            values
        }
        val relevantModuleIds = relevantModuleAndTasks.map(_._1)
        val isTaskSingleton = relevantModuleAndTasks.exists(_._2.task.singleton)
        if isTaskSingleton && relevantModuleIds.length > 1 then
          throw RuntimeException(s"Task '${taskName}' is singleton, cannot execute it on multiple modules at once")
        val execStartNanos = System.nanoTime()
        val results = executeTasks(
          ctx.requestId,
          CallerType.Cli,
          relevantModuleIds,
          taskName,
          args,
          watch = watch,
          serverNotificationsLogger,
          useLastGood = useLastGood
        )
        val totalDuration = Duration.ofNanos(System.nanoTime() - execStartNanos)
        // summarize across modules
        if results.nonEmpty then {
          val successes = results.collect { case s: TaskExecResult.Success => s }
          val failures = results.collect {
            case f: TaskExecResult.Failure =>
              serverNotificationsLogger.add(
                ServerNotification.logError(s"${f.taskInstance.moduleId}: ${f.error}")
              )
              ModuleFailure(f.taskInstance.moduleId, f.error, None)
            case s: TaskExecResult.Skipped =>
              serverNotificationsLogger.add(
                ServerNotification.logError(
                  s"${s.taskInstance.moduleId}: Skipped — ${s.because.taskInstance.moduleId} failed: ${s.because.error}"
                )
              )
              ModuleFailure(s.taskInstance.moduleId, s.because.error, Some(s.because.taskInstance.moduleId))
          }
          // generate cross-module summary from successful results
          if successes.nonEmpty then {
            val task = successes.head.taskInstance.task
            val moduleResults = successes.sortBy(_.taskInstance.moduleId).map(r => r.taskInstance.moduleId -> r.value)
            // Render cross-module summary in the chosen output format
            val summary = task.summarizeValueUnsafe(moduleResults, failures, totalDuration)
            given JsonRW[Any] = task.summarizable.jsonRW.asInstanceOf[JsonRW[Any]]
            given PlainTextWritable[Any] = task.summarizable.plainTextW.asInstanceOf[PlainTextWritable[Any]]
            given MermaidWritable[Any] = task.summarizable.mermaidW.asInstanceOf[MermaidWritable[Any]]
            given DotWritable[Any] = task.summarizable.dotW.asInstanceOf[DotWritable[Any]]
            val rendered = OutputFormat.render[Any](summary, ctx.outputFormat)
            val output = ctx.outputFormat match
              case OutputFormat.PlainText => rendered + s"\nTotal time: ${totalDuration.toPrettyString}"
              case _                      => rendered
            serverNotificationsLogger.add(ServerNotification.Output(output))
          }
        }
        if startWatch then {
          relevantModuleAndTasks.foreach { case (moduleId, taskInstance) =>
            val affectingSourceFileTasks = state.executionPlanner.getAffectingSourceFileTasks(moduleId, taskName)
            val affectingConfigValueTasks = state.executionPlanner.getAffectingConfigValueTasks(moduleId, taskName)
            watchedTasksLock.synchronized {
              watchedTasks = watchedTasks.appended(
                WatchedTaskData(
                  ctx,
                  taskInstance,
                  args,
                  serverNotificationsLogger,
                  useLastGood,
                  affectingSourceFileTasks,
                  affectingConfigValueTasks
                )
              )
            }
            serverNotificationsLogger.add(
              ServerNotification.logInfo(s"⌚ Executing ${moduleId}.${taskName} in watch mode...", moduleId)
            )
          }
        }
        if exitOnEnd then {
          val allSuccessful = results.forall {
            case TaskExecResult.Success(ti, value, _, _) => ti.task.isResultSuccessfulUnsafe(value)
            case _                                    => false // Failure and Skipped are always unsuccessful
          }
          serverNotificationsLogger.add(ServerNotification.RequestFinished(success = allSuccessful))
        }
    }
  } catch {
    case NonFatal(e) =>
      serverNotificationsLogger.add(ServerNotification.logError(e.getMessage))
      if !watch then serverNotificationsLogger.add(ServerNotification.RequestFinished(success = false))
  }

  def executeTask[T](
      moduleId: String,
      task: Task[T, ?, ?],
      args: Seq[String],
      serverNotificationsLogger: ServerNotificationsLogger,
      watch: Boolean = false,
      useLastGood: Boolean = false,
      requestId: Option[String] = None,
      callerType: CallerType = CallerType.Cli
  ): (res: T, changed: Boolean, fromCache: Boolean) = {
    val span = OTEL.TRACER
      .spanBuilder(s"${moduleId}.${task.name}")
      .setAttribute("moduleId", moduleId)
      .setAttribute("taskName", task.name)
      .startSpan()
    try {
      Using.resource(span.makeCurrent()) { scope =>
        val reqId = requestId.getOrElse(UUID.randomUUID().toString)
        val resOpt =
          executeTasks(
            reqId,
            callerType,
            Seq(moduleId),
            task.name,
            args,
            watch,
            serverNotificationsLogger,
            useLastGood
          )

        val res = resOpt match {
          case Seq(singleResult) => singleResult
          case Seq() =>
            throw TaskEvaluationException(s"Task '${task.name}' on module '${moduleId}' did not produce a result")
          case _ =>
            throw TaskEvaluationException(s"Multiple results returned for task '${task.name}' on module '${moduleId}'")
        }

        res match
          case TaskExecResult.Success(_, value, changed, fromCache) => (value.asInstanceOf[T], changed, fromCache)
          case TaskExecResult.Failure(ti, error) =>
            throw TaskEvaluationException(s"${ti.id}: $error")
          case TaskExecResult.Skipped(ti, because) =>
            throw TaskEvaluationException(s"${ti.id}: Skipped — ${because.taskInstance.id} failed: ${because.error}")
      }
    } catch {
      case e: Throwable =>
        span.recordException(e)
        span.setStatus(StatusCode.ERROR)
        throw e
    } finally span.end()
  }

  // execute a single task on many modules
  def executeTasks(
      requestId: String,
      callerType: CallerType,
      moduleIds: Seq[String], // nonempty please :')
      taskName: String,
      args: Seq[String],
      watch: Boolean,
      serverNotificationsLogger: ServerNotificationsLogger,
      useLastGood: Boolean
  ): Seq[TaskExecResult] =
    val requestStartNanos = System.nanoTime()
    val requestStartInstant = java.time.Instant.now()
    internals.recordRequestStarted(requestId, callerType, taskName, moduleIds, requestStartInstant)
    try {
      inFlightRequests.incrementAndGet()
      if shutdownStarted then throw TaskEvaluationException("Cannot execute tasks - server is shutting down")

      val state = readState(useLastGood) match
        case Left(err) => throw TaskEvaluationException(s"Project state is not available: ${err}")
        case Right(s)  => s

      val tasksExecStages = state.executionPlanner.getExecStages(moduleIds, taskName)
      val tasksExecutor =
        TasksExecutor(
          state.projectConfig,
          state.tasksResolver.modulesGraph,
          state.tasksResolver.taskInstancesGraph,
          state.dependencyResolver,
          internals
        )
      val allTaskInstances = tasksExecStages.flatten.sortBy(_.id) // essential!!
      val acquiredLocks = ArrayBuffer.empty[TaskInstance]
      try {
        allTaskInstances.foreach { taskInstance =>
          if taskLockTimeoutEnabled then
            val acquired = taskInstance.lock.tryLock(taskLockTimeoutSeconds.toLong, TimeUnit.SECONDS)
            if !acquired then
              throw TaskLockTimeoutException(
                s"Timed out waiting for lock on task '${taskInstance.id}' after ${taskLockTimeoutSeconds}s. " +
                  s"The lock is held by another in-flight request. Consider increasing 'taskLockTimeoutSeconds' in .deder/server.properties."
              )
          else taskInstance.lock.lock()
          acquiredLocks += taskInstance
        }
        DederGlobals.cancellationTokens.put(requestId, new AtomicBoolean(false))
        val result = tasksExecutor.execute(
          tasksExecStages,
          moduleIds,
          taskName,
          args,
          watch,
          serverNotificationsLogger
        )
        val duration = Duration.ofNanos(System.nanoTime() - requestStartNanos)
        val cancelled = Option(DederGlobals.cancellationTokens.get(requestId)).exists(_.get())

        // Collect ALL errors from the result set — Failure, Skipped, and
        // Success-with-unsuccessful-result (e.g., compile with errors):
        val errors = result.flatMap {
          case s: TaskExecResult.Success =>
            if !s.taskInstance.task.isResultSuccessfulUnsafe(s.value) then
              Some(s"${s.taskInstance.id}: result was unsuccessful")
            else None
          case TaskExecResult.Failure(ti, msg) =>
            Some(s"${ti.id}: $msg")
          case TaskExecResult.Skipped(ti, because) =>
            Some(s"${ti.id}: skipped — ${because.taskInstance.id} failed: ${because.error}")
        }
        val hasFailures = errors.nonEmpty

        internals.recordRequestCompleted(
          requestId, taskName,
          success = !cancelled && !hasFailures,
          duration, callerType,
          error = if errors.nonEmpty then Some(errors.mkString(" | ")) else None
        )
        result
      } finally {
        acquiredLocks.reverse.foreach { taskInstance =>
          taskInstance.lock.unlock()
        }
        DederGlobals.cancellationTokens.remove(requestId)
      }
    } catch {
      case NonFatal(e) =>
        val duration = Duration.ofNanos(System.nanoTime() - requestStartNanos)
        val errMsg = Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
        internals.recordRequestCompleted(requestId, taskName, success = false, duration, callerType, error = Some(errMsg))
        // send notification about failure to client
        serverNotificationsLogger.add(ServerNotification.logError(e.getMessage))
        if !watch then serverNotificationsLogger.add(ServerNotification.RequestFinished(success = false))
        throw TaskEvaluationException(s"Error during execution of task '${taskName}': ${e.getMessage}", e)
    } finally {
      inFlightRequests.decrementAndGet()
      lastRequestEndedAt.set(Instant.now())
    }

  def cancelRequest(requestId: String): Unit = {
    val token = DederGlobals.cancellationTokens.get(requestId)
    logger.debug(s"Cancelling request ${requestId} with token ${token}")
    if token != null then token.set(true)
  }

  def cleanModules(moduleSelectors: Seq[String], onOutput: String => Unit): Boolean = {
    readCurrentOrLastGood match {
      case Left(err) =>
        onOutput(s"✗ Cannot clean modules, project state is not available: $err")
        logger.error(s"Cannot clean modules, project state is not available: ${err}")
        false
      case Right(state) =>
        val allModuleIds = state.tasksResolver.allModules.map(_.id)
        val resolvedModuleIds =
          if moduleSelectors.isEmpty then Right(allModuleIds)
          else WildcardUtils.getMatchesOrRecommendations(allModuleIds, moduleSelectors)

        resolvedModuleIds match {
          case Left(recommendations) =>
            val msg =
              if recommendations.isEmpty then s"✗ No modules found for selectors: ${moduleSelectors.mkString(", ")}"
              else s"✗ No modules found, did you mean: ${recommendations.mkString(", ")} ?"
            onOutput(msg)
            logger.error(msg)
            false
          case Right(moduleIds) =>
            val modulesTaskInstances = moduleIds
              .flatMap(state.tasksResolver.taskInstancesPerModule.get)
              .flatten
              .sortBy(_.id)
            val acquiredCleanLocks = ArrayBuffer.empty[TaskInstance]
            try {
              modulesTaskInstances.foreach { taskInstance =>
                if taskLockTimeoutEnabled then
                  val acquired = taskInstance.lock.tryLock(taskLockTimeoutSeconds.toLong, TimeUnit.SECONDS)
                  if !acquired then
                    throw TaskLockTimeoutException(
                      s"Timed out waiting for lock on task '${taskInstance.id}' while cleaning after ${taskLockTimeoutSeconds}s. " +
                        s"The lock is held by another in-flight request."
                    )
                else taskInstance.lock.lock()
                acquiredCleanLocks += taskInstance
              }

              var totalSuccesses = 0
              var totalFailures = 0
              var totalBytes = 0L

              moduleIds.foreach { moduleId =>
                val outDir = DederGlobals.projectRootDir / ".deder/out" / moduleId
                try {
                  val estSize = DederCleaner.scanSize(outDir)
                  onOutput(s"Cleaning $moduleId (${DederCleaner.humanReadable(estSize)})...")
                  val actualSize = DederCleaner.cleanDir(outDir)
                  totalSuccesses += 1
                  totalBytes += actualSize
                } catch {
                  case NonFatal(e) =>
                    onOutput(s"  ✗ $moduleId: ${e.getMessage}")
                    totalFailures += 1
                }
              }

              val summary = buildCleanSummary(totalSuccesses, totalFailures, totalBytes, "module")
              onOutput(summary)
              totalFailures == 0
            } finally {
              acquiredCleanLocks.reverse.foreach(_.lock.unlock())
            }
        }
    }
  }

  def cleanTasks(moduleSelectors: Seq[String], taskPattern: String, onOutput: String => Unit): Boolean = {
    readCurrentOrLastGood match {
      case Left(err) =>
        onOutput(s"✗ Cannot clean tasks, project state is not available: $err")
        logger.error(s"Cannot clean tasks, project state is not available: ${err}")
        false
      case Right(state) =>
        val allModuleIds = state.tasksResolver.allModules.map(_.id)
        val resolvedModuleIds =
          if moduleSelectors.isEmpty then Right(allModuleIds)
          else WildcardUtils.getMatchesOrRecommendations(allModuleIds, moduleSelectors)

        resolvedModuleIds match {
          case Left(recommendations) =>
            val msg =
              if recommendations.isEmpty then s"✗ No modules found for selectors: ${moduleSelectors.mkString(", ")}"
              else s"✗ No modules found, did you mean: ${recommendations.mkString(", ")} ?"
            onOutput(msg)
            logger.error(msg)
            false
          case Right(moduleIds) =>
            state.executionPlanner.getTaskInstancesMatching(moduleIds, taskPattern) match {
              case Left(recommendations) =>
                val msg =
                  if recommendations.isEmpty then s"✗ No '$taskPattern' tasks found"
                  else s"✗ No '$taskPattern' tasks found, did you mean: ${recommendations.mkString(", ")} ?"
                onOutput(msg)
                logger.error(msg)
                false
              case Right(taskInstances) =>
                val sorted = taskInstances.map(_._2).sortBy(_.id)
                val acquiredCleanTaskLocks = ArrayBuffer.empty[TaskInstance]
                try {
                  sorted.foreach { taskInstance =>
                    if taskLockTimeoutEnabled then
                      val acquired = taskInstance.lock.tryLock(taskLockTimeoutSeconds.toLong, TimeUnit.SECONDS)
                      if !acquired then
                        throw TaskLockTimeoutException(
                          s"Timed out waiting for lock on task '${taskInstance.id}' while cleaning after ${taskLockTimeoutSeconds}s. " +
                            s"The lock is held by another in-flight request."
                        )
                    else taskInstance.lock.lock()
                    acquiredCleanTaskLocks += taskInstance
                  }

                  var totalSuccesses = 0
                  var totalFailures = 0
                  var totalBytes = 0L

                  taskInstances.foreach { (moduleId, taskInstance) =>
                    val taskName = taskInstance.task.name
                    val outDir = DederGlobals.projectRootDir / ".deder/out" / moduleId / taskName
                    try {
                      val estSize = DederCleaner.scanSize(outDir)
                      onOutput(s"Cleaning task '$taskName' on $moduleId (${DederCleaner.humanReadable(estSize)})...")
                      val actualSize = DederCleaner.cleanDir(outDir)
                      totalSuccesses += 1
                      totalBytes += actualSize
                    } catch {
                      case NonFatal(e) =>
                        onOutput(s"  ✗ $moduleId: ${e.getMessage}")
                        totalFailures += 1
                    }
                  }

                  val summary = buildCleanSummary(totalSuccesses, totalFailures, totalBytes, "task")
                  onOutput(summary)
                  totalFailures == 0
                } finally {
                  acquiredCleanTaskLocks.reverse.foreach(_.lock.unlock())
                }
            }
        }
    }
  }

  private def buildCleanSummary(successes: Int, failures: Int, bytes: Long, item: String): String =
    val sizeStr = DederCleaner.humanReadable(bytes)
    (successes, failures) match
      case (s, 0) if s == 0 => s"🔴 No ${item}s cleaned. $sizeStr freed"
      case (_, 0)           => s"✅ Cleaned $successes ${item}s. $sizeStr freed"
      case (0, f)           => s"🔴 All $f ${item}s failed. $sizeStr freed"
      case (s, f)           => s"✅ Cleaned $s ${item}s, $f ${item}s failed. $sizeStr freed"

  private def scheduleInactiveShutdownChecker(): Unit = {
    Thread
      .ofVirtual()
      .name("inactivity-checker")
      .start(() => {
        var running = true
        while (running && !shutdownStarted) {
          try {
            Thread.sleep(TimeUnit.MINUTES.toMillis(1))
            if (inFlightRequests.get() == 0) {
              val now = Instant.now()
              val lastEnded = lastRequestEndedAt.get()
              val inactiveDuration = Duration.between(lastEnded, now)
              if (inactiveDuration.compareTo(maxInactiveDuration) > 0) {
                logger.info(s"No requests for ${inactiveDuration.toMinutes} minutes, shutting down server.")
                shutdown()
                running = false
              }
            }
          } catch {
            case _: InterruptedException => running = false
            case NonFatal(e) =>
              logger.error(s"Error during inactivity shutdown checker: ${e.getMessage}")
          }
        }
      })
  }

  def triggerFileWatchedTasks(changedPaths: Set[os.Path]): Unit = {
    val snapshot = watchedTasksLock.synchronized { watchedTasks }
    snapshot.foreach { watchedTask =>
      logger.debug(
        s"Checking if watched task is affected: ${watchedTask.taskInstance} by ${watchedTask.affectingConfigValueTasks}"
      )
      // the watched task itself may be a config value task!
      val watchedTaskAsSourceTasks = watchedTask.taskInstance.task match {
        case _: SourceFilesTask => Set(watchedTask.taskInstance)
        case _: SourceFileTask  => Set(watchedTask.taskInstance)
        case _                  => Set.empty
      }
      val taskInstancesToCheck = watchedTaskAsSourceTasks ++ watchedTask.affectingSourceFileTasks
      val affected = taskInstancesToCheck.exists { taskInstance =>
        val sourceFiles = taskInstance.task match {
          case sourceFilesTask: SourceFilesTask =>
            executeTask(
              taskInstance.moduleId,
              sourceFilesTask,
              watchedTask.args,
              watchedTask.serverNotificationsLogger,
              useLastGood = watchedTask.useLastGood
            ).res.map(_.absPath)
          case sourceFileTask: SourceFileTask =>
            Seq(
              executeTask(
                taskInstance.moduleId,
                sourceFileTask,
                watchedTask.args,
                watchedTask.serverNotificationsLogger,
                useLastGood = watchedTask.useLastGood
              ).res.absPath
            )
          case _ => Seq.empty
        }
        changedPaths.exists { changedPath =>
          sourceFiles.exists(changedPath.startsWith)
        }
      }
      if affected then {
        val ctx = watchedTask.ctx.copy(requestId = UUID.randomUUID().toString)
        Thread
          .ofVirtual()
          .start(() =>
            try
              ox.supervised {
                RequestContext.current.supervisedWhere(ctx) {
                  watchedTask.serverNotificationsLogger.add(
                    ServerNotification.logInfo(
                      s"⌚ Executing ${watchedTask.taskInstance.id} in watch mode...",
                      watchedTask.taskInstance.moduleId
                    )
                  )
                  executeTasks(
                    ctx.requestId,
                    CallerType.Cli,
                    Seq(watchedTask.taskInstance.moduleId),
                    watchedTask.taskInstance.task.name,
                    watchedTask.args,
                    true, // tell client we are in watch mode
                    watchedTask.serverNotificationsLogger,
                    watchedTask.useLastGood
                  )
                }
              }
            catch
              case NonFatal(e) =>
                logger.warn(s"Watch rerun for ${watchedTask.taskInstance.id} failed: ${e.getMessage}")
          )
      }
    }
  }

  private def triggerConfigWatchedTasks(): Unit = {
    val snapshot = watchedTasksLock.synchronized { watchedTasks }
    snapshot.foreach { watchedTask =>
      logger.debug(
        s"Checking if watched task is affected: ${watchedTask.taskInstance} by ${watchedTask.affectingConfigValueTasks}"
      )
      // the watched task itself may be a config value task!
      val watchedTaskAffected = watchedTask.taskInstance.task match {
        case _: ConfigValueTask[?] => Set(watchedTask.taskInstance)
        case _: SourceFilesTask    => Set(watchedTask.taskInstance) // source tasks usually depend on config values
        case _: SourceFileTask     => Set(watchedTask.taskInstance)
        case _                     => Set.empty
      }
      val taskInstancesToCheck = watchedTaskAffected ++ watchedTask.affectingConfigValueTasks
      val affected = taskInstancesToCheck.exists { taskInstance =>
        val (_, changed, _) = taskInstance.task match {
          case configValueTask: ConfigValueTask[?] =>
            executeTask(
              taskInstance.moduleId,
              configValueTask,
              watchedTask.args,
              watchedTask.serverNotificationsLogger,
              useLastGood = watchedTask.useLastGood
            )
          case _ => ((), true, false) // should not happen
        }
        changed
      }
      if affected then {
        logger.debug(
          s"Config value dependencies of watched task ${watchedTask.taskInstance.id} have changed, re-executing..."
        )
        val ctx = watchedTask.ctx.copy(requestId = UUID.randomUUID().toString)
        Thread
          .ofVirtual()
          .start(() =>
            try
              ox.supervised {
                RequestContext.current.supervisedWhere(ctx) {
                  watchedTask.serverNotificationsLogger.add(
                    ServerNotification.logInfo(
                      s"⌚ Executing ${watchedTask.taskInstance.id} in watch mode...",
                      watchedTask.taskInstance.moduleId
                    )
                  )
                  executeCLI(
                    Seq(watchedTask.taskInstance.moduleId),
                    watchedTask.taskInstance.task.name,
                    watchedTask.args,
                    watchedTask.serverNotificationsLogger,
                    watchedTask.useLastGood,
                    startWatch = false,
                    exitOnEnd = false,
                    watch = true
                  )
                }
              }
            catch
              case NonFatal(e) =>
                logger.warn(s"Watch rerun for ${watchedTask.taskInstance.id} failed: ${e.getMessage}")
          )
      }
    }
  }

  def removeWatchedTasks(clientId: String): Unit = {
    logger.debug(s"Removing watched tasks for client ${clientId}")
    watchedTasksLock.synchronized {
      watchedTasks = watchedTasks.filterNot(_.ctx.clientId == clientId)
    }
  }

  def getTabCompletions(commandLine: String, cursorPos: Int): Seq[String] =
    readCurrentOrLastGood match {
      case Left(_) =>
        // State unavailable: use empty dynamic data so static completions (subcommands,
        // flags, shell types, etc.) still work
        new TabCompleter(Seq.empty, Seq.empty, Seq.empty).complete(commandLine, cursorPos)
      case Right(state) =>
        val tools = state.projectConfig.tools
        val toolNames = if tools != null then tools.keySet().asScala.toSeq else Seq.empty
        TabCompleter(state.tasksResolver, toolNames).complete(commandLine, cursorPos)
    }

  def shutdown(): Unit = {
    shutdownStarted = true
    notifyBspClientsShuttingDown()
    loadedPlugins.foreach(_.closeClassLoader())
    onShutdown()
  }

  def registerBspServer(server: ba.sake.deder.bsp.DederBspServer): Unit =
    bspServers.add(server)

  def unregisterBspServer(server: ba.sake.deder.bsp.DederBspServer): Unit =
    bspServers.remove(server)

  def notifyBspClientsShuttingDown(): Unit = {
    val snapshot = bspServers.iterator().asScala.toSeq
    if snapshot.nonEmpty then {
      logger.info(s"Notifying ${snapshot.size} BSP client(s) of impending shutdown...")
      snapshot.foreach { bspServer =>
        try bspServer.initiateShutdown()
        catch { case NonFatal(e) => logger.warn(s"Error notifying BSP client: ${e.getMessage}") }
      }
    }
  }

  /** Notify all connected BSP clients about source file changes detected by the file watcher.
    * Matches changed paths against modules' source directories and sends `buildTarget/didChange`
    * with per-module throttling (cooldown from `bspFileChangeNotifyCooldownSeconds`). */
  def notifyBspClientsOfFileChanges(changedPaths: Set[os.Path]): Unit = {
    val serversSnapshot = bspServers.iterator().asScala.toSeq
    if serversSnapshot.isEmpty then return

    val affectedModules = readState(useLastGood = true) match {
      case Right(state) =>
        val modules = state.projectConfig.modules.asScala.toSeq
        val visibleModuleIds = bsp.BspVisibleTargets.visibleModuleIds(modules)
        val projectRoot = DederGlobals.projectRootDir
        modules.filter { module =>
          if !visibleModuleIds.contains(module.id) then false
          else if module.sources.isEmpty then false
          else {
            val sourceBaseDirs = module.sources.asScala.map { src =>
              projectRoot / os.SubPath(s"${module.root}/${src}")
            }
            changedPaths.exists { changedPath =>
              sourceBaseDirs.exists(baseDir => changedPath.startsWith(baseDir))
            }
          }
        }
      case Left(_) => Seq.empty
    }

    if affectedModules.isEmpty then return

    val now = Instant.now()
    val cooldownSecs = bspFileChangeNotifyCooldownSeconds.toLong

    affectedModules.foreach { module =>
      val lastNotified = lastBspFileChangeNotify.get(module.id)
      val shouldNotify = lastNotified == null ||
        Duration.between(lastNotified, now).toSeconds >= cooldownSecs

      if shouldNotify then {
        lastBspFileChangeNotify.put(module.id, now)
        val targetId = new BuildTargetIdentifier(
          DederGlobals.projectRootDir.toURI.toString + "#" + module.id
        )
        val event = new BuildTargetEvent(targetId)
        event.setKind(BuildTargetEventKind.CHANGED)
        val params = new DidChangeBuildTarget(java.util.List.of(event))

        serversSnapshot.foreach { server =>
          try server.client.onBuildTargetDidChange(params)
          catch { case NonFatal(e) => logger.warn(s"Failed to notify BSP client about file change: ${e.getMessage}") }
        }
      }
    }
  }

  /** Notify all connected BSP clients that the project config (`deder.pkl`) has changed.
    * Sends `buildTarget/didChange` for all bsp-visible modules. No per-module throttling. */
  def notifyBspClientsOfConfigChange(): Unit = {
    val serversSnapshot = bspServers.iterator().asScala.toSeq
    if serversSnapshot.isEmpty then return

    val targetIds = readState(useLastGood = true) match {
      case Right(state) =>
        val modules = state.projectConfig.modules.asScala.toSeq
        val visibleModuleIds = bsp.BspVisibleTargets.visibleModuleIds(modules)
        modules
          .filter { m => visibleModuleIds.contains(m.id) }
          .map { module =>
            val targetId = new BuildTargetIdentifier(
              DederGlobals.projectRootDir.toURI.toString + "#" + module.id
            )
            val event = new BuildTargetEvent(targetId)
            event.setKind(BuildTargetEventKind.CHANGED)
            event
          }
      case Left(_) => Seq.empty
    }

    if targetIds.nonEmpty then {
      val params = new DidChangeBuildTarget(targetIds.asJava)
      serversSnapshot.foreach { server =>
        try server.client.onBuildTargetDidChange(params)
        catch { case NonFatal(e) => logger.warn(s"Failed to notify BSP client about config change: ${e.getMessage}") }
      }
    }
  }

  private def closeStaleClassLoaders(previous: Seq[URLClassLoader], current: Seq[URLClassLoader]): Unit =
    previous.distinct.foreach { old =>
      // Compare by reference: if the same classloader instance is still active, keep it open.
      val shouldClose = current.forall(_ ne old)
      if shouldClose then closeClassLoader(old)
    }

  private def closeClassLoader(classLoader: URLClassLoader): Unit =
    try classLoader.close()
    catch {
      case NonFatal(e) =>
        logger.warn(s"Failed to close old plugin classloader: ${e.getMessage}")
    }
}

case class DederProjectStateData(
    projectConfig: DederProject,
    tasksRegistry: TasksRegistry,
    tasksResolver: TasksResolver,
    executionPlanner: ExecutionPlanner,
    dependencyResolver: DependencyResolver
)

case class WatchedTaskData(
    ctx: RequestContext,
    taskInstance: TaskInstance,
    args: Seq[String],
    serverNotificationsLogger: ServerNotificationsLogger,
    useLastGood: Boolean,
    affectingSourceFileTasks: Set[TaskInstance],
    affectingConfigValueTasks: Set[TaskInstance]
)
