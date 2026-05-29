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
import ox.*
import ba.sake.deder.config.{ConfigParser, DederProject}
import ba.sake.deder.cli.TabCompleter
import ba.sake.deder.deps.{DependencyResolver, DependencyResolverApi}
import ba.sake.deder.plugin.{LoadedPlugin, PluginLoader, PluginLoaderApi}
import ba.sake.tupson.JsonRW
import ba.sake.tupson.toJson
import ba.sake.deder.scalajs.ScalaJsTasks
import ba.sake.deder.scalanative.ScalaNativeTasks
import ba.sake.deder.graalvm.GraalVmNativeImageTasks

class DederProjectState(
    coreTasks: CoreTasks,
    runTasks: RunTasks,
    scalaJsTasks: ScalaJsTasks,
    scalaNativeTasks: ScalaNativeTasks,
    graalvmNativeImageTasks: GraalVmNativeImageTasks,
    tasksRegistry: TasksRegistry,
    maxInactiveSeconds: Int,
    taskLockTimeoutSeconds: Int,
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
            val dependencyResolver = new DependencyResolver(assembledRepos)

            // Load plugin tasks before TasksResolver so they are included in the execution graph.
            // Plugin tasks are kept separately and the effective registry is rebuilt per reload.
            val coreTasksApi = CoreTasksApiAdapter(coreTasks, runTasks)
            val scalaJsTasksApi = ScalaJsTasksApiAdapter(scalaJsTasks)
            val scalaNativeTasksApi = ScalaNativeTasksApiAdapter(scalaNativeTasks)
            val pluginLoader = PluginLoader(coreTasksApi, scalaJsTasksApi, scalaNativeTasksApi, dependencyResolver, internals)
            loadedPlugins = pluginLoader.load(loadedPlugins, configFile, newConfig).loadedPlugins
            // TODO prepend plugin id to task name to avoid conflicts?
            val pluginTasks = loadedPlugins.flatMap(_.tasks).map(_.asInstanceOf[Task[?, ?, ?]])
            val effectiveRegistry = TasksRegistry(baseTasks ++ pluginTasks)
            val tasksResolver = TasksResolver(newConfig, effectiveRegistry)
            val executionPlanner =
              ExecutionPlanner(tasksResolver.taskInstancesGraph, tasksResolver.taskInstancesPerModule)

            val goodProjectStateData =
              DederProjectStateData(newConfig, effectiveRegistry, tasksResolver, executionPlanner, dependencyResolver)
            lastGood = Right(goodProjectStateData)
            current = Right(goodProjectStateData)
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
      watch: Boolean = false,
  ): Unit = try {
    val ctx = RequestContext.cliContext
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
        val results = executeTasks(
          ctx.requestId,
          CallerType.Cli,
          relevantModuleIds,
          taskName,
          args,
          watch = watch,
          serverNotificationsLogger,
          useLastGood = useLastGood,
        )
        // summarize across modules
        if results.nonEmpty then {
          val task = results.head.taskInstance.task
          val moduleResults = results.sortBy(_.taskInstance.moduleId).map(r => r.taskInstance.moduleId -> r.res)
          // Render cross-module summary in the chosen output format
          val summary = task.summarizeValueUnsafe(moduleResults)
          val format = ctx.outputFormat
          given JsonRW[Any] = task.summarizable.jsonRW.asInstanceOf[JsonRW[Any]]
          given PlainTextWritable[Any] = task.summarizable.plainTextW.asInstanceOf[PlainTextWritable[Any]]
          given MermaidWritable[Any] = task.summarizable.mermaidW.asInstanceOf[MermaidWritable[Any]]
          given DotWritable[Any] = task.summarizable.dotW.asInstanceOf[DotWritable[Any]]
          val output = format match
            case OutputFormat.Json =>
              summon[JsonRW[Any]].write(summary).toJson(spaces = 2, sort = true)
            case OutputFormat.DenseJson =>
              summon[JsonRW[Any]].write(summary).toJson(spaces = 0, sort = false)
            case OutputFormat.Mermaid =>
              summon[MermaidWritable[Any]].write(summary)
            case OutputFormat.Dot =>
              summon[DotWritable[Any]].write(summary)
            case _ => summon[PlainTextWritable[Any]].write(summary)
          serverNotificationsLogger.add(ServerNotification.Output(output))
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
          val allSuccessful = results.forall(r => r.taskInstance.task.isResultSuccessfulUnsafe(r.res))
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
      requestId: String = null,
      callerType: CallerType = CallerType.Cli
  ): (res: T, changed: Boolean) = {
    val span = OTEL.TRACER
      .spanBuilder(s"${moduleId}.${task.name}")
      .setAttribute("moduleId", moduleId)
      .setAttribute("taskName", task.name)
      .startSpan()
    try {
      Using.resource(span.makeCurrent()) { scope =>
        val reqId = if requestId != null then requestId else UUID.randomUUID().toString
        val res =
          executeTasks(
            reqId,
            callerType,
            Seq(moduleId),
            task.name,
            args,
            watch,
            serverNotificationsLogger,
            useLastGood
          ).head

        (res.res.asInstanceOf[T], res.changed)
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
      useLastGood: Boolean,
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
          else
            taskInstance.lock.lock()
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
        internals.recordRequestCompleted(requestId, taskName, success = true, duration, callerType)
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
        internals.recordRequestCompleted(requestId, taskName, success = false, duration, callerType)
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

  def cleanModules(moduleSelectors: Seq[String]): Boolean = {
    readCurrentOrLastGood match {
      case Left(err) =>
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
              if recommendations.isEmpty then s"No modules found for selectors: ${moduleSelectors.mkString(", ")}"
              else s"No modules found, did you mean: ${recommendations.mkString(", ")} ?"
            logger.error(msg)
            false
          case Right(moduleIds) =>
            // log what will be cleaned
            val shown = moduleIds.take(10)
            shown.foreach { moduleId =>
              val moduleOutDir = DederGlobals.projectRootDir / ".deder/out" / moduleId
              logger.info(s"Cleaning module '${moduleId}' output directory: ${moduleOutDir}")
            }
            val remaining = moduleIds.size - shown.size
            if remaining > 0 then logger.info(s"...and ${remaining} more module(s)")

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
                else
                  taskInstance.lock.lock()
                acquiredCleanLocks += taskInstance
              }
              DederCleaner.cleanModules(moduleIds)
            } finally {
              acquiredCleanLocks.reverse.foreach(_.lock.unlock())
            }
        }
    }
  }

  def cleanTasks(moduleSelectors: Seq[String], taskPattern: String): Boolean = {
    readCurrentOrLastGood match {
      case Left(err) =>
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
              if recommendations.isEmpty then s"No modules found for selectors: ${moduleSelectors.mkString(", ")}"
              else s"No modules found, did you mean: ${recommendations.mkString(", ")} ?"
            logger.error(msg)
            false
          case Right(moduleIds) =>
            state.executionPlanner.getTaskInstancesMatching(moduleIds, taskPattern) match {
              case Left(recommendations) =>
                val msg =
                  if recommendations.isEmpty then s"No '${taskPattern}' tasks found"
                  else s"No '${taskPattern}' tasks found, did you mean: ${recommendations.mkString(", ")} ?"
                logger.error(msg)
                false
              case Right(taskInstances) =>
                val shown = taskInstances.take(10)
                shown.foreach { (moduleId, taskInstance) =>
                  val taskOutDir = DederGlobals.projectRootDir / ".deder/out" / moduleId / taskInstance.task.name
                  logger.info(s"Cleaning task '${taskInstance.task.name}' on module '${moduleId}': ${taskOutDir}")
                }
                val remaining = taskInstances.size - shown.size
                if remaining > 0 then logger.info(s"...and ${remaining} more task/module combo(s)")

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
                    else
                      taskInstance.lock.lock()
                    acquiredCleanTaskLocks += taskInstance
                  }
                  taskInstances.forall { (moduleId, taskInstance) =>
                    DederCleaner.cleanTask(moduleId, taskInstance.task.name)
                  }
                } finally {
                  acquiredCleanTaskLocks.reverse.foreach(_.lock.unlock())
                }
            }
        }
    }
  }

  private def scheduleInactiveShutdownChecker(): Unit = {
    Thread.ofVirtual().name("inactivity-checker").start(() => {
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
        Thread.ofVirtual().start(() =>
          try
            supervised {
              RequestContext.clientContext.supervisedWhere(Some(ctx)) {
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
        val (_, changed) = taskInstance.task match {
          case configValueTask: ConfigValueTask[?] =>
            executeTask(
              taskInstance.moduleId,
              configValueTask,
              watchedTask.args,
              watchedTask.serverNotificationsLogger,
              useLastGood = watchedTask.useLastGood
            )
          case _ => ((), true) // should not happen
        }
        changed
      }
      if affected then {
        logger.debug(
          s"Config value dependencies of watched task ${watchedTask.taskInstance.id} have changed, re-executing..."
        )
        val ctx = watchedTask.ctx.copy(requestId = UUID.randomUUID().toString)
        Thread.ofVirtual().start(() =>
          try
            supervised {
              RequestContext.clientContext.supervisedWhere(Some(ctx)) {
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
                  watch = true,
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
      case Left(_) => Seq.empty
      case Right(state) =>
        val tabCompleter = TabCompleter(state.tasksResolver)
        tabCompleter.complete(commandLine, cursorPos)
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
    ctx: CliClientContext,
    taskInstance: TaskInstance,
    args: Seq[String],
    serverNotificationsLogger: ServerNotificationsLogger,
    useLastGood: Boolean,
    affectingSourceFileTasks: Set[TaskInstance],
    affectingConfigValueTasks: Set[TaskInstance]
)
