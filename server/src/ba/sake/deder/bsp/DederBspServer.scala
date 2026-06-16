package ba.sake.deder.bsp

import java.io.File
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import scala.util.Using
import scala.util.control.NonFatal
import scala.jdk.CollectionConverters.*
import com.typesafe.scalalogging.StrictLogging
import ch.epfl.scala.bsp4j
import ch.epfl.scala.bsp4j.*
import dependency.ScalaParameters
import coursierapi.error.DownloadingArtifactsError
import io.opentelemetry.api.trace.StatusCode as OtelStatusCode
import ba.sake.deder.{CompileResult => _, _}
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.DederModule
import ba.sake.deder.deps.Dependency
import ba.sake.deder.config.DederProject.ModuleType
import ba.sake.deder.scalajs.ScalaJsTasks
import ba.sake.deder.scalanative.ScalaNativeTasks

class DederBspServer(
    coreTasks: CoreTasks,
    runTasks: RunTasks,
    scalaJsTasks: ScalaJsTasks,
    scalaNativeTasks: ScalaNativeTasks,
    projectState: DederProjectState,
    onExit: () => Unit
) extends BuildServer,
      JvmBuildServer,
      JavaBuildServer,
      ScalaBuildServer,
      CancelExtension,
      StrictLogging {

  var client: BuildClient = compiletime.uninitialized // set by DederBspProxyServer

  var clientParams: Option[InitializeBuildParams] = None

  private val running = AtomicBoolean(true)

  // Per-module-keyed buffer for in-flight compilations.
  // Each InFlightCompilation is registered under EVERY module ID it covers,
  // so overlapping requests (including subsets like [A,B] vs [B]) are correctly buffered.
  private val inFlightCompilations = new ConcurrentHashMap[String, InFlightCompilation]

  private case class InFlightCompilation(
      modulesBeingCompiled: Set[String],
      primaryOriginId: String,
      compileFuture: CompletableFuture[CompileResult],
      pendingRequests: ConcurrentLinkedQueue[PendingRequest]
  )

  private case class PendingRequest(
      originId: String,
      resultFuture: CompletableFuture[CompileResult]
  )

  override def cancelRequest(params: CancelRequestParams): Unit = {
    val originId: String = params.getId match {
      case e if e.isLeft  => e.getLeft
      case e if e.isRight => e.getRight.toString
    }
    logger.debug(s"BSP cancel request for originId: $originId")

    // Check pending (buffered) requests across all in-flight compilations
    inFlightCompilations.values().asScala.foreach { inFlight =>
      inFlight.pendingRequests.removeIf { pr =>
        if pr.originId == originId then
          val cancelled = new CompileResult(StatusCode.CANCELLED)
          cancelled.setOriginId(originId)
          pr.resultFuture.complete(cancelled)
          logger.debug(s"Cancelled buffered compile request for originId: $originId")
          true
        else false
      }
    }

    // Also try to cancel the primary in-flight compilation
    projectState.cancelRequest(originId)
  }

  // fresh one for each BSP request!
  private def makeServerNotificationsLogger(
      originId: Option[String] = None,
      taskId: Option[TaskId] = None,
      moduleId: Option[String] = None,
      isCompileTask: Boolean = false
  ) = {
    ServerNotificationsLogger { sn =>
      sn match {
        case n: ServerNotification.Log =>
          if n.level == ServerNotification.LogLevel.ERROR then
            client.onBuildShowMessage(new ShowMessageParams(MessageType.ERROR, n.message))
        case tp: ServerNotification.TaskProgress =>
          val targetId = resolveModule(tp.moduleId).map(buildTargetId)
          val isRelevantCompileNotification = isCompileTask && moduleId.contains(tp.moduleId)
          if isRelevantCompileNotification then {
            val params = TaskProgressParams(taskId.orNull)
            params.setOriginId(originId.orNull)
            params.setEventTime(System.currentTimeMillis())
            params.setDataKind("compile-progress")
            params.setData(new CompileTask(targetId.orNull))
            params.setProgress(tp.progress)
            params.setTotal(tp.total)
            val percentage = tp.progress.toDouble / tp.total.toDouble * 100
            params.setMessage(f"${tp.moduleId}.${tp.taskName}: ${percentage}%.2f%%")
            client.onBuildTaskProgress(params)
          }
        case cd: ServerNotification.CompileDiagnostic =>
          val isRelevantCompileNotification = isCompileTask && moduleId.contains(cd.moduleId)
          if isRelevantCompileNotification then {
            if cd.problem.position.sourceFile.isPresent then {
              // Diagnostics with a source position are published in bulk by renderCompileResult()
              // at compilation end. Publishing them one-by-one here overwhelms the BSP client.
              // No action needed — the diagnostic is accumulated in the CompileResult.
            } else {
              val msgType = cd.problem.severity() match {
                case xsbti.Severity.Error => MessageType.ERROR
                case xsbti.Severity.Warn  => MessageType.WARNING
                case xsbti.Severity.Info  => MessageType.INFO
              }
              client.onBuildShowMessage(new ShowMessageParams(msgType, cd.problem.message()))
            }
          }
        case _: ServerNotification.CompileStarted  => // handled in createCompileFuture
        case _: ServerNotification.CompileFinished  => // handled in createCompileFuture
        case _: ServerNotification.CompileFailed    => // handled in createCompileFuture
        case _: ServerNotification.RequestFinished => // do nothing
        case _: ServerNotification.Output          => // do nothing
        case _: ServerNotification.RunSubprocess   => // do nothing
      }
    }
  }

  override def buildInitialize(params: InitializeBuildParams): CompletableFuture[InitializeBuildResult] =
    javaFuture("buildInitialize") {
      logger.debug(s"buildInitialize for params: ${params}")
      ensureRunning()
      clientParams = Some(params)
      val supportedLanguages = List("java", "scala")
      val capabilities = new BuildServerCapabilities()
      capabilities.setResourcesProvider(true)
      capabilities.setCompileProvider(new CompileProvider(supportedLanguages.asJava))
      capabilities.setRunProvider(new RunProvider(supportedLanguages.asJava))
      capabilities.setTestProvider(new TestProvider(supportedLanguages.asJava))
      // metals does debug stuff for us! https://github.com/scalameta/metals/issues/5928
      capabilities.setDebugProvider(new DebugProvider(Seq.empty.asJava))
      capabilities.setCanReload(true)
      capabilities.setBuildTargetChangedProvider(true)
      capabilities.setJvmCompileClasspathProvider(true)
      capabilities.setJvmRunEnvironmentProvider(true)
      capabilities.setJvmTestEnvironmentProvider(true)
      capabilities.setOutputPathsProvider(true)
      capabilities.setDependencySourcesProvider(true)
      capabilities.setDependencyModulesProvider(true)
      capabilities.setInverseSourcesProvider(true)
      val result = new InitializeBuildResult("deder-bsp", DederGlobals.version, "2.2.0-M2", capabilities)
      logger.debug(s"buildInitialize for params: ${params} return: ${result}")
      result
    }

  override def onBuildInitialized(): Unit = traced("onBuildInitialized") {
    logger.debug(s"onBuildInitialized")
    ensureRunning()
    // we dont trigger compile immediately
    // coz there is no progress seen in metals.. just "importing"
  }

  override def workspaceReload(): CompletableFuture[Object] = javaFuture("workspaceReload") {
    logger.debug(s"workspaceReload")
    ensureRunning()
    // auto reloads on file changes, no action needed here
    ().asInstanceOf[Object]
  }

  override def workspaceBuildTargets(): CompletableFuture[WorkspaceBuildTargetsResult] =
    javaFuture("workspaceBuildTargets") {
      logger.debug("workspaceBuildTargets called")
      ensureRunning()
      projectState.reloadProject()
      // Report current config errors even when lastGood provides fallback targets
      projectState.readState(useLastGood = false).left.foreach { errorMessage =>
        client.onBuildShowMessage(
          new ShowMessageParams(MessageType.ERROR, s"Failed to load project config: ${errorMessage}")
        )
      }
      val buildTargets = projectState.readState(useLastGood = true) match {
        case Left(errorMessage) =>
          client.onBuildShowMessage(
            new ShowMessageParams(MessageType.ERROR, s"Failed to load project state: ${errorMessage}")
          )
          List.empty
        case Right(projectStateData) =>
          val visibleModuleIds = BspVisibleTargets.visibleModuleIds(projectStateData.projectConfig.modules.asScala.toSeq)
          projectStateData.projectConfig.modules.asScala
            .filter(module => visibleModuleIds.contains(module.id))
            .sortBy(m => (if m.id.contains("-jvm") then 0 else 1, m.id))
            .map(m => buildTarget(m, projectStateData, visibleModuleIds))
            .toList
      }
      val result = new WorkspaceBuildTargetsResult(buildTargets.asJava)
      logger.debug(s"workspaceBuildTargets return: ${result}")
      result
    }

  override def buildTargetSources(params: SourcesParams): CompletableFuture[SourcesResult] =
    javaFuture("buildTargetSources") {
      logger.debug(s"buildTargetSources for params: ${params}")
      ensureRunning()
      val sourcesItems = withLastGoodState(_ => List.empty) { projectStateData =>
        resolveVisibleTargets(projectStateData, params.getTargets.asScala.toSeq).map { case (targetId, module) =>
          val moduleId = module.id
          val serverNotificationsLogger = makeServerNotificationsLogger()
          val sourceDirs = tryExecuteTask(serverNotificationsLogger, moduleId, coreTasks.sourcesTask)(Seq.empty)
          val sourceItems = sourceDirs.flatMap { srcDir =>
            val srcDirPath = srcDir.absPath
            val subPaths =
              if os.exists(srcDirPath) then
                os.walk(srcDirPath).map { srcFile =>
                  new SourceItem(
                    srcFile.toNIO.toUri.toString,
                    if (os.isDir(srcFile)) SourceItemKind.DIRECTORY else SourceItemKind.FILE,
                    false // generated
                  )
                }
              else List.empty
            List(new SourceItem(srcDirPath.toNIO.toUri.toString, SourceItemKind.DIRECTORY, false)) ++ subPaths
          }
          val sourcesItem = SourcesItem(targetId, sourceItems.asJava)
          sourcesItem.setRoots(sourceDirs.map(_.absPath.toNIO.toUri.toString).asJava)
          sourcesItem
        }
      }
      val result = new SourcesResult(sourcesItems.asJava)
      logger.debug(s"buildTargetSources for params: ${params} return: ${result}")
      result
    }

  override def buildTargetInverseSources(params: InverseSourcesParams): CompletableFuture[InverseSourcesResult] =
    javaFuture("buildTargetInverseSources") {
      logger.debug(s"buildTargetInverseSources for params: ${params}")
      ensureRunning()
      val serverNotificationsLogger = makeServerNotificationsLogger()
      val targetIds = withLastGoodState(_ => List.empty) { projectStateData =>
        val visibleIds = visibleModuleIds(projectStateData)
        val modules = projectStateData.tasksResolver.allModules.filter { m =>
          visibleIds.contains(m.id) && {
            val sourceDirs = tryExecuteTask(serverNotificationsLogger, m.id, coreTasks.sourcesTask)(Seq.empty)
            sourceDirs.exists { srcDir =>
              val srcDirUri = srcDir.absPath.toURI.toString
              params.getTextDocument.getUri.startsWith(srcDirUri)
            }
          }
        }
        modules.sortBy(m => if m.id.contains("-jvm") then 0 else 1).map(buildTargetId)
      }
      val result = InverseSourcesResult(targetIds.asJava)
      logger.debug(s"buildTargetInverseSources for params: ${params} return: ${result}")
      result
    }

  override def buildTargetResources(params: ResourcesParams): CompletableFuture[ResourcesResult] =
    javaFuture("buildTargetResources") {
      logger.debug(s"buildTargetResources for params: ${params}")
      ensureRunning()
      val resourcesItems = withLastGoodState(_ => List.empty) { projectStateData =>
        val serverNotificationsLogger = makeServerNotificationsLogger()
        resolveVisibleTargets(projectStateData, params.getTargets.asScala.toSeq).flatMap { case (targetId, module) =>
          val moduleId = module.id
          val resourceDirs = tryExecuteTask(serverNotificationsLogger, moduleId, coreTasks.resourcesTask)(Seq.empty)
          resourceDirs.map { resourceDir =>
            val resourceDirPath = resourceDir.absPath
            val resourceItems =
              if os.exists(resourceDirPath) then
                os.walk(resourceDirPath).map { resourceFile =>
                  resourceFile.toNIO.toUri.toString
                }
              else List.empty
            ResourcesItem(targetId, resourceItems.asJava)
          }
        }
      }
      val result = new ResourcesResult(resourcesItems.asJava)
      logger.debug(s"buildTargetResources for params: ${params} return: ${result}")
      result
    }

  private def createCompileFuture(params: CompileParams, requestedModules: Set[String]): CompletableFuture[CompileResult] =
    javaFuture("buildTargetCompile", Option(params.getOriginId)) {
      logger.debug(s"buildTargetCompile for params: ${params}")
      ensureRunning()
      val taskId = TaskId(s"compile-${UUID.randomUUID}")
      val taskStartParams = TaskStartParams(taskId)
      taskStartParams.setEventTime(System.currentTimeMillis())
      taskStartParams.setOriginId(params.getOriginId)
      taskStartParams.setMessage(s"Compiling modules: ${requestedModules.mkString(", ")}")
      client.onBuildTaskStart(taskStartParams)
      var allCompileSucceeded = true
      withLastGoodState(_ => allCompileSucceeded = false) { projectStateData =>
        resolveVisibleTargets(projectStateData, params.getTargets.asScala.toSeq).foreach { case (_, module) =>
          val moduleId = module.id
          val subtaskId = TaskId(s"compile-${moduleId}-${UUID.randomUUID}")
          subtaskId.setParents(List(taskId.getId).asJava)
          logger.debug(s"buildTargetCompile subtaskId ${subtaskId}")
          val serverNotificationsLogger = makeServerNotificationsLogger(
            originId = Option(params.getOriginId),
            taskId = Some(subtaskId),
            moduleId = Some(moduleId),
            isCompileTask = true
          )
          val targetIdForRender = resolveModule(moduleId).map(buildTargetId).orNull

          // Always send task start (previously split across cache-hit synthesis and CompileStarted notif)
          if targetIdForRender != null then {
            val startP = TaskStartParams(subtaskId)
            startP.setEventTime(System.currentTimeMillis())
            startP.setOriginId(params.getOriginId)
            startP.setMessage(s"Compiling ${moduleId} ...")
            startP.setDataKind(TaskStartDataKind.COMPILE_TASK)
            startP.setData(new CompileTask(targetIdForRender))
            client.onBuildTaskStart(startP)
          }

          val compileResult = try executeCompileTask(serverNotificationsLogger, moduleId, params.getOriginId)
            catch {
              case _: TaskEvaluationException =>
                val classesDir = DederPath(DederGlobals.classesDir(moduleId))
                (ba.sake.deder.CompileResult(classesDir, errors = 1, warnings = 0, sourceCount = 0), false)
            }
          val (cr, _) = compileResult // fromCache boolean not needed — all paths unified

          // Always publish diagnostics (all at once, per-file, reset=true)
          if targetIdForRender != null then
            renderCompileResult(cr, targetIdForRender)

          // Always send task finish (previously split across cache-hit synthesis and CompileFinished/CompileFailed notifs)
          if targetIdForRender != null then {
            val cStatus = if cr.errors == 0 then StatusCode.OK else StatusCode.ERROR
            val finishP = TaskFinishParams(subtaskId, cStatus)
            finishP.setEventTime(System.currentTimeMillis())
            finishP.setOriginId(params.getOriginId)
            finishP.setMessage(s"Finished compiling ${moduleId}")
            finishP.setDataKind(TaskFinishDataKind.COMPILE_REPORT)
            finishP.setData(new CompileReport(targetIdForRender, cr.errors, cr.warnings))
            client.onBuildTaskFinish(finishP)
          }

          if cr.errors > 0 then allCompileSucceeded = false
        }
      }
      val status = if allCompileSucceeded then StatusCode.OK else StatusCode.ERROR
      val taskFinishParams = TaskFinishParams(taskId, status)
      taskFinishParams.setEventTime(System.currentTimeMillis())
      taskFinishParams.setOriginId(params.getOriginId)
      taskFinishParams.setMessage(s"Finished compiling modules: ${requestedModules.mkString(", ")}")
      client.onBuildTaskFinish(taskFinishParams)
      val result = new CompileResult(status)
      result.setOriginId(params.getOriginId)
      logger.debug(s"buildTargetCompile for params ${params} return: ${result}")
      result
    }

  private def registerFanOut(
      compileFuture: CompletableFuture[CompileResult],
      inFlight: InFlightCompilation
  ): Unit =
    compileFuture.whenComplete { (result, ex) =>
      val pending = inFlight.pendingRequests.asScala.toSeq
      inFlight.pendingRequests.clear()
      pending.foreach { pr =>
        if ex != null then pr.resultFuture.completeExceptionally(ex)
        else {
          val copy = new CompileResult(result.getStatusCode)
          copy.setOriginId(pr.originId)
          pr.resultFuture.complete(copy)
        }
      }
      inFlight.modulesBeingCompiled.foreach { modId =>
        inFlightCompilations.remove(modId, inFlight)
      }
    }

  override def buildTargetCompile(params: CompileParams): CompletableFuture[CompileResult] = {
    if params.getTargets.isEmpty then {
      val future = javaFuture("buildTargetCompile", Option(params.getOriginId)) {
        logger.debug(s"buildTargetCompile for params: ${params}")
        ensureRunning()
        val compileResult = new CompileResult(StatusCode.OK)
        compileResult.setOriginId(params.getOriginId)
        compileResult
      }
      return future
    }

    val requestedModules = params.getTargets.asScala.map(_.moduleId).toSet

    // Check if any requested module has an in-flight compilation
    val inFlightEntries: Set[InFlightCompilation] = requestedModules.flatMap { modId =>
      Option(inFlightCompilations.get(modId))
    }

    // Remove stale entries (compileFuture already done)
    inFlightEntries.filter(_.compileFuture.isDone).foreach { stale =>
      stale.modulesBeingCompiled.foreach { modId =>
        inFlightCompilations.remove(modId, stale)
      }
    }
    val activeEntries = inFlightEntries.filter(!_.compileFuture.isDone)

    if activeEntries.isEmpty then {
      // Scenario A: No overlap — start a new compilation
      val compileFuture = createCompileFuture(params, requestedModules)
      val inFlight = InFlightCompilation(
        requestedModules,
        params.getOriginId,
        compileFuture,
        new ConcurrentLinkedQueue[PendingRequest]()
      )
      requestedModules.foreach { modId =>
        inFlightCompilations.put(modId, inFlight)
      }
      registerFanOut(compileFuture, inFlight)
      compileFuture
    } else if requestedModules.subsetOf(activeEntries.flatMap(_.modulesBeingCompiled)) then {
      // Scenario B: All requested modules are covered by in-flight compilations — buffer this request
      // All in-flight entries covering these modules point to the same InFlightCompilation object
      val inFlight = activeEntries.head
      val pendingFuture = new CompletableFuture[CompileResult]()
      inFlight.pendingRequests.add(PendingRequest(params.getOriginId, pendingFuture))
      pendingFuture
    } else {
      // Scenario B-prime: Partial overlap — proceed normally (will block on TaskInstance locks)
      val compileFuture = createCompileFuture(params, requestedModules)
      val inFlight = InFlightCompilation(
        requestedModules,
        params.getOriginId,
        compileFuture,
        new ConcurrentLinkedQueue[PendingRequest]()
      )
      requestedModules.foreach { modId =>
        inFlightCompilations.put(modId, inFlight)
      }
      registerFanOut(compileFuture, inFlight)
      compileFuture
    }
  }

  override def buildTargetCleanCache(params: CleanCacheParams): CompletableFuture[CleanCacheResult] =
    javaFuture("buildTargetCleanCache") {
      logger.debug(s"buildTargetCleanCache for params: ${params}")
      ensureRunning()
      withLastGoodState(_ => CleanCacheResult(false)) { projectStateData =>
        val cleaned = resolveVisibleTargets(projectStateData, params.getTargets.asScala.toSeq).forall { case (_, module) =>
          val moduleId = module.id
          projectState.cleanModules(Seq(moduleId), _ => ())
        }
        val result = CleanCacheResult(cleaned)
        logger.debug(s"buildTargetCleanCache for params ${params} return: ${result}")
        result
      }
    }

  // list of dependencies(maven)
  override def buildTargetDependencyModules(
      params: DependencyModulesParams
  ): CompletableFuture[DependencyModulesResult] = javaFuture("buildTargetDependencyModules") {
    logger.debug(s"buildTargetDependencyModules for params: ${params}")
    ensureRunning()
    val items = withLastGoodState(_ => List.empty) { projectStateData =>
      val serverNotificationsLogger = makeServerNotificationsLogger()
      resolveVisibleTargets(projectStateData, params.getTargets.asScala.toSeq).map { case (targetId, module) =>
        val moduleId = module.id
        try {
          val dependencies = tryExecuteTask(serverNotificationsLogger, moduleId, coreTasks.dependenciesTask)(Seq.empty)
          val fetchRes = projectStateData.dependencyResolver.fetch(dependencies)
          // assuming that dependencies and artifacts are in the same order, 1:1 mapping
          val depsWithArtifacts = fetchRes.getDependencies.asScala
            .zip(fetchRes.getArtifacts.asScala)
            .map { case (dep, entry) => (dep, entry.getKey, entry.getValue) }
          val depItems = depsWithArtifacts.map { case (dep, artifact, file) =>
            val mavenDependencyModuleArtifact = MavenDependencyModuleArtifact(file.toURI.toString)
            if dep.getPublication != null then
              mavenDependencyModuleArtifact.setClassifier(dep.getPublication.getClassifier)
            val mavenDependencyModule = MavenDependencyModule(
              dep.getModule.getOrganization,
              dep.getModule.getName,
              dep.getVersion,
              List(mavenDependencyModuleArtifact).asJava
            )
            val depModule = DependencyModule(dep.getModule.getName, dep.getVersion)
            depModule.setDataKind(DependencyModuleDataKind.MAVEN)
            depModule.setData(mavenDependencyModule)
            depModule
          }
          DependencyModulesItem(targetId, depItems.asJava)
        } catch {
          case NonFatal(e) =>
            logger.error(s"buildTargetDependencyModules failed for module $moduleId", e)
            DependencyModulesItem(targetId, List.empty.asJava)
        }
      }
    }
    val result = DependencyModulesResult(items.asJava)
    logger.debug(s"buildTargetDependencyModules for params ${params} return: ${result}")
    result
  }

  // source jars of dependencies
  override def buildTargetDependencySources(
      params: DependencySourcesParams
  ): CompletableFuture[DependencySourcesResult] = javaFuture("buildTargetDependencySources") {
    logger.debug(s"buildTargetDependencySources for params ${params}")
    ensureRunning()
    val items = withLastGoodState(_ => List.empty) { projectStateData =>
      val serverNotificationsLogger = makeServerNotificationsLogger()
      resolveVisibleTargets(projectStateData, params.getTargets.asScala.toSeq).map { case (targetId, module) =>
        val moduleId = module.id
        val sourceArtifactFiles =
          tryExecuteTask(serverNotificationsLogger, moduleId, coreTasks.depSourcesTask)(Seq.empty)
            .map(_.toNIO.toUri.toString)
        DependencySourcesItem(targetId, sourceArtifactFiles.asJava)
      }
    }
    val result = DependencySourcesResult(items.asJava)
    logger.debug(s"buildTargetDependencySources for params ${params} return: ${result}")
    result
  }

  // hidden/ignored/output dirs
  override def buildTargetOutputPaths(params: OutputPathsParams): CompletableFuture[OutputPathsResult] =
    javaFuture("buildTargetOutputPaths") {
      logger.debug(s"buildTargetOutputPaths for params ${params}")
      ensureRunning()
      val modulesItems = withLastGoodState(_ => Seq.empty) { projectStateData =>
        val excludedDirNames = FileWatchUtils.bspExcludedDirNames
        val outputPathsItems =
          for dirName <- excludedDirNames
          yield OutputPathItem(DederPath(dirName).absPath.toNIO.toUri.toString, OutputPathItemKind.DIRECTORY)
        for (targetId, _) <- resolveVisibleTargets(projectStateData, params.getTargets.asScala.toSeq)
        yield OutputPathsItem(targetId, outputPathsItems.asJava)
      }
      val result = new OutputPathsResult(modulesItems.asJava)
      logger.debug(s"buildTargetOutputPaths for params ${params} return: ${result}")
      result
    }

  override def buildTargetJavacOptions(params: JavacOptionsParams): CompletableFuture[JavacOptionsResult] =
    javaFuture("buildTargetJavacOptions") {
      logger.debug(s"buildTargetJavacOptions for params ${params}")
      ensureRunning()
      val javacOptionsItems = withLastGoodState(_ => List.empty) { projectStateData =>
        val serverNotificationsLogger = makeServerNotificationsLogger()
        resolveVisibleTargets(projectStateData, params.getTargets.asScala.toSeq).flatMap { case (targetId, module) =>
          val moduleId = module.id
          val classesDir = DederGlobals.classesDir(moduleId).toNIO.toUri.toString
          val semanticdbDir = DederGlobals.semanticdbDir(moduleId)
          val javacOptions = tryExecuteTask(serverNotificationsLogger, moduleId, coreTasks.javacOptionsTask)(Seq.empty)
          val javacAnnotationProcessors =
            tryExecuteTask(serverNotificationsLogger, moduleId, coreTasks.javacAnnotationProcessorsTask)(Seq.empty)
          val finalJavacOptions = javacOptions ++
            Seq(
              "-processorpath",
              javacAnnotationProcessors.map(_.toString).mkString(File.pathSeparator),
              s"-Xplugin:semanticdb -sourceroot:${DederGlobals.projectRootDir} -targetroot:${semanticdbDir} -build-tool:sbt"
            )
          val compileClasspath =
            tryExecuteTask(serverNotificationsLogger, moduleId, coreTasks.compileClasspathTask)(Seq.empty)
              .map(_.toNIO.toUri.toString)
              .toList
          // logger.debug(s"compileClasspath for ${moduleId} : ${compileClasspath}")
          val javacOptionsItem =
            new JavacOptionsItem(targetId, finalJavacOptions.asJava, compileClasspath.asJava, classesDir)
          List(javacOptionsItem)
        }
      }
      val result = JavacOptionsResult(javacOptionsItems.asJava)
      logger.debug(s"buildTargetJavacOptions for params ${params} return: ${result}")
      result
    }

  override def buildTargetScalaMainClasses(
      params: ScalaMainClassesParams
  ): CompletableFuture[ScalaMainClassesResult] = javaFuture("buildTargetScalaMainClasses", Option(params.getOriginId)) {
    logger.debug(s"buildTargetScalaMainClasses for params ${params}")
    ensureRunning()
    val items = withLastGoodState(_ => List.empty) { projectStateData =>
      val serverNotificationsLogger = makeServerNotificationsLogger()
      resolveVisibleTargets(projectStateData, params.getTargets.asScala.toSeq).map { case (targetId, module) =>
        val moduleId = module.id
        val jvmOptions = tryExecuteTask(serverNotificationsLogger, moduleId, coreTasks.jvmOptionsTask)(Seq.empty)
        val items =
          tryExecuteTask(serverNotificationsLogger, moduleId, coreTasks.finalMainClassTask)(None).map { mainClass =>
            ScalaMainClass(mainClass, List.empty.asJava, jvmOptions.asJava)
          }.toList
        ScalaMainClassesItem(targetId, items.asJava)
      }
    }
    val result = ScalaMainClassesResult(items.asJava)
    result.setOriginId(params.getOriginId)
    logger.debug(s"buildTargetScalaMainClasses for params ${params} return: ${result}")
    result
  }

  override def buildTargetScalaTestClasses(
      params: ScalaTestClassesParams
  ): CompletableFuture[ScalaTestClassesResult] = javaFuture("buildTargetScalaTestClasses", Option(params.getOriginId)) {
    logger.debug(s"buildTargetScalaTestClasses for params ${params}")
    ensureRunning()
    val items = withLastGoodState(_ => List.empty) { projectStateData =>
      val visibleTargets = resolveVisibleTargets(projectStateData, params.getTargets.asScala.toSeq)
      val testModuleIds = projectStateData.projectConfig.modules.asScala.collect {
        case m: DederProject.ScalaTestModule => m.id
        case m: DederProject.JavaTestModule  => m.id
      }
      val serverNotificationsLogger = makeServerNotificationsLogger()
      visibleTargets.filter((_, module) => testModuleIds.contains(module.id)).flatMap { case (targetId, module) =>
        val moduleId = module.id
        try {
          val frameworkTests = tryExecuteTask(serverNotificationsLogger, moduleId, coreTasks.testClassesTask)(Seq.empty)
          frameworkTests.map { ft =>
            val item = ScalaTestClassesItem(targetId, ft.testClasses.map(_.className).asJava)
            item.setFramework(ft.frameworkName)
            item
          }
        } catch {
          case e: TaskEvaluationException =>
            // module failed to compile for example
            List.empty
        }
      }
    }
    val result = ScalaTestClassesResult(items.asJava)
    logger.debug(s"buildTargetScalaTestClasses for params ${params} return: ${result}")
    result
  }

  override def buildTargetScalacOptions(params: ScalacOptionsParams): CompletableFuture[ScalacOptionsResult] =
    javaFuture("buildTargetScalacOptions") {
      logger.debug(s"buildTargetScalacOptions for params ${params}")
      ensureRunning()
      val scalacOptionsItems = withLastGoodState(_ => List.empty) { projectStateData =>
        val serverNotificationsLogger = makeServerNotificationsLogger()
        resolveVisibleTargets(projectStateData, params.getTargets.asScala.toSeq).flatMap { case (targetId, module) =>
          val moduleId = module.id
          val scalaVersion = executeTask(serverNotificationsLogger, moduleId, coreTasks.scalaVersionTask)
          val scalacOptions =
            tryExecuteTask(serverNotificationsLogger, moduleId, coreTasks.scalacOptionsTask)(Seq.empty)
          val semanticdbDir = DederGlobals.semanticdbDir(moduleId)
          val scalacPlugins =
            tryExecuteTask(serverNotificationsLogger, moduleId, coreTasks.scalacPluginsTask)(Seq.empty)
          val semanticdbOptions =
            if scalaVersion.startsWith("3.") then
              scalacPlugins.map(p => s"-Xplugin:${p.toString}") ++
                Seq(
                  "-Xsemanticdb",
                  "-sourceroot",
                  DederGlobals.projectRootDir.toString,
                  "-semanticdb-target",
                  semanticdbDir.toString
                )
            else
              val scalaSemanticdbVersion =
                executeTask(serverNotificationsLogger, moduleId, coreTasks.scalaSemanticdbVersionTask)
              val semanticdbScalacJar = projectStateData.dependencyResolver.fetchFiles(
                Seq(Dependency.make(s"org.scalameta:::semanticdb-scalac:${scalaSemanticdbVersion}", scalaVersion))
              )
              val allScalacPlugins = scalacPlugins ++ semanticdbScalacJar
              Seq(
                "-Yrangepos",
                s"-P:semanticdb:sourceroot:${DederGlobals.projectRootDir}",
                s"-P:semanticdb:targetroot:${semanticdbDir}"
              ) ++
                allScalacPlugins.map(p => s"-Xplugin:${p.toString}")
          val finalScalacOptions = scalacOptions ++ semanticdbOptions
          val compileClasspath =
            tryExecuteTask(serverNotificationsLogger, moduleId, coreTasks.compileClasspathTask)(Seq.empty)
              .map(_.toNIO.toUri.toString)
              .toList
          val classesDir = DederGlobals.classesDir(moduleId).toNIO.toUri.toString
          val scalacOptionsItem =
            ScalacOptionsItem(targetId, finalScalacOptions.asJava, compileClasspath.asJava, classesDir)
          List(scalacOptionsItem)
        }
      }
      val result = ScalacOptionsResult(scalacOptionsItems.asJava)
      logger.debug(s"buildTargetScalacOptions for params ${params} return: ${result}")
      result
    }

  override def buildTargetJvmCompileClasspath(
      params: JvmCompileClasspathParams
  ): CompletableFuture[JvmCompileClasspathResult] = javaFuture("buildTargetJvmCompileClasspath") {
    logger.debug(s"buildTargetJvmCompileClasspath for params ${params}")
    ensureRunning()
    val items = withLastGoodState(_ => List.empty) { projectStateData =>
      val serverNotificationsLogger = makeServerNotificationsLogger()
      resolveVisibleTargets(projectStateData, params.getTargets.asScala.toSeq).map { case (targetId, module) =>
        val moduleId = module.id
        val compileClasspath =
          tryExecuteTask(serverNotificationsLogger, moduleId, coreTasks.compileClasspathTask)(Seq.empty)
            .map(_.toNIO.toUri.toString)
            .toList
        JvmCompileClasspathItem(targetId, compileClasspath.asJava)
      }
    }
    val result = JvmCompileClasspathResult(items.asJava)
    logger.debug(s"buildTargetJvmCompileClasspath for params ${params} return: ${result}")
    result
  }

  override def buildTargetJvmRunEnvironment(
      params: JvmRunEnvironmentParams
  ): CompletableFuture[JvmRunEnvironmentResult] =
    javaFuture("buildTargetJvmRunEnvironment", Option(params.getOriginId)) {
      logger.debug(s"buildTargetJvmRunEnvironment for params ${params}")
      ensureRunning()
      val items = withLastGoodState(_ => List.empty) { projectStateData =>
        val serverNotificationsLogger = makeServerNotificationsLogger()
        resolveVisibleTargets(projectStateData, params.getTargets.asScala.toSeq).map { case (targetId, module) =>
          val moduleId = module.id
          val mainClasses = tryExecuteTask(serverNotificationsLogger, moduleId, coreTasks.mainClassesTask)(Seq.empty)
          val classpath =
            tryExecuteTask(serverNotificationsLogger, moduleId, coreTasks.runClasspathTask)(Seq.empty)
              .map(_.toNIO.toUri.toString)
              .toList
          val jvmOptions = tryExecuteTask(serverNotificationsLogger, moduleId, coreTasks.jvmOptionsTask)(Seq.empty)
          val workingDirectory = DederGlobals.projectRootDir.toNIO.toUri.toString
          val environmentVariables = Map.empty[String, String] // TODO: Get environment variables
          val item = JvmEnvironmentItem(
            targetId,
            classpath.asJava,
            jvmOptions.asJava,
            workingDirectory,
            environmentVariables.asJava
          )
          val mainClassItems = mainClasses.map { mainClass =>
            val args = List.empty[String] // TODO
            JvmMainClass(mainClass, args.asJava)
          }
          item.setMainClasses(mainClassItems.asJava)
          item

        }
      }
      val result = JvmRunEnvironmentResult(items.asJava)
      logger.debug(s"buildTargetJvmRunEnvironment for params ${params} return: ${result}")
      result
    }

  override def buildTargetJvmTestEnvironment(
      params: JvmTestEnvironmentParams
  ): CompletableFuture[JvmTestEnvironmentResult] =
    javaFuture("buildTargetJvmTestEnvironment", Option(params.getOriginId)) {
      logger.debug(s"buildTargetJvmTestEnvironment for params ${params}")
      ensureRunning()
      val items = withLastGoodState(_ => List.empty) { projectStateData =>
        val serverNotificationsLogger = makeServerNotificationsLogger()
        resolveVisibleTargets(projectStateData, params.getTargets.asScala.toSeq).map { case (targetId, module) =>
          val moduleId = module.id
          val testClasses =
            tryExecuteTask(serverNotificationsLogger, moduleId, coreTasks.testClassesTask)(Seq.empty)
          val classpath =
            tryExecuteTask(serverNotificationsLogger, moduleId, coreTasks.runClasspathTask)(Seq.empty)
              .map(_.toNIO.toUri.toString)
              .toList
          val jvmOptions = tryExecuteTask(serverNotificationsLogger, moduleId, coreTasks.jvmOptionsTask)(Seq.empty)
          val workingDirectory = DederGlobals.projectRootDir.toNIO.toUri.toString
          val environmentVariables = Map.empty[String, String] // TODO: Get environment variables
          val item = JvmEnvironmentItem(
            targetId,
            classpath.asJava,
            jvmOptions.asJava,
            workingDirectory,
            environmentVariables.asJava
          )
          val testClassItems = testClasses.flatMap { ft =>
            ft.testClasses.map { test =>
              val args = List.empty[String]
              JvmMainClass(test.className, args.asJava)
            }
          }
          item.setMainClasses(testClassItems.asJava)
          item
        }
      }
      val result = JvmTestEnvironmentResult(items.asJava)
      logger.debug(s"buildTargetJvmTestEnvironment for params ${params} return: ${result}")
      result
    }

  override def buildTargetRun(params: RunParams): CompletableFuture[RunResult] =
    javaFuture("buildTargetRun", Option(params.getOriginId)) {
      logger.debug(s"buildTargetRun for params ${params}")
      ensureRunning()
      val taskId = TaskId(s"run-${UUID.randomUUID}")
      val taskStartParams = TaskStartParams(taskId)
      taskStartParams.setEventTime(System.currentTimeMillis())
      taskStartParams.setOriginId(params.getOriginId)
      taskStartParams.setMessage(s"Running ${params.getTarget.moduleId}")
      client.onBuildTaskStart(taskStartParams)
      var runSucceeded = true
      val result = withLastGoodState(_ => { runSucceeded = false; RunResult(StatusCode.ERROR) }) { projectStateData =>
        val moduleId = resolveVisibleTarget(projectStateData, params.getTarget).id
        val serverNotificationsLogger = makeServerNotificationsLogger(
          originId = Option(params.getOriginId),
          taskId = Some(taskId),
          moduleId = Some(moduleId),
          isCompileTask = true
        )
        executeTask(serverNotificationsLogger, moduleId, coreTasks.finalMainClassTask, originId = params.getOriginId) match {
          case Some(mainClass) =>
            val args = Option(params.getArguments).map(_.asScala.toSeq).getOrElse(Seq.empty)
            val runCmd =
              tryExecuteTask(serverNotificationsLogger, moduleId, runTasks.runTask, args = args)(Seq.empty)
            if runCmd.isEmpty then {
              logger.error(s"Failed to run module ${moduleId} via BSP")
              runSucceeded = false
              RunResult(StatusCode.ERROR)
            } else {
              val wd = Option(params.getWorkingDirectory)
                .map(uri => os.Path(java.nio.file.Path.of(new java.net.URI(uri))))
                .getOrElse(os.pwd)
              val runRes =
                os.proc(runCmd).call(cwd = wd, stdin = os.Pipe, stdout = os.Pipe, stderr = os.Pipe, check = false)
              val status = if runRes.exitCode == 0 then StatusCode.OK else StatusCode.ERROR
              if status != StatusCode.OK then runSucceeded = false
              RunResult(status)
            }
          case None =>
            runSucceeded = false
            throw DederException(s"Module ${moduleId} does not have a main class to run")
        }
      }
      result.setOriginId(params.getOriginId)
      val runStatus = if runSucceeded then StatusCode.OK else StatusCode.ERROR
      val taskFinishParams = TaskFinishParams(taskId, runStatus)
      taskFinishParams.setEventTime(System.currentTimeMillis())
      taskFinishParams.setOriginId(params.getOriginId)
      taskFinishParams.setMessage(s"Finished running ${moduleId}")
      client.onBuildTaskFinish(taskFinishParams)
      logger.debug(s"buildTargetRun for params ${params} return: ${result}")
      result
    }

  override def buildTargetTest(params: TestParams): CompletableFuture[TestResult] =
    javaFuture("buildTargetTest", Option(params.getOriginId)) {
      logger.debug(s"buildTargetTest for params ${params}")
      ensureRunning()
      val targets = params.getTargets.asScala
      val taskId = TaskId(s"test-${UUID.randomUUID}")
      val taskStartParams = TaskStartParams(taskId)
      taskStartParams.setEventTime(System.currentTimeMillis())
      taskStartParams.setOriginId(params.getOriginId)
      taskStartParams.setMessage(s"Testing modules: ${targets.map(_.moduleId).mkString(", ")}")
      client.onBuildTaskStart(taskStartParams)
      var allTestsSucceeded = true
      withLastGoodState(_ => allTestsSucceeded = false) { projectStateData =>
        val resolvedTargets = resolveVisibleTargets(projectStateData, targets.toSeq)
        val untestableTargets = resolvedTargets.filterNot { case (_, module) =>
          isTestModule(module)
        }
        if untestableTargets.nonEmpty then
          throw DederException(
            s"Targets are not testable: ${untestableTargets.map((targetId, _) => targetId.moduleId).mkString(", ")}"
          )
        resolvedTargets.foreach { case (_, module) =>
          val moduleId = module.id
          try {
            val module = projectStateData.tasksResolver.modulesMap(moduleId)
            val testTask = module.`type` match {
              case ModuleType.SCALA_JS_TEST     => scalaJsTasks.testJsTask
              case ModuleType.SCALA_NATIVE_TEST => scalaNativeTasks.testNativeTask
              case _                            => coreTasks.testTask
            }
            val subtaskId = TaskId(s"test-${moduleId}-${UUID.randomUUID}")
            subtaskId.setParents(List(taskId.getId).asJava)
            val serverNotificationsLogger =
              makeServerNotificationsLogger(
                originId = Option(params.getOriginId),
                taskId = Some(subtaskId),
                moduleId = Some(moduleId),
                isCompileTask = true
              )
            val testRes = executeTask(serverNotificationsLogger, moduleId, testTask, originId = params.getOriginId)
            if !testRes.success then allTestsSucceeded = false
          } catch case e: TaskEvaluationException => allTestsSucceeded = false
        }
      }
      val status = if allTestsSucceeded then StatusCode.OK else StatusCode.ERROR
      val taskFinishParams = TaskFinishParams(taskId, status)
      taskFinishParams.setEventTime(System.currentTimeMillis())
      taskFinishParams.setOriginId(params.getOriginId)
      taskFinishParams.setMessage(s"Finished testing modules: ${targets.map(_.moduleId).mkString(", ")}")
      client.onBuildTaskFinish(taskFinishParams)
      val result = TestResult(status)
      result.setOriginId(params.getOriginId)
      logger.debug(s"buildTargetTest for params ${params} return: ${result}")
      result
    }

  override def debugSessionStart(params: DebugSessionParams): CompletableFuture[DebugSessionAddress] =
    javaFuture("debugSessionStart") {
      logger.debug(s"debugSessionStart for params ${params}")
      ensureRunning()
      throw new NotImplementedError("debugSessionStart is not supported in Deder BSP server")
    }

  override def onRunReadStdin(params: ReadParams): Unit = {
    logger.debug(s"onRunReadStdin for params ${params}")
    ensureRunning()
    // TODO
    throw new NotImplementedError("onRunReadStdin is not supported in Deder BSP server")
  }

  override def buildShutdown(): CompletableFuture[Object] = javaFuture("buildShutdown") {
    logger.debug(s"buildShutdown")
    ensureRunning()
    running.set(false)
    null.asInstanceOf[Object]
  }

  override def onBuildExit(): Unit = traced("onBuildExit") {
    logger.debug(s"onBuildExit")
    onExit() // just closes the unix socket connection
  }

  /** Called by projectState when CLI shutdown is requested — gracefully ends the BSP session */
  def initiateShutdown(): Unit = {
    logger.info("Initiating BSP server shutdown (CLI shutdown requested)...")
    running.set(false)
    cancelInFlightCompilationsOnShutdown()
    Thread.ofVirtual().name("bsp-shutdown-close").start(() => {
      try Thread.sleep(200) // give JSON-RPC layer a brief window to flush cancelled responses
      catch {
        case _: InterruptedException =>
      }
      try { onExit() } catch { case _: Exception => }
    })
  }

  private def cancelInFlightCompilationsOnShutdown(): Unit = {
    val inFlightSnapshot = inFlightCompilations.values().asScala.toSet
    inFlightSnapshot.foreach { inFlight =>
      Option(inFlight.primaryOriginId).filter(_.nonEmpty).foreach(projectState.cancelRequest)
      inFlight.pendingRequests.asScala.foreach { pr =>
        Option(pr.originId).filter(_.nonEmpty).foreach(projectState.cancelRequest)
      }
      if !inFlight.compileFuture.isDone then
        inFlight.compileFuture.complete(cancelledCompileResult(inFlight.primaryOriginId))
    }
  }

  private def cancelledCompileResult(originId: String): CompileResult = {
    val cancelled = new CompileResult(StatusCode.CANCELLED)
    cancelled.setOriginId(originId)
    cancelled
  }

  private def ensureRunning(): Unit = {
    if !running.get then throw DederException("BSP server is shut down, not accepting more requests")
  }

  private def isTestModule(module: DederModule): Boolean =
    val testModuleTypes =
      Set(ModuleType.JAVA_TEST, ModuleType.SCALA_TEST, ModuleType.SCALA_JS_TEST, ModuleType.SCALA_NATIVE_TEST)
    testModuleTypes.contains(module.`type`)

  private def buildTarget(
      module: DederModule,
      projectStateData: DederProjectStateData,
      visibleModuleIds: Set[String]
  ): BuildTarget = {
    val id = buildTargetId(module)
    val isTestModule0 = isTestModule(module)

    val serverNotificationsLogger = makeServerNotificationsLogger(
      moduleId = Some(module.id),
      isCompileTask = true
    )
    val isAppModule = tryExecuteTask(serverNotificationsLogger, module.id, coreTasks.finalMainClassTask)(None).isDefined
    val tags = List(
      List(BuildTargetTag.APPLICATION).filter(_ => isAppModule),
      List(BuildTargetTag.TEST).filter(_ => isTestModule0),
      List(BuildTargetTag.LIBRARY).filter(_ => !isTestModule0 && !isAppModule)
    ).flatten
    val languageIds = module.`type` match {
      case ModuleType.SCALA | ModuleType.SCALA_TEST | ModuleType.SCALA_JS | ModuleType.SCALA_JS_TEST |
          ModuleType.SCALA_NATIVE | ModuleType.SCALA_NATIVE_TEST =>
        List("scala", "java")
      case ModuleType.JAVA | ModuleType.JAVA_TEST => List("java")
    }
    val dependencies = module.moduleDeps.asScala.filter(dep => visibleModuleIds.contains(dep.id)).map(buildTargetId)
    val capabilities = new BuildTargetCapabilities()
    capabilities.setCanCompile(true)
    capabilities.setCanRun(isAppModule)
    capabilities.setCanTest(isTestModule0)
    capabilities.setCanDebug(false) // Metals does it for us https://github.com/scalameta/metals/issues/5928
    val buildTarget = new BuildTarget(id, tags.asJava, languageIds.asJava, dependencies.asJava, capabilities)
    buildTarget.setDisplayName(module.id)
    buildTarget.setBaseDirectory(DederPath(module.root).absPath.toNIO.toUri.toString)
    module match {
      case m: DederProject.ScalaJsModule =>
        val binaryVersion = ScalaParameters(m.scalaVersion).scalaBinaryVersion
        val scalaBuildTarget =
          new ScalaBuildTarget("org.scala-lang", m.scalaVersion, binaryVersion, ScalaPlatform.JS, List.empty.asJava)
        buildTarget.setData(scalaBuildTarget)
        buildTarget.setDataKind(BuildTargetDataKind.SCALA)
      case m: DederProject.ScalaNativeModule =>
        val binaryVersion = ScalaParameters(m.scalaVersion).scalaBinaryVersion
        val scalaBuildTarget =
          new ScalaBuildTarget("org.scala-lang", m.scalaVersion, binaryVersion, ScalaPlatform.NATIVE, List.empty.asJava)
        buildTarget.setData(scalaBuildTarget)
        buildTarget.setDataKind(BuildTargetDataKind.SCALA)
      case m: DederProject.ScalaModule =>
        val binaryVersion = ScalaParameters(m.scalaVersion).scalaBinaryVersion
        val scalaBuildTarget =
          new ScalaBuildTarget("org.scala-lang", m.scalaVersion, binaryVersion, ScalaPlatform.JVM, List.empty.asJava)
        buildTarget.setData(scalaBuildTarget)
        buildTarget.setDataKind(BuildTargetDataKind.SCALA)
      case m: DederProject.JavaModule =>
        val jvmBuildTarget = new JvmBuildTarget()
        jvmBuildTarget.setJavaHome(m.javaHome)
        jvmBuildTarget.setJavaVersion(m.javaVersion)
        buildTarget.setData(jvmBuildTarget)
        buildTarget.setDataKind(BuildTargetDataKind.JVM)
      case _ =>
    }
    buildTarget
  }

  private def buildTargetId(module: DederModule): BuildTargetIdentifier =
    BuildTargetIdentifier(
      DederGlobals.projectRootDir.toURI.toString + "#" + module.id
    )

  private def visibleModuleIds(projectStateData: DederProjectStateData): Set[String] =
    BspVisibleTargets.visibleModuleIds(projectStateData.projectConfig.modules.asScala.toSeq)

  private def resolveVisibleTarget(
      projectStateData: DederProjectStateData,
      targetId: BuildTargetIdentifier
  ): DederModule =
    resolveVisibleTargets(projectStateData, Seq(targetId)).head._2

  private def resolveVisibleTargets(
      projectStateData: DederProjectStateData,
      targetIds: Seq[BuildTargetIdentifier]
  ): Seq[(BuildTargetIdentifier, DederModule)] = {
    val visibleIds = visibleModuleIds(projectStateData)
    targetIds.map { targetId =>
      val moduleId = targetId.moduleId
      projectStateData.tasksResolver.modulesMap.get(moduleId) match {
        case Some(module) if visibleIds.contains(moduleId) =>
          targetId -> module
        case Some(_) =>
          throw DederException(s"BSP target '$moduleId' is not visible")
        case None =>
          throw DederException(s"Unknown BSP target '$moduleId'")
      }
    }
  }

  private def resolveModule(moduleId: String): Option[DederModule] =
    withLastGoodState(_ => None) { projectStateData =>
      projectStateData.tasksResolver.modulesMap.get(moduleId)
    }

  private def withLastGoodState[T](onError: String => T)(f: DederProjectStateData => T): T =
    withLastGoodState(Some(onError))(f)

  private def withLastGoodState[T](onError: Option[String => T])(f: DederProjectStateData => T): T =
    projectState.readState(useLastGood = true) match {
      case Left(errorMessage) =>
        onError
          .map(_.apply(errorMessage))
          .getOrElse(throw DederException(s"Cannot get last good project state: ${errorMessage}"))
      case Right(projectStateData) =>
        f(projectStateData)
    }

  private def tryExecuteTask[T](
      serverNotificationsLogger: ServerNotificationsLogger,
      moduleId: String,
      task: Task[T, ?, ?],
      args: Seq[String] = Seq.empty
  )(fallback: => T): T =
    try executeTask(serverNotificationsLogger, moduleId, task, args)
    catch case _: TaskEvaluationException => fallback

  private def tryExecuteTask[T](
      serverNotificationsLogger: ServerNotificationsLogger,
      moduleId: String,
      task: Task[T, ?, ?],
      args: Seq[String],
      originId: String
  )(onError: TaskEvaluationException => Unit): Unit =
    try executeTask(serverNotificationsLogger, moduleId, task, args, originId)
    catch case e: TaskEvaluationException => onError(e)

  private def executeTask[T](
      serverNotificationsLogger: ServerNotificationsLogger,
      moduleId: String,
      task: Task[T, ?, ?],
      args: Seq[String] = Seq.empty,
      originId: String = null
  ): T =
    projectState.executeTask(moduleId, task, args, serverNotificationsLogger, useLastGood = true, requestId = Option(originId), callerType = CallerType.Bsp).res

  /** Execute the compile task, exposing whether the result was served from cache (in which case
    * the compiler was skipped and no live diagnostics notifications were emitted). */
  private def executeCompileTask(
      serverNotificationsLogger: ServerNotificationsLogger,
      moduleId: String,
      originId: String
  ): (res: ba.sake.deder.CompileResult, fromCache: Boolean) = {
    val r = projectState.executeTask(
      moduleId, coreTasks.compileTask, Seq.empty, serverNotificationsLogger,
      useLastGood = true, requestId = Option(originId), callerType = CallerType.Bsp
    )
    (r.res, r.fromCache)
  }

  private def bspDiagnostic(d: ba.sake.deder.CompileDiagnostic): Diagnostic = {
    val range = new bsp4j.Range(
      new bsp4j.Position(d.range.startLine, d.range.startChar),
      new bsp4j.Position(d.range.endLine, d.range.endChar)
    )
    val out = new Diagnostic(range, d.message)
    out.setSeverity(d.severity match {
      case ba.sake.deder.CompileSeverity.Error   => DiagnosticSeverity.ERROR
      case ba.sake.deder.CompileSeverity.Warning => DiagnosticSeverity.WARNING
      case ba.sake.deder.CompileSeverity.Info    => DiagnosticSeverity.INFORMATION
      case ba.sake.deder.CompileSeverity.Hint    => DiagnosticSeverity.HINT
    })
    d.code.foreach(out.setCode)
    out.setSource("deder")
    out
  }

  /** Render the complete diagnostics picture for a module from a CompileResult: publish each known
    * source file with reset=true. Clean files (empty list) thereby clear stale IDE markers; files
    * with diagnostics set them. Used on cache hits and to finalize cache-miss compiles, so both
    * converge on identical, complete state. */
  private def renderCompileResult(
      result: ba.sake.deder.CompileResult,
      targetId: BuildTargetIdentifier
  ): Unit =
    result.diagnostics.foreach { fd =>
      val uri = fd.file.absPath.toNIO.toUri.toString
      val diags = fd.diagnostics.map(bspDiagnostic).asJava
      client.onBuildPublishDiagnostics(
        PublishDiagnosticsParams(TextDocumentIdentifier(uri), targetId, diags, true)
      )
    }

  private def toBspLogMessage(n: ServerNotification.Log): LogMessageParams = {
    val level = n.level match {
      case ServerNotification.LogLevel.ERROR   => MessageType.ERROR
      case ServerNotification.LogLevel.WARNING => MessageType.WARNING
      case ServerNotification.LogLevel.INFO    => MessageType.INFO
      case ServerNotification.LogLevel.DEBUG   => MessageType.LOG
      case ServerNotification.LogLevel.TRACE   => MessageType.LOG
    }
    new LogMessageParams(level, n.message)
  }

  private def javaFuture[T](spanName: String, originId: Option[String] = None)(thunk: => T): CompletableFuture[T] = {
    // Capture W3C traceparent on the dispatch thread (ThreadLocal is lost across supplyAsync)
    val capturedTraceparent = Option(RequestContext.traceparent.get())
    CompletableFuture.supplyAsync { () =>
      // Propagate to the async worker thread so traced() can read it
      capturedTraceparent.foreach(RequestContext.traceparent.set)
      traced(spanName, originId) {
        thunk
      }
    }
  }

  private def traced[T](spanName: String, originId: Option[String] = None)(thunk: => T): T = {
    val spanBuilder = OTEL.TRACER
      .spanBuilder(s"bsp.${spanName}")
      .setAttribute("originId", originId.getOrElse("unknown"))
      .setAttribute("clientId", clientParams.map(_.getDisplayName).getOrElse("unknown"))
      .setAttribute("clientVersion", clientParams.map(_.getVersion).getOrElse("unknown"))
      .setAttribute("clientBspVersion", clientParams.map(_.getBspVersion).getOrElse("unknown"))
      .setAttribute("request.id", UUID.randomUUID().toString)

    // Link to Metals' parent span if traceparent is present
    Option(RequestContext.traceparent.get()).foreach { tp =>
      spanBuilder.setParent(OTEL.extractParentContext(tp))
    }

    val span = spanBuilder.startSpan()
    try {
      Using.resource(span.makeCurrent()) { scope =>
        thunk
      }
    } catch {
      case e: Throwable =>
        span.recordException(e)
        span.setStatus(OtelStatusCode.ERROR)
        logger.error(s"Unhandled exception in BSP handler '$spanName'", e)
        throw e
    } finally {
      RequestContext.traceparent.remove()
      span.end()
    }
  }

  extension (id: BuildTargetIdentifier) {
    def moduleId: String = id.getUri.split("#").last
  }
}
