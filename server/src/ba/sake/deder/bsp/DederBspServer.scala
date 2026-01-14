package ba.sake.deder.bsp

import java.io.File
import java.util.UUID
import java.util.concurrent.*
import scala.jdk.CollectionConverters.*
import com.typesafe.scalalogging.StrictLogging
import ch.epfl.scala.bsp4j
import ch.epfl.scala.bsp4j.*
import org.eclipse.lsp4j.jsonrpc.Launcher
import dependency.ScalaParameters
import ba.sake.deder.*
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.DederModule
import ba.sake.deder.deps.DependencyResolver
import ba.sake.deder.config.DederProject.ModuleType
import java.util.concurrent.atomic.AtomicBoolean

class DederBspServer(projectState: DederProjectState, onExit: () => Unit)
    extends BuildServer,
      JvmBuildServer,
      JavaBuildServer,
      ScalaBuildServer,
      StrictLogging {

  var client: BuildClient = compiletime.uninitialized // set by DederBspProxyServer

  private val running = AtomicBoolean(true)

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
        // we have everything in server logs
        case cs: ServerNotification.CompileStarted =>
          // dont send notification if compile was triggered transitively by another task
          // e.g. mainClasses -> compile
          // or for dependent modules, we only care about the module being compiled directly!
          val targetId = resolveModule(cs.moduleId).map(buildTargetId)
          val isRelevantCompileNotification = isCompileTask && moduleId.contains(cs.moduleId)
          if isRelevantCompileNotification then {
            val taskStartParams = TaskStartParams(taskId.orNull)
            taskStartParams.setEventTime(System.currentTimeMillis())
            taskStartParams.setOriginId(originId.orNull)
            taskStartParams.setMessage(s"Compiling ${cs.moduleId} ...")
            taskStartParams.setDataKind(TaskStartDataKind.COMPILE_TASK)
            taskStartParams.setData(new CompileTask(targetId.orNull))
            client.onBuildTaskStart(taskStartParams)
            // reset diagnostics for these files
            cs.files.foreach { file =>
              val fileUri = TextDocumentIdentifier(file.toURI.toString)
              val params = PublishDiagnosticsParams(fileUri, targetId.orNull, List.empty.asJava, true)
              client.onBuildPublishDiagnostics(params)
            }
          }
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
          val targetId = resolveModule(cd.moduleId).map(buildTargetId)
          val isRelevantCompileNotification = isCompileTask && moduleId.contains(cd.moduleId)
          if isRelevantCompileNotification then {
            val file = cd.problem.position.sourceFile.get
            val problem = cd.problem
            val fileUri = TextDocumentIdentifier(file.toURI.toString)
            val range = {
              val pos = problem.position
              // Zinc's range starts at 1 whereas BSP at 0
              val startLine = pos.startLine().orElse(1) - 1
              val startColumn = pos.startColumn().orElse(1) - 1
              val endLine = pos.endLine().orElse(1) - 1
              val endColumn = pos.endColumn().orElse(1) - 1
              new bsp4j.Range(
                new bsp4j.Position(startLine, startColumn),
                new bsp4j.Position(endLine, endColumn)
              )
            }
            val severity = problem.severity() match {
              case xsbti.Severity.Error => DiagnosticSeverity.ERROR
              case xsbti.Severity.Warn  => DiagnosticSeverity.WARNING
              case xsbti.Severity.Info  => DiagnosticSeverity.INFORMATION
            }
            val diagnostic = new Diagnostic(range, problem.message())
            diagnostic.setSeverity(severity)
            diagnostic.setCode(problem.category())
            diagnostic.setSource("deder")
            val targetId = resolveModule(cd.moduleId).map(buildTargetId)
            val params = PublishDiagnosticsParams(fileUri, targetId.orNull, List(diagnostic).asJava, false)
            client.onBuildPublishDiagnostics(params)
          }
        case cf: ServerNotification.CompileFinished =>
          val targetId = resolveModule(cf.moduleId).map(buildTargetId)
          val isRelevantCompileNotification = isCompileTask && moduleId.contains(cf.moduleId)
          if isRelevantCompileNotification then {
            val status = if cf.errors == 0 then StatusCode.OK else StatusCode.ERROR
            val taskFinishParams = TaskFinishParams(taskId.orNull, status)
            taskFinishParams.setEventTime(System.currentTimeMillis())
            taskFinishParams.setOriginId(originId.orNull)
            taskFinishParams.setMessage(s"Finished compiling ${cf.moduleId}")
            taskFinishParams.setDataKind(TaskFinishDataKind.COMPILE_REPORT)
            taskFinishParams.setData(new CompileReport(targetId.orNull, cf.errors, cf.warnings))
            client.onBuildTaskFinish(taskFinishParams)
          }
        case cf: ServerNotification.CompileFailed =>
          val targetId = resolveModule(cf.moduleId).map(buildTargetId)
          val isRelevantCompileNotification = isCompileTask && moduleId.contains(cf.moduleId)
          if isRelevantCompileNotification then {
            val taskFinishParams = TaskFinishParams(taskId.orNull, StatusCode.ERROR)
            taskFinishParams.setEventTime(System.currentTimeMillis())
            taskFinishParams.setOriginId(originId.orNull)
            taskFinishParams.setMessage(s"Finished compiling ${cf.moduleId}")
            taskFinishParams.setDataKind(TaskFinishDataKind.COMPILE_REPORT)
            taskFinishParams.setData(new CompileReport(targetId.orNull, cf.errors, cf.warnings))
            client.onBuildTaskFinish(taskFinishParams)
          }
        case n: ServerNotification.RequestFinished => // do nothing
        case n: ServerNotification.Output          => // do nothing
        case n: ServerNotification.RunSubprocess   => // do nothing
      }
    }
  }

  override def buildInitialize(params: InitializeBuildParams): CompletableFuture[InitializeBuildResult] =
    CompletableFuture.supplyAsync { () =>
      logger.debug(s"buildInitialize for params: ${params}")
      ensureRunning()
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
      val result = new InitializeBuildResult("deder-bsp", "0.0.1", "2.2.0-M2", capabilities)
      logger.debug(s"buildInitialize for params: ${params} return: ${result}")
      result
    }

  override def onBuildInitialized(): Unit = {
    logger.debug(s"onBuildInitialized")
    ensureRunning()
    // we dont trigger compile immediately
    // coz there is no progress seen in metals.. just "importing"
  }

  override def workspaceReload(): CompletableFuture[Object] = CompletableFuture.supplyAsync { () =>
    logger.debug(s"workspaceReload")
    ensureRunning()
    // auto reloaded on file changes
    ().asInstanceOf[Object]
  }

  override def workspaceBuildTargets(): CompletableFuture[WorkspaceBuildTargetsResult] = CompletableFuture.supplyAsync {
    () =>
      logger.debug("workspaceBuildTargets called")
      ensureRunning()
      val buildTargets = projectState.lastGood match {
        case Left(errorMessage) =>
          List.empty
        case Right(projectStateData) =>
          projectStateData.projectConfig.modules.asScala.map(m => buildTarget(m, projectStateData)).toList
      }
      val result = new WorkspaceBuildTargetsResult(buildTargets.asJava)
      logger.debug(s"workspaceBuildTargets return: ${result}")
      result
  }

  override def buildTargetSources(params: SourcesParams): CompletableFuture[SourcesResult] =
    CompletableFuture.supplyAsync { () =>
      logger.debug(s"buildTargetSources for params: ${params}")
      ensureRunning()
      val sourcesItems = params.getTargets.asScala.flatMap { targetId =>
        val moduleId = targetId.moduleId
        val serverNotificationsLogger = makeServerNotificationsLogger()
        withLastGoodState { projectStateData =>
          val coreTasks = projectStateData.tasksRegistry.coreTasks
          val sourceDirs = executeTask(serverNotificationsLogger, moduleId, coreTasks.sourcesTask)
          sourceDirs.map { srcDir =>
            val srcDirPath = srcDir.absPath
            val sourceItems =
              if os.exists(srcDirPath) then
                os.walk(srcDirPath).map { srcFile =>
                  new SourceItem(
                    srcFile.toNIO.toUri.toString,
                    if (os.isDir(srcFile)) SourceItemKind.DIRECTORY else SourceItemKind.FILE,
                    false // generated
                  )
                }
              else List.empty
            val sourcesItem = SourcesItem(targetId, sourceItems.asJava)
            sourcesItem.setRoots(List(srcDirPath.toNIO.toUri.toString).asJava)
            sourcesItem
          }
        }
      }
      val result = new SourcesResult(sourcesItems.asJava)
      logger.debug(s"buildTargetSources for params: ${params} return: ${result}")
      result
    }

  override def buildTargetInverseSources(params: InverseSourcesParams): CompletableFuture[InverseSourcesResult] =
    CompletableFuture.supplyAsync { () =>
      logger.debug(s"buildTargetInverseSources for params: ${params}")
      ensureRunning()
      val serverNotificationsLogger = makeServerNotificationsLogger()
      val targetIds = withLastGoodState { projectStateData =>
        val coreTasks = projectStateData.tasksRegistry.coreTasks
        val modules = projectStateData.tasksResolver.allModules.filter { m =>
          val sourceDirs = executeTask(serverNotificationsLogger, m.id, coreTasks.sourcesTask)
          sourceDirs.exists { srcDir =>
            val srcDirUri = srcDir.absPath.toURI.toString()
            srcDirUri.startsWith(params.getTextDocument.getUri)
          }
        }
        modules.map(buildTargetId)
      }
      val result = InverseSourcesResult(targetIds.asJava)
      logger.debug(s"buildTargetInverseSources for params: ${params} return: ${result}")
      result
    }

  override def buildTargetResources(params: ResourcesParams): CompletableFuture[ResourcesResult] =
    CompletableFuture.supplyAsync { () =>
      logger.debug(s"buildTargetResources for params: ${params}")
      ensureRunning()
      val serverNotificationsLogger = makeServerNotificationsLogger()
      val resourcesItems = params.getTargets.asScala.flatMap { targetId =>
        val moduleId = targetId.moduleId
        withLastGoodState { projectStateData =>
          val coreTasks = projectStateData.tasksRegistry.coreTasks
          val resourceDirs = executeTask(serverNotificationsLogger, moduleId, coreTasks.resourcesTask)
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

  override def buildTargetCompile(params: CompileParams): CompletableFuture[CompileResult] =
    CompletableFuture.supplyAsync { () =>
      logger.debug(s"buildTargetCompile for params: ${params}")
      ensureRunning()
      if (params.getTargets.isEmpty) {
        // no need to start a task, it would confuse IDE
        val compileResult = new CompileResult(StatusCode.OK)
        compileResult.setOriginId(params.getOriginId)
        compileResult
      } else {
        val taskId = TaskId(s"compile-${UUID.randomUUID}")
        val taskStartParams = TaskStartParams(taskId)
        taskStartParams.setEventTime(System.currentTimeMillis())
        taskStartParams.setOriginId(params.getOriginId)
        taskStartParams.setMessage(s"Compiling modules: ${params.getTargets.asScala.map(_.moduleId).mkString(", ")}")
        client.onBuildTaskStart(taskStartParams)
        var allCompileSucceeded = true
        withLastGoodState { projectStateData =>
          val coreTasks = projectStateData.tasksRegistry.coreTasks
          params.getTargets.asScala.foreach { targetId =>
            val moduleId = targetId.moduleId
            val subtaskId = TaskId(s"compile-${moduleId}-${UUID.randomUUID}")
            subtaskId.setParents(List(taskId.getId).asJava)
            logger.debug(s"buildTargetCompile subtaskId ${subtaskId}")
            val serverNotificationsLogger = makeServerNotificationsLogger(
              originId = Option(params.getOriginId),
              taskId = Some(subtaskId),
              moduleId = Some(moduleId),
              isCompileTask = true
            )
            val module = projectStateData.tasksResolver.modulesMap(moduleId)
            var currentModuleCompileSucceeded = true
            try {
              executeTask(serverNotificationsLogger, moduleId, coreTasks.compileTask)
            } catch {
              case e: TaskEvaluationException =>
                currentModuleCompileSucceeded = false
                allCompileSucceeded = false
            }
          }
        }

        val status = if allCompileSucceeded then StatusCode.OK else StatusCode.ERROR
        val taskFinishParams = TaskFinishParams(taskId, status)
        taskFinishParams.setEventTime(System.currentTimeMillis())
        taskFinishParams.setOriginId(params.getOriginId)
        taskFinishParams.setMessage(
          s"Finished compiling modules: ${params.getTargets.asScala.map(_.moduleId).mkString(", ")}"
        )
        client.onBuildTaskFinish(taskFinishParams)
        val result = new CompileResult(status)
        result.setOriginId(params.getOriginId)
        logger.debug(s"buildTargetCompile for params ${params} return: ${result}")
        result
      }
    }

  override def buildTargetCleanCache(params: CleanCacheParams): CompletableFuture[CleanCacheResult] =
    CompletableFuture.supplyAsync { () =>
      logger.debug(s"buildTargetCleanCache for params: ${params}")
      ensureRunning()
      withLastGoodState(_ => CleanCacheResult(false)) { projectStateData =>
        val coreTasks = projectStateData.tasksRegistry.coreTasks
        val cleaned = params.getTargets.asScala.forall { targetId =>
          val moduleId = targetId.moduleId
          DederCleaner.cleanModules(Seq(moduleId))
        }
        val result = CleanCacheResult(cleaned)
        logger.debug(s"buildTargetCleanCache for params ${params} return: ${result}")
        result
      }
    }

  // list of dependencies(maven)
  override def buildTargetDependencyModules(
      params: DependencyModulesParams
  ): CompletableFuture[DependencyModulesResult] = CompletableFuture.supplyAsync { () =>
    logger.debug(s"buildTargetDependencyModules for params: ${params}")
    ensureRunning()
    val items = withLastGoodState { projectStateData =>
      val coreTasks = projectStateData.tasksRegistry.coreTasks
      val serverNotificationsLogger = makeServerNotificationsLogger()
      params.getTargets.asScala.map { targetId =>
        val moduleId = targetId.moduleId
        val module = projectStateData.tasksResolver.modulesMap(moduleId)
        val dependencies =
          try executeTask(serverNotificationsLogger, moduleId, coreTasks.dependenciesTask)
          catch case e: TaskEvaluationException => Seq.empty
        val fetchRes = DependencyResolver.fetch(dependencies)
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
      }
    }
    val result = DependencyModulesResult(items.asJava)
    logger.debug(s"buildTargetDependencyModules for params ${params} return: ${result}")
    result
  }

  // source jars of dependencies
  override def buildTargetDependencySources(
      params: DependencySourcesParams
  ): CompletableFuture[DependencySourcesResult] = CompletableFuture.supplyAsync { () =>
    logger.debug(s"buildTargetDependencySources for params ${params}")
    ensureRunning()
    val items = withLastGoodState { projectStateData =>
      val coreTasks = projectStateData.tasksRegistry.coreTasks
      val serverNotificationsLogger = makeServerNotificationsLogger()
      params.getTargets.asScala.map { targetId =>
        val moduleId = targetId.moduleId
        val module = projectStateData.tasksResolver.modulesMap(moduleId)
        val dependencies =
          try executeTask(serverNotificationsLogger, moduleId, coreTasks.dependenciesTask)
          catch case e: TaskEvaluationException => Seq.empty
        val fetchRes = DependencyResolver.fetch(dependencies)
        val depSources = fetchRes.getDependencies.asScala.map { dep =>
          dep.withClassifier("sources").withType("jar").withConfiguration("sources")
        }
        logger.debug(s"Fetching sources for dependencies: ${depSources.map(_.toString).mkString(", ")}")
        val sourceArtifactFiles = DependencyResolver
          .doFetch(depSources.toSeq)
          .getArtifacts
          .asScala
          .map(_.getValue())
          .toSeq
          .filter(_.getName.endsWith("-sources.jar")) // TODO coursier returns main artifact too..???
        DependencySourcesItem(targetId, sourceArtifactFiles.map(f => f.toURI.toString).asJava)
      }
    }
    val result = DependencySourcesResult(items.asJava)
    logger.debug(s"buildTargetDependencySources for params ${params} return: ${result}")
    result
  }

  // hidden/ignored/output dirs
  override def buildTargetOutputPaths(params: OutputPathsParams): CompletableFuture[OutputPathsResult] =
    CompletableFuture.supplyAsync { () =>
      logger.debug(s"buildTargetOutputPaths for params ${params}")
      ensureRunning()
      val excludedDirNames = Seq(".deder", ".bsp", ".metals", ".idea", ".vscode")
      val outputPathsItems = for {
        dirName <- excludedDirNames
        targetId <- params.getTargets.asScala
        outputPathItems = OutputPathItem(DederPath(dirName).absPath.toNIO.toUri.toString, OutputPathItemKind.DIRECTORY)
      } yield OutputPathsItem(targetId, List.empty.asJava)
      val result = new OutputPathsResult(outputPathsItems.asJava)
      logger.debug(s"buildTargetOutputPaths for params ${params} return: ${result}")
      result
    }

  override def buildTargetJavacOptions(params: JavacOptionsParams): CompletableFuture[JavacOptionsResult] =
    CompletableFuture.supplyAsync { () =>
      logger.debug(s"buildTargetJavacOptions for params ${params}")
      ensureRunning()
      val javacOptionsItems = withLastGoodState { projectStateData =>
        val coreTasks = projectStateData.tasksRegistry.coreTasks
        val serverNotificationsLogger = makeServerNotificationsLogger()
        params.getTargets.asScala.flatMap { targetId =>
          val moduleId = targetId.moduleId
          val classesDir =
            executeTask(serverNotificationsLogger, moduleId, coreTasks.classesDirTask).toNIO.toUri.toString
          val semanticdbDir =
              executeTask(serverNotificationsLogger, moduleId, coreTasks.semanticdbDirTask).toNIO.toUri.toString
          val javacOptions = executeTask(serverNotificationsLogger, moduleId, coreTasks.javacOptionsTask)
          val javacAnnotationProcessors =
            try executeTask(serverNotificationsLogger, moduleId, coreTasks.javacAnnotationProcessorsTask)
            catch case e: TaskEvaluationException => Seq.empty
          val finalJavacOptions = javacOptions ++
            Seq(
              "-processorpath",
              javacAnnotationProcessors.map(_.toString).mkString(File.pathSeparator),
              s"-Xplugin:semanticdb -sourceroot:${DederGlobals.projectRootDir} -targetroot:${semanticdbDir} -build-tool:sbt"
            )
          val compileClasspath =
            try
              executeTask(serverNotificationsLogger, moduleId, coreTasks.compileClasspathTask)
                .map(_.toNIO.toUri.toString)
                .toList
            catch case e: TaskEvaluationException => List.empty
          val javacOptionsItem =
            JavacOptionsItem(targetId, finalJavacOptions.asJava, compileClasspath.asJava, classesDir)
          List(javacOptionsItem)
        }
      }
      val result = JavacOptionsResult(javacOptionsItems.asJava)
      logger.debug(s"buildTargetJavacOptions for params ${params} return: ${result}")
      result
    }

  override def buildTargetScalaMainClasses(
      params: ScalaMainClassesParams
  ): CompletableFuture[ScalaMainClassesResult] = CompletableFuture.supplyAsync { () =>
    logger.debug(s"buildTargetScalaMainClasses for params ${params}")
    ensureRunning()
    val items = withLastGoodState { projectStateData =>
      val coreTasks = projectStateData.tasksRegistry.coreTasks
      val serverNotificationsLogger = makeServerNotificationsLogger()
      params.getTargets.asScala.flatMap { targetId =>
        val moduleId = targetId.moduleId
        val module = projectStateData.tasksResolver.modulesMap(moduleId)
        // metals sometimes requires it even for test modules.. sigh
        Option.when(isAppModule(module)) {
          val items =
            try {
              executeTask(serverNotificationsLogger, moduleId, coreTasks.mainClassesTask).map { mainClass =>
                // TODO arguments + JVM opts
                ScalaMainClass(mainClass, List.empty.asJava, List.empty.asJava)
              }
            } catch {
              case e: TaskEvaluationException =>
                // module failed to compile for example
                List.empty
            }
          ScalaMainClassesItem(targetId, items.asJava)
        }
      }
    }
    val result = ScalaMainClassesResult(items.asJava)
    result.setOriginId(params.getOriginId)
    logger.debug(s"buildTargetScalaMainClasses for params ${params} return: ${result}")
    result
  }

  override def buildTargetScalaTestClasses(
      params: ScalaTestClassesParams
  ): CompletableFuture[ScalaTestClassesResult] = CompletableFuture.supplyAsync { () =>
    logger.debug(s"buildTargetScalaTestClasses for params ${params}")
    ensureRunning()
    val items = withLastGoodState { projectStateData =>
      val coreTasks = projectStateData.tasksRegistry.coreTasks
      val testModules = projectStateData.projectConfig.modules.asScala.collect { case m: DederProject.ScalaTestModule =>
        m
      }
      val serverNotificationsLogger = makeServerNotificationsLogger()
      testModules.flatMap { module =>
        val targetId = buildTargetId(module)
        try {
          val frameworkTests =
            try executeTask(serverNotificationsLogger, module.id, coreTasks.testClassesTask)
            catch case e: TaskEvaluationException => Seq.empty
          frameworkTests.map { ft =>
            val item = ScalaTestClassesItem(targetId, ft.testClasses.asJava)
            item.setFramework(ft.framework)
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
    CompletableFuture.supplyAsync { () =>
      logger.debug(s"buildTargetScalacOptions for params ${params}")
      ensureRunning()
      val scalacOptionsItems = withLastGoodState { projectStateData =>
        val coreTasks = projectStateData.tasksRegistry.coreTasks
        val serverNotificationsLogger = makeServerNotificationsLogger()
        params.getTargets.asScala.flatMap { targetId =>
          val moduleId = targetId.moduleId
          val scalaVersion = executeTask(serverNotificationsLogger, moduleId, coreTasks.scalaVersionTask)
          val scalacOptions = executeTask(serverNotificationsLogger, moduleId, coreTasks.scalacOptionsTask)
          val semanticdbDir =
            executeTask(serverNotificationsLogger, moduleId, coreTasks.semanticdbDirTask).toNIO.toUri.toString
          val scalacPlugins =
            try executeTask(serverNotificationsLogger, moduleId, coreTasks.scalacPluginsTask)
            catch case e: TaskEvaluationException => Seq.empty
          val semanticdbOptions =
            if scalaVersion.startsWith("3.") then
              scalacPlugins.map(p => s"-Xplugin:${p.toString}") ++
                Seq("-Xsemanticdb", s"-sourceroot", s"${DederGlobals.projectRootDir}",  "-semanticdb-target:", semanticdbDir.toString)
            else
              Seq("-Yrangepos", s"-P:semanticdb:sourceroot:${DederGlobals.projectRootDir}",s"-P:semanticdb:targetroot:${semanticdbDir}") ++
                scalacPlugins.map(p => s"-Xplugin:${p.toString}")
          val finalScalacOptions = scalacOptions ++ semanticdbOptions
          val compileClasspath =
            try
              executeTask(serverNotificationsLogger, moduleId, coreTasks.compileClasspathTask)
                .map(_.toNIO.toUri.toString)
                .toList
            catch case e: TaskEvaluationException => List.empty
          val classesDir =
            executeTask(serverNotificationsLogger, moduleId, coreTasks.classesDirTask).toNIO.toUri.toString
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
  ): CompletableFuture[JvmCompileClasspathResult] = CompletableFuture.supplyAsync { () =>
    logger.debug(s"buildTargetJvmCompileClasspath for params ${params}")
    ensureRunning()
    val items = withLastGoodState { projectStateData =>
      val coreTasks = projectStateData.tasksRegistry.coreTasks
      val serverNotificationsLogger = makeServerNotificationsLogger()
      params.getTargets.asScala.map { targetId =>
        val moduleId = targetId.moduleId
        val compileClasspath =
          try
            executeTask(serverNotificationsLogger, moduleId, coreTasks.compileClasspathTask)
              .map(_.toNIO.toUri.toString)
              .toList
          catch case e: TaskEvaluationException => List.empty
        JvmCompileClasspathItem(targetId, compileClasspath.asJava)
      }
    }
    val result = JvmCompileClasspathResult(items.asJava)
    logger.debug(s"buildTargetJvmCompileClasspath for params ${params} return: ${result}")
    result
  }

  override def buildTargetJvmRunEnvironment(
      params: JvmRunEnvironmentParams
  ): CompletableFuture[JvmRunEnvironmentResult] = CompletableFuture.supplyAsync { () =>
    logger.debug(s"buildTargetJvmRunEnvironment for params ${params}")
    ensureRunning()
    val items = withLastGoodState { projectStateData =>
      val coreTasks = projectStateData.tasksRegistry.coreTasks
      val serverNotificationsLogger = makeServerNotificationsLogger()
      params.getTargets.asScala.flatMap { targetId =>
        val moduleId = targetId.moduleId
        val module = projectStateData.tasksResolver.modulesMap(moduleId)
        // metals sometimes requires it even for test modules.. sigh
        Option.when(isAppModule(module)) {
          val mainClasses =
            try executeTask(serverNotificationsLogger, moduleId, coreTasks.mainClassesTask)
            catch case e: TaskEvaluationException => Seq.empty
          val classpath =
            try
              executeTask(serverNotificationsLogger, moduleId, coreTasks.runClasspathTask)
                .map(_.toNIO.toUri.toString)
                .toList
            catch case e: TaskEvaluationException => List.empty
          val jvmOptions = executeTask(serverNotificationsLogger, moduleId, coreTasks.jvmOptionsTask)
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
    }
    val result = JvmRunEnvironmentResult(items.asJava)
    logger.debug(s"buildTargetJvmRunEnvironment for params ${params} return: ${result}")
    result
  }

  override def buildTargetJvmTestEnvironment(
      params: JvmTestEnvironmentParams
  ): CompletableFuture[JvmTestEnvironmentResult] = CompletableFuture.supplyAsync { () =>
    logger.debug(s"buildTargetJvmTestEnvironment for params ${params}")
    ensureRunning()
    val items = withLastGoodState { projectStateData =>
      val coreTasks = projectStateData.tasksRegistry.coreTasks
      val serverNotificationsLogger = makeServerNotificationsLogger()
      params.getTargets.asScala.map { targetId =>
        val moduleId = targetId.moduleId
        val testClasses =
          try executeTask(serverNotificationsLogger, moduleId, coreTasks.testClassesTask)
          catch case e: TaskEvaluationException => Seq.empty
        val classpath =
          try
            executeTask(serverNotificationsLogger, moduleId, coreTasks.runClasspathTask)
              .map(_.toNIO.toUri.toString)
              .toList
          catch case e: TaskEvaluationException => List.empty
        val jvmOptions = executeTask(serverNotificationsLogger, moduleId, coreTasks.jvmOptionsTask)
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
          ft.testClasses.map { testClass =>
            val args = List.empty[String] // TODO
            JvmMainClass(testClass, args.asJava)
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

  override def buildTargetRun(params: RunParams): CompletableFuture[RunResult] = CompletableFuture.supplyAsync { () =>
    logger.debug(s"buildTargetRun for params ${params}")
    ensureRunning()
    val result = withLastGoodState { projectStateData =>
      val coreTasks = projectStateData.tasksRegistry.coreTasks
      val moduleId = params.getTarget.moduleId
      val serverNotificationsLogger = makeServerNotificationsLogger(
        originId = Option(params.getOriginId),
        moduleId = Some(moduleId),
        isCompileTask = true
      )
      val isRunnable = isAppModule(resolveModule(moduleId).get)
      if !isRunnable then throw DederException(s"Module ${moduleId} does not have a main class to run")
      val args = Option(params.getArguments).map(_.asScala.toSeq).getOrElse(Seq.empty)
      val runCmd =
        try executeTask(serverNotificationsLogger, moduleId, coreTasks.runTask, args = args)
        catch case e: TaskEvaluationException => Seq.empty
      if runCmd.isEmpty then {
        logger.error(s"Failed to run module ${moduleId} via BSP")
        RunResult(StatusCode.ERROR)
      } else {
        val wd = Option(params.getWorkingDirectory).map(os.Path(_)).getOrElse(os.pwd)
        val runRes = os.proc(runCmd).call(cwd = wd, stdin = os.Pipe, stdout = os.Pipe, stderr = os.Pipe)
        val status = if runRes.exitCode == 0 then StatusCode.OK else StatusCode.ERROR
        RunResult(status)
      }
    }
    result.setOriginId(params.getOriginId)
    logger.debug(s"buildTargetRun for params ${params} return: ${result}")
    result
  }

  override def buildTargetTest(params: TestParams): CompletableFuture[TestResult] = CompletableFuture.supplyAsync {
    () =>
      logger.debug(s"buildTargetTest for params ${params}")
      ensureRunning()
      val result = withLastGoodState { projectStateData =>
        val coreTasks = projectStateData.tasksRegistry.coreTasks
        val serverNotificationsLogger =
          makeServerNotificationsLogger(originId = Option(params.getOriginId), isCompileTask = true)
        var allTestsSucceeded = true
        val targets = params.getTargets.asScala
        val untestableTargets = targets.filterNot { targetId =>
          val module = projectStateData.tasksResolver.modulesMap(targetId.moduleId)
          isTestModule(module)
        }
        if untestableTargets.nonEmpty then
          throw DederException(s"Targets are not testable: ${untestableTargets.map(_.moduleId).mkString(", ")}")
        targets.foreach { targetId =>
          val moduleId = targetId.moduleId
          try {
            val testRes = executeTask(serverNotificationsLogger, moduleId, coreTasks.testTask)
            if !testRes.success then allTestsSucceeded = false
          } catch case e: TaskEvaluationException => allTestsSucceeded = false
        }
        val status = if allTestsSucceeded then StatusCode.OK else StatusCode.ERROR
        TestResult(status)
      }
      result.setOriginId(params.getOriginId)
      logger.debug(s"buildTargetTest for params ${params} return: ${result}")
      result
  }

  override def debugSessionStart(params: DebugSessionParams): CompletableFuture[DebugSessionAddress] =
    CompletableFuture.supplyAsync { () =>
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

  override def buildShutdown(): CompletableFuture[Object] = CompletableFuture.supplyAsync { () =>
    logger.debug(s"buildShutdown")
    ensureRunning()
    running.set(false)
    null.asInstanceOf[Object]
  }

  override def onBuildExit(): Unit =
    logger.debug(s"onBuildExit")
    ensureRunning()
    onExit() // just closes the unix socket connection

  private def ensureRunning(): Unit = {
    if !running.get then throw DederException("BSP server is shut down, not accepting more requests")
  }

  private def isTestModule(module: DederModule): Boolean =
    val testModuleTypes = Set(ModuleType.SCALA_TEST)
    testModuleTypes.contains(module.`type`)

  private def isAppModule(module: DederModule): Boolean =
    module match {
      case m: DederProject.JavaModule => m.mainClass != null
      case _                          => false
    }

  private def buildTarget(module: DederModule, projectStateData: DederProjectStateData): BuildTarget = {
    val id = buildTargetId(module)
    val isTestModule0 = isTestModule(module)
    val isAppModule0 = isAppModule(module)
    val tags = List(
      List(BuildTargetTag.TEST).filter(_ => isTestModule0),
      List(BuildTargetTag.APPLICATION).filter(_ => isAppModule0),
      List(BuildTargetTag.LIBRARY).filter(_ => !isTestModule0 && !isAppModule0)
    ).flatten
    val languageIds = module.`type` match {
      case ModuleType.SCALA | ModuleType.SCALA_TEST => List("scala", "java")
      case ModuleType.JAVA                          => List("java")
    }
    val dependencies = module.moduleDeps.asScala.map(buildTargetId)
    val capabilities = new BuildTargetCapabilities()
    capabilities.setCanCompile(true)
    capabilities.setCanRun(isAppModule0)
    capabilities.setCanTest(isTestModule0)
    capabilities.setCanDebug(false) // Metals does it for us https://github.com/scalameta/metals/issues/5928
    val buildTarget = new BuildTarget(id, tags.asJava, languageIds.asJava, dependencies.asJava, capabilities)
    buildTarget.setDisplayName(module.id)
    buildTarget.setBaseDirectory(DederPath(module.root).absPath.toNIO.toUri.toString)
    module match {
      case m: DederProject.ScalaModule =>
        val binaryVersion = ScalaParameters(m.scalaVersion).scalaBinaryVersion
        val scalaBuildTarget =
          new ScalaBuildTarget("org.scala-lang", m.scalaVersion, binaryVersion, ScalaPlatform.JVM, List.empty.asJava)
        val jvmBuildTarget = new JvmBuildTarget()
        jvmBuildTarget.setJavaHome(m.javaHome)
        jvmBuildTarget.setJavaVersion(m.javaVersion)
        // scalaBuildTarget.setJvmBuildTarget(jvmBuildTarget)
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

  private def resolveModule(moduleId: String): Option[DederModule] =
    withLastGoodState { projectStateData =>
      projectStateData.tasksResolver.modulesMap.get(moduleId)
    }

  private def withLastGoodState[T](onError: String => T)(f: DederProjectStateData => T): T =
    withLastGoodState(Some(onError))(f)

  private def withLastGoodState[T](f: DederProjectStateData => T): T =
    withLastGoodState(None)(f)

  private def withLastGoodState[T](onError: Option[String => T])(f: DederProjectStateData => T): T =
    projectState.lastGood match {
      case Left(errorMessage) =>
        onError
          .map(_.apply(errorMessage))
          .getOrElse(throw DederException(s"Cannot get last good project state: ${errorMessage}"))
      case Right(projectStateData) =>
        f(projectStateData)
    }

  private def executeTask[T](
      serverNotificationsLogger: ServerNotificationsLogger,
      moduleId: String,
      task: Task[T, ?],
      args: Seq[String] = Seq.empty
  ): T =
    projectState.executeTask(moduleId, task, args, serverNotificationsLogger, useLastGood = true).res

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

  extension (id: BuildTargetIdentifier) {
    def moduleId: String = id.getUri.split("#").last
  }
}
