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

class DederBspServer(projectState: DederProjectState, onExit: () => Unit)
    extends BuildServer,
      JvmBuildServer,
      JavaBuildServer,
      ScalaBuildServer,
      StrictLogging {

  var client: BuildClient = null // set by DederBspProxyServer

  // fresh one for each BSP request!
  private def makeServerNotificationsLogger(
      originId: Option[String] = None,
      targetId: Option[BuildTargetIdentifier] = None,
      taskId: Option[TaskId] = None,
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
          val isRelevantCompileNotification = isCompileTask && targetId.map(_.moduleId) == Some(cs.moduleId)
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
          val isRelevantCompileNotification = isCompileTask && targetId.map(_.moduleId) == Some(tp.moduleId)
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
          val isRelevantCompileNotification = isCompileTask && targetId.map(_.moduleId) == Some(cd.moduleId)
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
            val params = PublishDiagnosticsParams(fileUri, targetId.orNull, List(diagnostic).asJava, false)
            client.onBuildPublishDiagnostics(params)
          }
        case cf: ServerNotification.CompileFinished =>
          val isRelevantCompileNotification = isCompileTask && targetId.map(_.moduleId) == Some(cf.moduleId)
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
        case n: ServerNotification.RequestFinished => // do nothing
        case n: ServerNotification.Output          => // do nothing
        case n: ServerNotification.RunSubprocess   => // do nothing
      }
    }
  }

  override def buildInitialize(params: InitializeBuildParams): CompletableFuture[InitializeBuildResult] = {
    logger.debug(s"BSP client connected: ${params}")
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
    CompletableFuture.completedFuture(result)
  }

  override def onBuildInitialized(): Unit = {
    // we dont trigger compile immediately
    // coz there is no progress seen in metals.. just "importing"
  }

  override def workspaceReload(): CompletableFuture[Object] = CompletableFuture.supplyAsync { () =>
    // auto reloaded on file changes
    ().asInstanceOf[Object]
  }

  override def workspaceBuildTargets(): CompletableFuture[WorkspaceBuildTargetsResult] = CompletableFuture.supplyAsync {
    () =>
      val buildTargets = projectState.lastGood match {
        case Left(errorMessage) =>
          List.empty
        case Right(projectStateData) =>
          projectStateData.projectConfig.modules.asScala.map(m => buildTarget(m, projectStateData)).toList
      }
      val result = new WorkspaceBuildTargetsResult(buildTargets.asJava)
      logger.debug(s"BSP workspaceBuildTargets called, returning: ${result}")
      result
  }

  override def buildTargetSources(params: SourcesParams): CompletableFuture[SourcesResult] =
    CompletableFuture.supplyAsync { () =>
      val sourcesItems = params.getTargets.asScala.flatMap { targetId =>
        val moduleId = targetId.moduleId
        val serverNotificationsLogger = makeServerNotificationsLogger(targetId = Some(targetId))
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
      logger.debug(
        s"BSP buildTargetSources called for ${params.getTargets.asScala.map(_.getUri)}," +
          s" returning: ${sourcesItems.toList}"
      )
      new SourcesResult(sourcesItems.asJava)
    }

  override def buildTargetInverseSources(params: InverseSourcesParams): CompletableFuture[InverseSourcesResult] =
    CompletableFuture.supplyAsync { () =>
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
      InverseSourcesResult(targetIds.asJava)
    }

  override def buildTargetResources(params: ResourcesParams): CompletableFuture[ResourcesResult] =
    CompletableFuture.supplyAsync { () =>
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
      new ResourcesResult(resourcesItems.asJava)
    }

  override def buildTargetCompile(params: CompileParams): CompletableFuture[CompileResult] =
    CompletableFuture.supplyAsync { () =>
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
        logger.debug(s"BSP buildTargetCompile called for ${params}")
        withLastGoodState { projectStateData =>
          val coreTasks = projectStateData.tasksRegistry.coreTasks
          params.getTargets.asScala.foreach { targetId =>
            val moduleId = targetId.moduleId
            val subtaskId = TaskId(s"compile-${moduleId}-${UUID.randomUUID}")
            subtaskId.setParents(List(taskId.getId()).asJava)
            logger.debug(s"BSP buildTargetCompile subtaskId ${subtaskId}")
            val serverNotificationsLogger = makeServerNotificationsLogger(
              originId = Option(params.getOriginId),
              targetId = Some(targetId),
              taskId = Some(subtaskId),
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
        val compileResult = new CompileResult(status)
        compileResult.setOriginId(params.getOriginId)
        compileResult
      }
    }

  override def buildTargetCleanCache(params: CleanCacheParams): CompletableFuture[CleanCacheResult] =
    CompletableFuture.supplyAsync { () =>
      withLastGoodState(_ => CleanCacheResult(false)) { projectStateData =>
        val coreTasks = projectStateData.tasksRegistry.coreTasks
        val cleaned = params.getTargets.asScala.forall { targetId =>
          val moduleId = targetId.moduleId
          DederCleaner.cleanModules(Seq(moduleId))
        }
        CleanCacheResult(cleaned)
      }
    }

  // list of dependencies(maven)
  override def buildTargetDependencyModules(
      params: DependencyModulesParams
  ): CompletableFuture[DependencyModulesResult] = CompletableFuture.supplyAsync { () =>
    val items = withLastGoodState { projectStateData =>
      val coreTasks = projectStateData.tasksRegistry.coreTasks
      val serverNotificationsLogger = makeServerNotificationsLogger()
      params.getTargets.asScala.map { targetId =>
        val moduleId = targetId.moduleId
        val module = projectStateData.tasksResolver.modulesMap(moduleId)
        val dependencies = executeTask(serverNotificationsLogger, moduleId, coreTasks.dependenciesTask)
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
    logger.debug(
      s"BSP buildTargetDependencyModules called for ${params.getTargets.asScala.map(_.getUri)}," +
        s" returning: ${items.toList}"
    )
    DependencyModulesResult(items.asJava)
  }

  // source jars of dependencies
  override def buildTargetDependencySources(
      params: DependencySourcesParams
  ): CompletableFuture[DependencySourcesResult] = CompletableFuture.supplyAsync { () =>
    val items = withLastGoodState { projectStateData =>
      val coreTasks = projectStateData.tasksRegistry.coreTasks
      val serverNotificationsLogger = makeServerNotificationsLogger()
      params.getTargets.asScala.map { targetId =>
        val moduleId = targetId.moduleId
        val module = projectStateData.tasksResolver.modulesMap(moduleId)
        val dependencies = executeTask(serverNotificationsLogger, moduleId, coreTasks.dependenciesTask)
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
    DependencySourcesResult(items.asJava)
  }

  // hidden/ignored/output dirs
  override def buildTargetOutputPaths(params: OutputPathsParams): CompletableFuture[OutputPathsResult] =
    CompletableFuture.supplyAsync { () =>
      val excludedDirNames = Seq(".deder", ".bsp", ".metals", ".idea", ".vscode")
      val outputPathsItems = for {
        dirName <- excludedDirNames
        targetId <- params.getTargets.asScala
        outputPathItems = OutputPathItem(DederPath(dirName).absPath.toNIO.toUri.toString, OutputPathItemKind.DIRECTORY)
      } yield OutputPathsItem(targetId, List.empty.asJava)
      new OutputPathsResult(outputPathsItems.asJava)
    }

  override def buildTargetJavacOptions(params: JavacOptionsParams): CompletableFuture[JavacOptionsResult] =
    CompletableFuture.supplyAsync { () =>
      val javacOptionsItems = withLastGoodState { projectStateData =>
        val coreTasks = projectStateData.tasksRegistry.coreTasks
        val serverNotificationsLogger = makeServerNotificationsLogger()
        params.getTargets().asScala.flatMap { targetId =>
          val moduleId = targetId.moduleId
          val classesDir =
            executeTask(serverNotificationsLogger, moduleId, coreTasks.classesDirTask).toNIO.toUri.toString
          val javacOptions = executeTask(serverNotificationsLogger, moduleId, coreTasks.javacOptionsTask)
          val javacAnnotationProcessors =
            executeTask(serverNotificationsLogger, moduleId, coreTasks.javacAnnotationProcessorsTask)
          val finalJavacOptions = javacOptions ++
            Seq(
              "-processorpath",
              javacAnnotationProcessors.map(_.toString).mkString(File.pathSeparator),
              s"-Xplugin:semanticdb -sourceroot:${DederGlobals.projectRootDir} -targetroot:${classesDir}"
            )
          val compileClasspath =
            executeTask(serverNotificationsLogger, moduleId, coreTasks.compileClasspathTask)
              .map(_.toNIO.toUri.toString)
              .toList
          val javacOptionsItem =
            JavacOptionsItem(targetId, finalJavacOptions.asJava, compileClasspath.asJava, classesDir)
          List(javacOptionsItem)
        }
      }
      JavacOptionsResult(javacOptionsItems.asJava)
    }

  override def buildTargetScalaMainClasses(
      params: ScalaMainClassesParams
  ): CompletableFuture[ScalaMainClassesResult] = CompletableFuture.supplyAsync { () =>
    val items = withLastGoodState { projectStateData =>
      val coreTasks = projectStateData.tasksRegistry.coreTasks
      val serverNotificationsLogger = makeServerNotificationsLogger()
      params.getTargets().asScala.map { targetId =>
        val moduleId = targetId.moduleId
        val module = projectStateData.tasksResolver.modulesMap(moduleId)
        // TODO figure out nicer way for test modules
        val items =
          if module.isInstanceOf[DederProject.ScalaTestModule] then List.empty
          else
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
    val result = ScalaMainClassesResult(items.asJava)
    result.setOriginId(params.getOriginId)
    logger.debug(s"BSP buildTargetScalaMainClasses called, returning: ${result}")
    result
  }

  override def buildTargetScalaTestClasses(
      params: ScalaTestClassesParams
  ): CompletableFuture[ScalaTestClassesResult] = CompletableFuture.supplyAsync { () =>
    val items = withLastGoodState { projectStateData =>
      val coreTasks = projectStateData.tasksRegistry.coreTasks
      val testModules = projectStateData.projectConfig.modules.asScala.collect { case m: DederProject.ScalaTestModule =>
        m
      }
      val serverNotificationsLogger = makeServerNotificationsLogger()
      testModules.flatMap { module =>
        val targetId = buildTargetId(module)
        try {
          val frameworkTests = executeTask(serverNotificationsLogger, module.id, coreTasks.testClassesTask)
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
    ScalaTestClassesResult(items.asJava)
  }

  override def buildTargetScalacOptions(params: ScalacOptionsParams): CompletableFuture[ScalacOptionsResult] =
    CompletableFuture.supplyAsync { () =>
      val scalacOptionsItems = withLastGoodState { projectStateData =>
        val coreTasks = projectStateData.tasksRegistry.coreTasks
        val serverNotificationsLogger = makeServerNotificationsLogger()
        params.getTargets().asScala.flatMap { targetId =>
          val moduleId = targetId.moduleId
          val scalaVersion = executeTask(serverNotificationsLogger, moduleId, coreTasks.scalaVersionTask)
          val scalacOptions = executeTask(serverNotificationsLogger, moduleId, coreTasks.scalacOptionsTask)
          val scalacPlugins = executeTask(serverNotificationsLogger, moduleId, coreTasks.scalacPluginsTask)
          val semanticdbOptions =
            if scalaVersion.startsWith("3.") then
              scalacPlugins.map(p => s"-Xplugin:${p.toString}") ++
                Seq("-Xsemanticdb", s"-sourceroot", s"${DederGlobals.projectRootDir}")
            else
              Seq("-Yrangepos", s"-P:semanticdb:sourceroot:${DederGlobals.projectRootDir}") ++
                scalacPlugins.map(p => s"-Xplugin:${p.toString}")
          val finalScalacOptions = scalacOptions ++ semanticdbOptions
          val compileClasspath =
            executeTask(serverNotificationsLogger, moduleId, coreTasks.compileClasspathTask)
              .map(_.toNIO.toUri.toString)
              .toList
          val classesDir =
            executeTask(serverNotificationsLogger, moduleId, coreTasks.classesDirTask).toNIO.toUri.toString
          val scalacOptionsItem =
            ScalacOptionsItem(targetId, finalScalacOptions.asJava, compileClasspath.asJava, classesDir)
          List(scalacOptionsItem)
        }
      }
      ScalacOptionsResult(scalacOptionsItems.asJava)
    }

  override def buildTargetJvmCompileClasspath(
      params: JvmCompileClasspathParams
  ): CompletableFuture[JvmCompileClasspathResult] = CompletableFuture.supplyAsync { () =>
    val items = withLastGoodState { projectStateData =>
      val coreTasks = projectStateData.tasksRegistry.coreTasks
      val serverNotificationsLogger = makeServerNotificationsLogger()
      params.getTargets.asScala.map { targetId =>
        val moduleId = targetId.moduleId
        val compileClasspath =
          executeTask(serverNotificationsLogger, moduleId, coreTasks.compileClasspathTask)
            .map(_.toNIO.toUri.toString)
            .toList
        JvmCompileClasspathItem(targetId, compileClasspath.asJava)
      }
    }
    JvmCompileClasspathResult(items.asJava)
  }

  override def buildTargetJvmRunEnvironment(
      params: JvmRunEnvironmentParams
  ): CompletableFuture[JvmRunEnvironmentResult] = CompletableFuture.supplyAsync { () =>
    val items = withLastGoodState { projectStateData =>
      val coreTasks = projectStateData.tasksRegistry.coreTasks
      val serverNotificationsLogger = makeServerNotificationsLogger()
      params.getTargets.asScala.map { targetId =>
        val moduleId = targetId.moduleId
        val mainClasses = executeTask(serverNotificationsLogger, moduleId, coreTasks.mainClassesTask)
        val classpath =
          executeTask(serverNotificationsLogger, moduleId, coreTasks.runClasspathTask)
            .map(_.toNIO.toUri.toString)
            .toList
        val jvmOptions = List.empty[String] // TODO: Get JVM options
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
          // TODO what are the arguments???
          JvmMainClass(mainClass, List.empty.asJava)
        }
        item.setMainClasses(mainClassItems.asJava)
        item
      }
    }
    val res = JvmRunEnvironmentResult(items.asJava)
    logger.debug(s"BSP buildTargetJvmRunEnvironment called, returning: ${res}")
    res
  }

  override def buildTargetJvmTestEnvironment(
      params: JvmTestEnvironmentParams
  ): CompletableFuture[JvmTestEnvironmentResult] = CompletableFuture.supplyAsync { () =>
    val items = withLastGoodState { projectStateData =>
      val coreTasks = projectStateData.tasksRegistry.coreTasks
      val serverNotificationsLogger = makeServerNotificationsLogger()
      params.getTargets.asScala.map { targetId =>
        val moduleId = targetId.moduleId
        val testClasses = executeTask(serverNotificationsLogger, moduleId, coreTasks.testClassesTask)
        val classpath =
          executeTask(serverNotificationsLogger, moduleId, coreTasks.runClasspathTask)
            .map(_.toNIO.toUri.toString)
            .toList
        val jvmOptions = List.empty[String] // TODO: Get JVM options
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
            // TODO what are the arguments???
            JvmMainClass(testClass, List.empty.asJava)
          }
        }
        item.setMainClasses(testClassItems.asJava)
        item
      }
    }
    val res = JvmTestEnvironmentResult(items.asJava)
    logger.debug(s"BSP buildTargetJvmTestEnvironment called, returning: ${res}")
    res
  }

  override def buildTargetRun(params: RunParams): CompletableFuture[RunResult] = {
    // TODO
    logger.debug(s"BSP buildTargetRun called ${params}")
    CompletableFuture.failedFuture(new NotImplementedError("buildTargetRun is not supported in Deder BSP server"))
  }

  override def buildTargetTest(params: TestParams): CompletableFuture[TestResult] = {
    // TODO
    logger.debug(s"BSP buildTargetTest called ${params}")
    CompletableFuture.failedFuture(new NotImplementedError("buildTargetTest is not supported in Deder BSP server"))
  }

  override def debugSessionStart(params: DebugSessionParams): CompletableFuture[DebugSessionAddress] =
    CompletableFuture.failedFuture {
      new NotImplementedError("debugSessionStart is not supported in Deder BSP server")
    }

  override def onRunReadStdin(params: ReadParams): Unit = {
    // TODO
    logger.debug(s"BSP onRunReadStdin called ${params}")
    throw new NotImplementedError("buildTargetRun is not supported in Deder BSP server")
  }

  override def buildShutdown(): CompletableFuture[Object] = {
    // dont care, this is a long running server
    CompletableFuture.completedFuture(null.asInstanceOf[Object])
  }

  override def onBuildExit(): Unit =
    onExit() // just closes the unix socket connection

  private def buildTarget(module: DederModule, projectStateData: DederProjectStateData): BuildTarget = {
    val id = buildTargetId(module)
    val testModuleTypes = Set(ModuleType.SCALA_TEST)
    val isTestModule = testModuleTypes.contains(module.`type`)
    val isAppModule = module match {
      case m: DederProject.ScalaModule => m.mainClass != null
      case m: DederProject.JavaModule  => m.mainClass != null
      case _                           => false
    }
    val tags = List(
      List(BuildTargetTag.TEST).filter(_ => isTestModule),
      List(BuildTargetTag.APPLICATION).filter(_ => isAppModule),
      List(BuildTargetTag.LIBRARY).filter(_ => !isTestModule && !isAppModule)
    ).flatten
    val languageIds = module.`type` match {
      case ModuleType.SCALA | ModuleType.SCALA_TEST => List("scala", "java")
      case ModuleType.JAVA                          => List("java")
    }
    val dependencies = module.moduleDeps.asScala.map(buildTargetId)
    val capabilities = new BuildTargetCapabilities()
    capabilities.setCanCompile(true)
    capabilities.setCanRun(isAppModule)
    capabilities.setCanTest(isTestModule)
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
        jvmBuildTarget.setJavaHome(scala.util.Properties.javaHome) // TODO configurable?
        jvmBuildTarget.setJavaVersion(System.getProperty("java.version")) // TODO configurable?
        // scalaBuildTarget.setJvmBuildTarget(jvmBuildTarget)
        buildTarget.setData(scalaBuildTarget)
        buildTarget.setDataKind(BuildTargetDataKind.SCALA)
      case m: DederProject.JavaModule =>
        val jvmBuildTarget = new JvmBuildTarget()
        jvmBuildTarget.setJavaHome(scala.util.Properties.javaHome) // TODO configurable?
        jvmBuildTarget.setJavaVersion(System.getProperty("java.version")) // TODO configurable?
        buildTarget.setData(jvmBuildTarget)
        buildTarget.setDataKind(BuildTargetDataKind.JVM)
      case _ =>
    }
    buildTarget
  }

  private def buildTargetId(module: DederModule): BuildTargetIdentifier =
    BuildTargetIdentifier(
      DederPath(module.root).absPath.toNIO.toUri.toString + "#" + module.id
    )

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
