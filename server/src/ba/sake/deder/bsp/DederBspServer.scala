package ba.sake.deder.bsp

import java.util.UUID
import java.util.concurrent.*
import scala.jdk.CollectionConverters.*
import ch.epfl.scala.bsp4j.*
import org.eclipse.lsp4j.jsonrpc.Launcher
import ba.sake.deder.config.DederProject.DederModule
import ba.sake.deder.*
import ba.sake.deder.config.DederProject
import ba.sake.deder.deps.DependencyResolver

class DederBspServer(projectState: DederProjectState, onExit: () => Unit)
    extends BuildServer,
      JvmBuildServer,
      JavaBuildServer,
      ScalaBuildServer {

  var client: BuildClient = null // set by DederBspProxyServer

  override def buildInitialize(params: InitializeBuildParams): CompletableFuture[InitializeBuildResult] = {
    println(s"BSP client connected: ${params}")
    val supportedLanguages = List("java", "scala")
    val capabilities = new BuildServerCapabilities()
    capabilities.setResourcesProvider(true)
    capabilities.setCompileProvider(new CompileProvider(supportedLanguages.asJava))
    capabilities.setRunProvider(new RunProvider(supportedLanguages.asJava))
    capabilities.setTestProvider(new TestProvider(supportedLanguages.asJava))
    capabilities.setDebugProvider(new DebugProvider(supportedLanguages.asJava))
    capabilities.setCanReload(true)
    capabilities.setBuildTargetChangedProvider(true)
    capabilities.setJvmCompileClasspathProvider(true)
    capabilities.setJvmRunEnvironmentProvider(true)
    capabilities.setJvmTestEnvironmentProvider(true)
    capabilities.setOutputPathsProvider(true)
    capabilities.setDependencySourcesProvider(true)
    capabilities.setDependencyModulesProvider(true)
    capabilities.setInverseSourcesProvider(true)

    val result = new InitializeBuildResult(
      "deder-bsp",
      "0.0.1",
      "2.2.0-M2",
      capabilities
    )
    CompletableFuture.completedFuture(result)
  }

  override def onBuildInitialized(): Unit = {
    // TODO maybe trigger compilation immediately?
  }

  override def workspaceReload(): CompletableFuture[Object] = {
    projectState.refreshProjectState(m => client.onBuildShowMessage(new ShowMessageParams(MessageType.ERROR, m)))
    CompletableFuture.completedFuture(().asInstanceOf[Object])
  }

  override def workspaceBuildTargets(): CompletableFuture[WorkspaceBuildTargetsResult] = {
    val buildTargets = projectState.lastGood match {
      case Left(errorMessage) =>
        List.empty
      case Right(projectStateData) =>
        projectStateData.projectConfig.modules.asScala.map(m => buildTarget(m, projectStateData)).toList
    }
    // println(s"BSP workspaceBuildTargets called, returning: ${buildTargets.map(_.getId.getUri)}")
    val result = new WorkspaceBuildTargetsResult(buildTargets.asJava)
    CompletableFuture.completedFuture(result)
  }

  override def buildTargetSources(params: SourcesParams): CompletableFuture[SourcesResult] = {
    val sourcesItems = params.getTargets.asScala.flatMap { targetId =>
      val moduleId = targetId.moduleId
      projectState.lastGood match {
        case Left(errorMessage) =>
          List.empty
        case Right(projectStateData) =>
          val coreTasks = projectStateData.tasksRegistry.coreTasks
          val sourceDirs =
            projectState.executeTask(moduleId, coreTasks.sourcesTask, notifyClient, useLastGood = true)
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
    /*println(
      s"BSP buildTargetSources called for ${params.getTargets.asScala.map(_.getUri)}," +
        s" returning: ${sourcesItems.toList}"
    )*/
    CompletableFuture.completedFuture(new SourcesResult(sourcesItems.asJava))
  }

  override def buildTargetInverseSources(params: InverseSourcesParams): CompletableFuture[InverseSourcesResult] = {
    val targetIds = projectState.lastGood match {
      case Left(errorMessage) =>
        List.empty
      case Right(projectStateData) =>
        val coreTasks = projectStateData.tasksRegistry.coreTasks
        val modules = projectStateData.tasksResolver.allModules.filter { m =>
          val sourceDirs =
            projectState.executeTask(m.id, coreTasks.sourcesTask, notifyClient, useLastGood = true)
          sourceDirs.exists { srcDir =>
            val srcDirUri = srcDir.absPath.toURI.toString()
            srcDirUri.startsWith(params.getTextDocument().getUri())
          }
        }
        modules.map(buildTargetId)
    }
    CompletableFuture.completedFuture(InverseSourcesResult(targetIds.asJava))
  }

  override def buildTargetResources(params: ResourcesParams): CompletableFuture[ResourcesResult] = {
    val resourcesItems = params.getTargets.asScala.flatMap { targetId =>
      val moduleId = targetId.moduleId
      projectState.lastGood match {
        case Left(value) =>
          List.empty
        case Right(projectStateData) =>
          val coreTasks = projectStateData.tasksRegistry.coreTasks
          val resourceDirs =
            projectState.executeTask(moduleId, coreTasks.resourcesTask, notifyClient, useLastGood = true)
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
    CompletableFuture.completedFuture(new ResourcesResult(resourcesItems.asJava))
  }

  override def buildTargetCompile(params: CompileParams): CompletableFuture[CompileResult] = {
    var allCompileSucceeded = true
    projectState.lastGood match {
      case Left(errorMessage) =>
        allCompileSucceeded = false
      case Right(projectStateData) =>
        val coreTasks = projectStateData.tasksRegistry.coreTasks
        params.getTargets().asScala.foreach { targetId =>
          var currentModuleCompileSucceeded = true
          val moduleId = targetId.moduleId
          val taskId = TaskId(s"compile-${moduleId}-${UUID.randomUUID}")
          val taskStartParams = TaskStartParams(taskId)
          taskStartParams.setEventTime(System.currentTimeMillis())
          taskStartParams.setOriginId(params.getOriginId)
          taskStartParams.setMessage(s"Compiling ${moduleId} ...")
          taskStartParams.setDataKind(TaskStartDataKind.COMPILE_TASK)
          taskStartParams.setData(new CompileTask(targetId))
          client.onBuildTaskStart(taskStartParams)
          val module = projectStateData.tasksResolver.modulesMap(moduleId)
          try {
            projectState.executeTask(moduleId, coreTasks.compileTask, notifyClient, useLastGood = true)
          } catch {
            case e: TaskEvaluationException =>
              currentModuleCompileSucceeded = false
              allCompileSucceeded = false
          } finally {
            val status = if currentModuleCompileSucceeded then StatusCode.OK else StatusCode.ERROR
            val taskFinishParams = TaskFinishParams(taskId, status)
            taskFinishParams.setEventTime(System.currentTimeMillis())
            taskFinishParams.setOriginId(params.getOriginId)
            taskFinishParams.setMessage(s"Finished compiling ${moduleId}")
            taskFinishParams.setDataKind(TaskFinishDataKind.COMPILE_REPORT)
            taskFinishParams.setData(new CompileReport(targetId, 0, 0)) // TODO warnings/errors count
            client.onBuildTaskFinish(taskFinishParams)
          }
        }
    }

    val status = if allCompileSucceeded then StatusCode.OK else StatusCode.ERROR
    // println(s"BSP buildTargetCompile called for ${targetIds}, returning status: ${status}")
    val compileResult = new CompileResult(status)
    compileResult.setOriginId(params.getOriginId)
    CompletableFuture.completedFuture(compileResult)
  }

  override def buildTargetCleanCache(params: CleanCacheParams): CompletableFuture[CleanCacheResult] = {
    projectState.lastGood match {
      case Left(errorMessage) =>
        CompletableFuture.completedFuture(CleanCacheResult(false))
      case Right(projectStateData) =>
        val coreTasks = projectStateData.tasksRegistry.coreTasks
        params.getTargets.asScala.foreach { targetId =>
          val moduleId = targetId.moduleId
          val module = projectStateData.tasksResolver.modulesMap(moduleId)
          val classesDir =
            projectState.executeTask(moduleId, coreTasks.classesDirTask, notifyClient, useLastGood = true)
          os.remove.all(classesDir, ignoreErrors = true)
        }
        CompletableFuture.completedFuture(CleanCacheResult(true))
    }
  }

  // list of dependencies(maven)
  override def buildTargetDependencyModules(
      params: DependencyModulesParams
  ): CompletableFuture[DependencyModulesResult] = {
    val items = projectState.lastGood match {
      case Left(errorMessage) =>
        List.empty
      case Right(projectStateData) =>
        val coreTasks = projectStateData.tasksRegistry.coreTasks
        params.getTargets.asScala.map { targetId =>
          val moduleId = targetId.moduleId
          val module = projectStateData.tasksResolver.modulesMap(moduleId)
          val fetchRes =
            projectState.executeTask(moduleId, coreTasks.dependenciesTask, notifyClient, useLastGood = true)

          // assuming that dependencies and artifacts are in the same order, 1:1 mapping
          val depsWithArtifacts = fetchRes.getDependencies.asScala
            .zip(fetchRes.getArtifacts.asScala)
            .map { case (dep, entry) => (dep, entry.getKey, entry.getValue) }
          val depItems = depsWithArtifacts.map { case (dep, artifact, file) =>
            val mavenDependencyModuleArtifact = MavenDependencyModuleArtifact(file.toURI.toString())
            if dep.getPublication != null then
              mavenDependencyModuleArtifact.setClassifier(dep.getPublication.getClassifier)
            val mavenDependencyModule = MavenDependencyModule(
              dep.getModule().getOrganization(),
              dep.getModule().getName(),
              dep.getVersion(),
              List(mavenDependencyModuleArtifact).asJava
            )
            val depModule = DependencyModule(dep.getModule().getName(), dep.getVersion())
            depModule.setDataKind(DependencyModuleDataKind.MAVEN)
            depModule.setData(mavenDependencyModule)
            depModule
          }
          DependencyModulesItem(targetId, depItems.asJava)
        }
    }
    /*println(
      s"BSP buildTargetDependencyModules called for ${params.getTargets.asScala.map(_.getUri)}," +
        s" returning: ${items.toList}"
    )*/
    CompletableFuture.completedFuture(DependencyModulesResult(items.asJava))
  }

  // source jars of dependencies
  override def buildTargetDependencySources(
      params: DependencySourcesParams
  ): CompletableFuture[DependencySourcesResult] = {
    val items = projectState.lastGood match {
      case Left(errorMessage) =>
        List.empty
      case Right(projectStateData) =>
        val coreTasks = projectStateData.tasksRegistry.coreTasks
        params.getTargets.asScala.map { targetId =>
          val moduleId = targetId.moduleId
          val module = projectStateData.tasksResolver.modulesMap(moduleId)
          val fetchRes =
            projectState.executeTask(moduleId, coreTasks.dependenciesTask, notifyClient, useLastGood = true)
          val depSources = fetchRes.getDependencies().asScala.map { dep =>
            dep.withClassifier("sources").withType("jar").withConfiguration("sources")
          }
          // println(s"Fetching sources for dependencies: ${depSources.map(_.toString).mkString(", ")}")
          val sourceArtifactFiles = DependencyResolver
            .fetch(depSources.toSeq)
            .getArtifacts()
            .asScala
            .map(_.getValue())
            .toSeq
            .filter(_.getName().endsWith("-sources.jar")) // TODO coursier returns main artifact too..???
          DependencySourcesItem(targetId, sourceArtifactFiles.map(f => f.toURI.toString).asJava)
        }
    }
    CompletableFuture.completedFuture(DependencySourcesResult(items.asJava))
  }

  // hidden/ignored/output dirs
  override def buildTargetOutputPaths(params: OutputPathsParams): CompletableFuture[OutputPathsResult] = {
    val excludedDirNames = Seq(".deder", ".bsp", ".metals", ".idea", ".vscode")
    val outputPathsItems = for {
      dirName <- excludedDirNames
      targetId <- params.getTargets.asScala
      outputPathItems = OutputPathItem(DederPath(dirName).absPath.toNIO.toUri.toString, OutputPathItemKind.DIRECTORY)
    } yield OutputPathsItem(targetId, List.empty.asJava)
    CompletableFuture.completedFuture(new OutputPathsResult(outputPathsItems.asJava))
  }

  override def buildTargetJavacOptions(params: JavacOptionsParams): CompletableFuture[JavacOptionsResult] = {
    val javacOptionsItems = projectState.lastGood match {
      case Left(errorMessage) =>
        List.empty
      case Right(projectStateData) =>
        val coreTasks = projectStateData.tasksRegistry.coreTasks
        params.getTargets().asScala.flatMap { targetId =>
          val moduleId = targetId.moduleId
          val javacOptions =
            projectState.executeTask(moduleId, coreTasks.javacOptionsTask, notifyClient, useLastGood = true)
          val compileClasspath = projectState
            .executeTask(moduleId, coreTasks.compileClasspathTask, notifyClient, useLastGood = true)
            .map { cpEntry => cpEntry.toNIO.toUri.toString }
            .toList
          val classesDir =
            projectState
              .executeTask(moduleId, coreTasks.classesDirTask, notifyClient, useLastGood = true)
              .toNIO
              .toUri
              .toString
          val javacOptionsItem =
            JavacOptionsItem(targetId, javacOptions.asJava, compileClasspath.asJava, classesDir)
          List(javacOptionsItem)
        }
    }
    CompletableFuture.completedFuture(JavacOptionsResult(javacOptionsItems.asJava))
  }

  override def buildTargetScalaMainClasses(
      params: ScalaMainClassesParams
  ): CompletableFuture[ScalaMainClassesResult] = {
    // TODO discover main classes properly
    val items = projectState.lastGood match {
      case Left(errorMessage) =>
        List.empty
      case Right(projectStateData) =>
        val coreTasks = projectStateData.tasksRegistry.coreTasks
        params.getTargets().asScala.map { targetId =>
          val moduleId = targetId.moduleId
          val mainClass =
            projectState.executeTask(moduleId, coreTasks.mainClassTask, notifyClient, useLastGood = true)
          val item = ScalaMainClass(mainClass, List.empty.asJava, List.empty.asJava)
          // TODO arguments + JVM opts
          ScalaMainClassesItem(targetId, List(item).asJava)
        }
    }
    val result = ScalaMainClassesResult(items.asJava)
    result.setOriginId(params.getOriginId)
    CompletableFuture.completedFuture(result)
  }

  override def buildTargetScalaTestClasses(
      params: ScalaTestClassesParams
  ): CompletableFuture[ScalaTestClassesResult] = {
    // TODO
    CompletableFuture.completedFuture(ScalaTestClassesResult(List.empty.asJava))
  }

  override def buildTargetScalacOptions(params: ScalacOptionsParams): CompletableFuture[ScalacOptionsResult] = {
    val scalacOptionsItems = projectState.lastGood match {
      case Left(errorMessage) =>
        List.empty
      case Right(projectStateData) =>
        val coreTasks = projectStateData.tasksRegistry.coreTasks
        params.getTargets().asScala.flatMap { targetId =>
          val moduleId = targetId.moduleId
          val scalacOptions =
            projectState.executeTask(moduleId, coreTasks.scalacOptionsTask, notifyClient, useLastGood = true)
          val compileClasspath = projectState
            .executeTask(moduleId, coreTasks.compileClasspathTask, notifyClient, useLastGood = true)
            .map { cpEntry => cpEntry.toNIO.toUri.toString }
            .toList
          val classesDir =
            projectState
              .executeTask(moduleId, coreTasks.classesDirTask, notifyClient, useLastGood = true)
              .toNIO
              .toUri
              .toString
          val scalacOptionsItem =
            ScalacOptionsItem(targetId, scalacOptions.asJava, compileClasspath.asJava, classesDir)
          List(scalacOptionsItem)
        }
    }
    CompletableFuture.completedFuture(ScalacOptionsResult(scalacOptionsItems.asJava))
  }

  override def buildTargetJvmCompileClasspath(
      params: JvmCompileClasspathParams
  ): CompletableFuture[JvmCompileClasspathResult] = {
    // TODO
    CompletableFuture.completedFuture(JvmCompileClasspathResult(List.empty.asJava))
  }

  override def buildTargetJvmRunEnvironment(
      params: JvmRunEnvironmentParams
  ): CompletableFuture[JvmRunEnvironmentResult] = {
    val items = projectState.lastGood match {
      case Left(errorMessage) =>
        List.empty
      case Right(projectStateData) =>
        val coreTasks = projectStateData.tasksRegistry.coreTasks
        params.getTargets().asScala.map { targetId =>
          val moduleId = targetId.moduleId
          val mainClass =
            projectState.executeTask(moduleId, coreTasks.mainClassTask, notifyClient, useLastGood = true)
          val classpath = projectState
            .executeTask(moduleId, coreTasks.runClasspathTask, notifyClient, useLastGood = true)
            .map { cpEntry => cpEntry.toNIO.toUri.toString }
            .toList
          val jvmOptions = List.empty[String] // TODO: Get JVM options
          val workingDirectory = DederGlobals.projectRootDir.toNIO.toUri.toString
          val environmentVariables = Map.empty[String, String] // TODO: Get environment variables
          JvmEnvironmentItem(
            targetId,
            classpath.asJava,
            jvmOptions.asJava,
            workingDirectory,
            environmentVariables.asJava
          )
        }
    }
    CompletableFuture.completedFuture(JvmRunEnvironmentResult(items.asJava))
  }

  override def buildTargetJvmTestEnvironment(
      params: JvmTestEnvironmentParams
  ): CompletableFuture[JvmTestEnvironmentResult] = {
    // TODO
    CompletableFuture.completedFuture(JvmTestEnvironmentResult(List.empty.asJava))
  }

  override def buildTargetRun(params: RunParams): CompletableFuture[RunResult] = {
    // TODO
    println(s"BSP buildTargetRun called ${params}")
    CompletableFuture.completedFuture(RunResult(StatusCode.ERROR))
  }

  override def buildTargetTest(params: TestParams): CompletableFuture[TestResult] = {
    // TODO
    println(s"BSP buildTargetTest called ${params}")
    CompletableFuture.completedFuture(TestResult(StatusCode.ERROR))
  }

  override def debugSessionStart(params: DebugSessionParams): CompletableFuture[DebugSessionAddress] = {
    // TODO https://github.com/scalacenter/scala-debug-adapter
    println(s"BSP debugSessionStart called ${params}")
    CompletableFuture.completedFuture(DebugSessionAddress("localhost:5005"))
  }

  override def onRunReadStdin(params: ReadParams): Unit = {
    // TODO
    println(s"BSP onRunReadStdin called ${params}")
  }

  override def buildShutdown(): CompletableFuture[Object] = {
    // dont care, this is a long running server
    CompletableFuture.completedFuture(null.asInstanceOf[Object])
  }

  override def onBuildExit(): Unit =
    onExit() // just closes the unix socket connection

  private def buildTarget(module: DederModule, projectStateData: DederProjectStateData): BuildTarget = {
    val id = buildTargetId(module)
    val isTestModule = false
    // TODO if has mainClass then it's an app.. ?
    val tags = if (isTestModule) List(BuildTargetTag.TEST) else List(BuildTargetTag.APPLICATION)
    val languageIds = List(module.`type`.toString)
    val dependencies = module.moduleDeps.asScala.map(buildTargetId)
    val capabilities = new BuildTargetCapabilities()
    capabilities.setCanCompile(true)
    capabilities.setCanRun(true)
    capabilities.setCanTest(true)
    capabilities.setCanDebug(true)
    val buildTarget = new BuildTarget(id, tags.asJava, languageIds.asJava, dependencies.asJava, capabilities)
    buildTarget.setDisplayName(module.id)
    buildTarget.setBaseDirectory(DederPath(module.root).absPath.toNIO.toUri.toString)
    module match {
      case m: DederProject.JavaModule =>
        val data = new JvmBuildTarget() // TODO set path & version
        buildTarget.setData(data)
        buildTarget.setDataKind(BuildTargetDataKind.JVM)
      case m: DederProject.ScalaModule =>
        val binaryVersion = m.scalaVersion.split("\\.").take(2).mkString(".") // TODO extract with coursier
        // TODO val scalaJars = List("scala-compiler.jar", "scala-reflect.jar", "scala-library.jar").asJava
        val data =
          new ScalaBuildTarget("org.scala-lang", m.scalaVersion, binaryVersion, ScalaPlatform.JVM, List.empty.asJava)
        buildTarget.setData(data)
        buildTarget.setDataKind(BuildTargetDataKind.SCALA)
      case _ =>
    }
    buildTarget
  }

  private def buildTargetId(module: DederModule): BuildTargetIdentifier =
    BuildTargetIdentifier(
      DederPath(module.root).absPath.toNIO.toUri.toString + "#" + module.id
    )

  private val notifyClient = (n: ServerNotification) =>
    n match {
      case n: ServerNotification.Log =>
        // dont spam the client with debug/trace messages..
        if n.level.ordinal <= ServerNotification.Level.INFO.ordinal then client.onBuildLogMessage(toBspLogMessage(n))
      case n: ServerNotification.RequestFinished =>
      // do nothing
      case _ => // ignore other notifications for now
    }

  private def toBspLogMessage(n: ServerNotification.Log): LogMessageParams = {
    val level = n.level match {
      case ServerNotification.Level.ERROR   => MessageType.ERROR
      case ServerNotification.Level.WARNING => MessageType.WARNING
      case ServerNotification.Level.INFO    => MessageType.INFO
      case ServerNotification.Level.DEBUG   => MessageType.LOG
      case ServerNotification.Level.TRACE   => MessageType.LOG
    }
    new LogMessageParams(level, n.message)
  }

  extension (id: BuildTargetIdentifier) {
    def moduleId: String = id.getUri.split("#").last
  }
}
