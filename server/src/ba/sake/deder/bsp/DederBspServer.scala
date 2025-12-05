package ba.sake.deder.bsp

import java.util.concurrent.*
import scala.jdk.CollectionConverters.*
import ch.epfl.scala.bsp4j.*
import org.eclipse.lsp4j.jsonrpc.Launcher
import ba.sake.deder.config.DederProject.DederModule
import ba.sake.deder.*
import ba.sake.deder.config.DederProject
import java.util.UUID
import ba.sake.deder.deps.DependencyResolver

class DederBspServer(projectState: DederProjectState, onExit: () => Unit)
    extends BuildServer,
      JavaBuildServer,
      ScalaBuildServer {

  var client: BuildClient = null // set by DederBspProxyServer

  def buildInitialize(params: InitializeBuildParams): CompletableFuture[InitializeBuildResult] = {
    // println(s"BSP buildInitialize called ${params}")
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

  def onBuildInitialized(): Unit = {
    // TODO maybe trigger compilation immediately?
  }

  def workspaceReload(): CompletableFuture[Object] = {
    projectState.refreshProjectState(m => client.onBuildShowMessage(new ShowMessageParams(MessageType.ERROR, m)))
    CompletableFuture.completedFuture(().asInstanceOf[Object])
  }

  def workspaceBuildTargets(): CompletableFuture[WorkspaceBuildTargetsResult] = {
    val buildTargets = projectState.lastGood match {
      case Left(errorMessage) =>
        List.empty
      case Right(projectStateData) =>
        projectStateData.projectConfig.modules.asScala.map(m => buildTarget(m, projectStateData)).toList
    }
    //println(s"BSP workspaceBuildTargets called, returning: ${buildTargets.map(_.getId.getUri)}")
    val result = new WorkspaceBuildTargetsResult(buildTargets.asJava)
    CompletableFuture.completedFuture(result)
  }

  def buildTargetSources(params: SourcesParams): CompletableFuture[SourcesResult] = {
    val sourcesItems = params.getTargets.asScala.flatMap { targetId =>
      val moduleId = targetId.moduleId
      projectState.lastGood match {
        case Left(errorMessage) =>
          List.empty
        case Right(projectStateData) =>
          val coreTasks = projectStateData.tasksRegistry.coreTasks
          val module = projectStateData.tasksResolver.modulesMap(moduleId)
          module match {
            case _: DederProject.JavaModule | _: DederProject.ScalaModule =>
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
            case _ => List.empty
          }
      }
    }
    /*println(
      s"BSP buildTargetSources called for ${params.getTargets.asScala.map(_.getUri)}," +
        s" returning: ${sourcesItems.toList}"
    )*/
    CompletableFuture.completedFuture(new SourcesResult(sourcesItems.asJava))
  }

  def buildTargetResources(params: ResourcesParams): CompletableFuture[ResourcesResult] = {
    val resourcesItems = params.getTargets.asScala.flatMap { targetId =>
      val moduleId = targetId.moduleId
      projectState.lastGood match {
        case Left(value) =>
          List.empty
        case Right(projectStateData) =>
          val coreTasks = projectStateData.tasksRegistry.coreTasks
          val module = projectStateData.tasksResolver.modulesMap(moduleId)
          module match {
            case _: DederProject.JavaModule | _: DederProject.ScalaModule =>
              val resourceDirs =
                projectState.executeTask(moduleId, coreTasks.resourcesTask, notifyClient, useLastGood = true)
              resourceDirs.map { srcDir =>
                val srcDirPath = srcDir.absPath
                val sourceItems =
                  if os.exists(srcDirPath) then
                    os.walk(srcDirPath).map { resourceFile =>
                      resourceFile.toNIO.toUri.toString
                    }
                  else List.empty
                ResourcesItem(targetId, sourceItems.asJava)
              }
            case _ => List.empty
          }
      }
    }
    CompletableFuture.completedFuture(new ResourcesResult(resourcesItems.asJava))
  }

  def buildTargetCompile(params: CompileParams): CompletableFuture[CompileResult] = {
    val taskId = Option(params.getOriginId).getOrElse(s"compile-${UUID.randomUUID}")
    val taskStartParams = TaskStartParams(TaskId(taskId))
    taskStartParams.setEventTime(System.currentTimeMillis())
    taskStartParams.setOriginId(params.getOriginId)
    taskStartParams.setMessage(s"Compiling modules: ${params.getTargets.asScala.map(_.moduleId).mkString(", ")}")
    client.onBuildTaskStart(taskStartParams)
    val targetIds = params.getTargets.asScala.map(_.getUri).toList
    var allCompileSucceeded = true
    targetIds.foreach { targetId =>
      projectState.lastGood match {
        case Left(errorMessage) =>
          allCompileSucceeded = false
        case Right(projectStateData) =>
          val coreTasks = projectStateData.tasksRegistry.coreTasks
          params.getTargets().asScala.foreach { targetId =>
            val moduleId = targetId.moduleId
            val module = projectStateData.tasksResolver.modulesMap(moduleId)
            try {
              projectState.executeTask(moduleId, coreTasks.compileTask, notifyClient, useLastGood = true)
            } catch {
              case e: TaskEvaluationException =>
                allCompileSucceeded = false
            }
          }
      }
    }
    val status = if allCompileSucceeded then StatusCode.OK else StatusCode.ERROR
    //println(s"BSP buildTargetCompile called for ${targetIds}, returning status: ${status}")
    val compileResult = new CompileResult(status)
    compileResult.setOriginId(params.getOriginId)
    val taskFinishParams = TaskFinishParams(TaskId(taskId), status)
    taskFinishParams.setEventTime(System.currentTimeMillis())
    taskFinishParams.setOriginId(params.getOriginId)
    taskFinishParams.setMessage(
      s"Finished compiling modules: ${params.getTargets.asScala.map(_.moduleId).mkString(", ")}"
    )
    client.onBuildTaskFinish(taskFinishParams)
    CompletableFuture.completedFuture(compileResult)
  }

  def buildTargetCleanCache(params: CleanCacheParams): CompletableFuture[CleanCacheResult] = {
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

  def buildTargetDependencyModules(params: DependencyModulesParams): CompletableFuture[DependencyModulesResult] = {
    // list of dependencies(maven)
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

  def buildTargetDependencySources(params: DependencySourcesParams): CompletableFuture[DependencySourcesResult] = {
    // TODO sources of build target dependencies that are external to the workspace
    // hmm, so coursier source jars?
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
          //println(s"Fetching sources for dependencies: ${depSources.map(_.toString).mkString(", ")}")
          val sourceArtifactFiles = DependencyResolver
            .fetch(depSources.toSeq)
            .getArtifacts()
            .asScala
            .map(_.getValue())
            .toSeq
            .filter(_.getName().endsWith("-sources.jar")) // coursier may return main artifact too..???
          DependencySourcesItem(targetId, sourceArtifactFiles.map(f => f.toURI.toString).asJava)
        }
    }
    CompletableFuture.completedFuture(DependencySourcesResult(items.asJava))
  }

  def buildTargetInverseSources(params: InverseSourcesParams): CompletableFuture[InverseSourcesResult] = {
    // TODO return if a file belongs to target(s), just peek in file path..
    CompletableFuture.completedFuture(InverseSourcesResult(List.empty.asJava))
  }

  def buildTargetOutputPaths(params: OutputPathsParams): CompletableFuture[OutputPathsResult] = {
    val excludedDirNames = Seq(".deder", ".bsp", ".metals", ".idea", ".vscode")
    val outputPathsItems = for {
      dirName <- excludedDirNames
      targetId <- params.getTargets.asScala
      outputPathItems = OutputPathItem(DederPath(dirName).absPath.toNIO.toUri.toString, OutputPathItemKind.DIRECTORY)
    } yield OutputPathsItem(targetId, List.empty.asJava)
    CompletableFuture.completedFuture(new OutputPathsResult(outputPathsItems.asJava))
  }

  def buildTargetJavacOptions(params: JavacOptionsParams): CompletableFuture[JavacOptionsResult] = {
    val javacOptionsItems = projectState.lastGood match {
      case Left(errorMessage) =>
        List.empty
      case Right(projectStateData) =>
        val coreTasks = projectStateData.tasksRegistry.coreTasks
        params.getTargets().asScala.flatMap { targetId =>
          val moduleId = targetId.moduleId
          val module = projectStateData.tasksResolver.modulesMap(moduleId)
          module match {
            case _: DederProject.JavaModule | _: DederProject.ScalaModule =>
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
            case _ =>
              List.empty
          }
        }
    }
    CompletableFuture.completedFuture(JavacOptionsResult(javacOptionsItems.asJava))
  }

  def buildTargetScalaMainClasses(params: ScalaMainClassesParams): CompletableFuture[ScalaMainClassesResult] = {
    val items = projectState.lastGood match {
      case Left(errorMessage) =>
        List.empty
      case Right(projectStateData) =>
        val coreTasks = projectStateData.tasksRegistry.coreTasks
        params.getTargets().asScala.map { targetId =>
          val moduleId = targetId.moduleId
          val module = projectStateData.tasksResolver.modulesMap(moduleId)
          module match {
            case _: DederProject.JavaModule | _: DederProject.ScalaModule =>
              val mainClass =
                projectState.executeTask(moduleId, coreTasks.mainClassTask, notifyClient, useLastGood = true)
              val item = ScalaMainClass(mainClass, List.empty.asJava, List.empty.asJava)
              // TODO arguments + JVM opts
              ScalaMainClassesItem(targetId, List(item).asJava)
          }
        }
    }
    val result = ScalaMainClassesResult(items.asJava)
    result.setOriginId(params.getOriginId)
    CompletableFuture.completedFuture(result)
  }

  def buildTargetScalaTestClasses(params: ScalaTestClassesParams): CompletableFuture[ScalaTestClassesResult] = {
    // TODO
    CompletableFuture.completedFuture(ScalaTestClassesResult(List.empty.asJava))
  }

  def buildTargetScalacOptions(params: ScalacOptionsParams): CompletableFuture[ScalacOptionsResult] = {
    val scalacOptionsItems = projectState.lastGood match {
      case Left(errorMessage) =>
        List.empty
      case Right(projectStateData) =>
        val coreTasks = projectStateData.tasksRegistry.coreTasks
        params.getTargets().asScala.flatMap { targetId =>
          val moduleId = targetId.moduleId
          val module = projectStateData.tasksResolver.modulesMap(moduleId)
          module match {
            case _: DederProject.JavaModule | _: DederProject.ScalaModule =>
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
            case _ =>
              List.empty
          }
        }
    }
    CompletableFuture.completedFuture(ScalacOptionsResult(scalacOptionsItems.asJava))
  }

  def buildTargetRun(params: RunParams): CompletableFuture[RunResult] = {
    // TODO
    println(s"BSP buildTargetRun called ${params}")
    CompletableFuture.completedFuture(RunResult(StatusCode.ERROR))
  }

  def buildTargetTest(params: TestParams): CompletableFuture[TestResult] = {
    // TODO
    println(s"BSP buildTargetTest called ${params}")
    CompletableFuture.completedFuture(TestResult(StatusCode.ERROR))
  }

  def debugSessionStart(params: DebugSessionParams): CompletableFuture[DebugSessionAddress] = {
    // TODO
    println(s"BSP debugSessionStart called ${params}")
    CompletableFuture.completedFuture(DebugSessionAddress("localhost:5005"))
  }

  def onRunReadStdin(params: ReadParams): Unit = {
    // TODO
    println(s"BSP onRunReadStdin called ${params}")
  }

  def buildShutdown(): CompletableFuture[Object] = {
    // dont care, this is a long running server
    CompletableFuture.completedFuture(null.asInstanceOf[Object])
  }

  def onBuildExit(): Unit =
    onExit() // just closes the unix socket connection

  private def buildTarget(module: DederModule, projectStateData: DederProjectStateData): BuildTarget = {
    val id = new BuildTargetIdentifier(buildTargetUri(module))
    val isTestModule = false
    // TODO if has mainClass then it's an app.. ?
    val tags = if (isTestModule) List(BuildTargetTag.TEST) else List(BuildTargetTag.APPLICATION)
    val languageIds = List(module.`type`.toString)
    val dependencies = module.moduleDeps.asScala.map { depModule =>
      new BuildTargetIdentifier(buildTargetUri(depModule))
    }
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

  private def buildTargetUri(module: DederModule): String =
    DederPath(module.root).absPath.toNIO.toUri.toString + "#" + module.id

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
