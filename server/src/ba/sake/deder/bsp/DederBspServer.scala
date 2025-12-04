package ba.sake.deder.bsp

import java.util.concurrent.*
import scala.jdk.CollectionConverters.*
import ch.epfl.scala.bsp4j.*
import org.eclipse.lsp4j.jsonrpc.Launcher
import ba.sake.deder.config.DederProject.DederModule
import ba.sake.deder.*
import ba.sake.deder.config.DederProject

// TODO refactor all to use task evaluation api
class DederBspServer(projectState: DederProjectState, onExit: () => Unit)
    extends BuildServer,
      JavaBuildServer,
      ScalaBuildServer {

  var client: BuildClient = null // set by DederBspProxyServer

  def buildInitialize(params: InitializeBuildParams): CompletableFuture[InitializeBuildResult] = {
    println(s"BSP buildInitialize called ${params}")
    val capabilities = new BuildServerCapabilities()
    capabilities.setResourcesProvider(true)
    capabilities.setCompileProvider(new CompileProvider(List("java", "scala").asJava))
    // capabilities.setTestProvider()
    // capabilities.setRunProvider()
    // capabilities.setDebugProvider()
    capabilities.setDependencySourcesProvider(false)
    capabilities.setDependencyModulesProvider(false)
    capabilities.setCanReload(true)
    capabilities.setBuildTargetChangedProvider(true)
    capabilities.setJvmCompileClasspathProvider(true)
    capabilities.setJvmRunEnvironmentProvider(true)
    capabilities.setJvmTestEnvironmentProvider(true)
    capabilities.setOutputPathsProvider(true)
    capabilities.setInverseSourcesProvider(false)

    // TODO capabilities.setTestProvider(new TestProvider(List("java", "scala").asJava))
    val result = new InitializeBuildResult(
      "deder-bsp",
      "0.0.1",
      "2.2.0-M2",
      capabilities
    )
    CompletableFuture.completedFuture(result)
  }

  def onBuildInitialized(): Unit = {
    // println(s"BSP onBuildInitialized called")
    // TODO maybe trigger compilation immediately?
  }

  def workspaceBuildTargets(): CompletableFuture[WorkspaceBuildTargetsResult] = {
    //println("BSP workspaceBuildTargets called")
    val buildTargets = projectState.lastGood match {
      case Left(errorMessage) =>
        client.onBuildLogMessage(new LogMessageParams(MessageType.ERROR, errorMessage))
        List.empty
      case Right(projectStateData) =>
        projectStateData.projectConfig.modules.asScala.map(m => buildTarget(m, projectStateData)).toList
    }
    val result = new WorkspaceBuildTargetsResult(buildTargets.asJava)
    CompletableFuture.completedFuture(result)
  }

  def buildTargetSources(params: SourcesParams): CompletableFuture[SourcesResult] = {
    println(s"BSP buildTargetSources called ${params}")
    val sourcesItems = params.getTargets.asScala.flatMap { targetId =>
      val moduleId = targetId.getUri.split("#").last
      projectState.lastGood match {
        case Left(errorMessage) =>
          println(s"Cannot provide sources for target $moduleId: $errorMessage")
          List.empty
        case Right(projectStateData) =>
          val module = projectStateData.tasksResolver.modulesMap(moduleId)
          module.sources.asScala.map { srcDir =>
            val srcDirPath = DederPath(s"${module.root}/${srcDir}").absPath
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
          }.toList
      }
    }
    // println(s"Returning sources items for ${params.getTargets()}: ${sourcesItems} ")
    CompletableFuture.completedFuture(new SourcesResult(sourcesItems.asJava))
  }

  def buildTargetResources(params: ResourcesParams): CompletableFuture[ResourcesResult] = {
    println(s"BSP buildTargetResources called ${params}")
    val resourcesItems = params.getTargets.asScala.map { targetId =>
      val moduleId = targetId.getUri.split("#").last
      projectState.lastGood match {
        case Left(value) =>
          println(s"Project in bad state: $value")
          ResourcesItem(targetId, List.empty.asJava)
        case Right(projectStateData) =>
          val module = projectStateData.tasksResolver.modulesMap(moduleId)
          val resourceDirs = module match {
            case m: DederProject.JavaModule  => m.resources.asScala
            case m: DederProject.ScalaModule => m.resources.asScala
            case _                           => List.empty
          }
          val resourceUrls = resourceDirs.flatMap { resourceDir =>
            val resourceDirPath = DederPath(s"${module.root}/${resourceDir}").absPath
            if os.exists(resourceDirPath) then
              os.walk(resourceDirPath).map { resourceFile =>
                resourceFile.toNIO.toUri.toString
              }
            else List.empty
          }
          ResourcesItem(targetId, resourceUrls.asJava)
      }
    }
    CompletableFuture.completedFuture(new ResourcesResult(resourcesItems.asJava))
  }

  def buildTargetCompile(params: CompileParams): CompletableFuture[CompileResult] = {
    println(s"BSP buildTargetCompile called ${params}")
    val targetIds = params.getTargets.asScala.map(_.getUri).toList
    targetIds.foreach { targetId =>
      println(s"Compiling target $targetId")
      projectState.execute(
        targetId.split("#").last,
        "compile",
        notification => {
          println(notification)
        }
      )
    }
    val compileResult = new CompileResult(StatusCode.OK)
    compileResult.setOriginId(params.getOriginId)
    CompletableFuture.completedFuture(compileResult)
  }

  def buildTargetCleanCache(params: CleanCacheParams): CompletableFuture[CleanCacheResult] = {
    println(s"BSP buildTargetCleanCache called ${params}")
    CompletableFuture.completedFuture(CleanCacheResult(true))
  }

  // sources of build target dependencies that are external to the workspace
  // hmm, so coursier source jars?
  def buildTargetDependencySources(params: DependencySourcesParams): CompletableFuture[DependencySourcesResult] = {
    println(s"BSP buildTargetDependencySources called ${params}")
    ???
  }

  def buildTargetDependencyModules(params: DependencyModulesParams): CompletableFuture[DependencyModulesResult] = {
    println(s"BSP buildTargetDependencyModules called ${params}")
    ???
  }

  def buildTargetInverseSources(params: InverseSourcesParams): CompletableFuture[InverseSourcesResult] = {
    println(s"BSP buildTargetInverseSources called ${params}")
    ???
  }

  def buildTargetOutputPaths(params: OutputPathsParams): CompletableFuture[OutputPathsResult] = {
    println(s"BSP buildTargetOutputPaths called ${params}")
    val excludedDirNames = Seq(".deder", ".bsp", ".metals", ".idea", ".vscode")
    val outputPathsItems = for {
      dirName <- excludedDirNames
      targetId <- params.getTargets.asScala
      outputPathItems = OutputPathItem(DederPath(dirName).absPath.toNIO.toUri.toString, OutputPathItemKind.DIRECTORY)
    } yield OutputPathsItem(targetId, List.empty.asJava)
    CompletableFuture.completedFuture(new OutputPathsResult(outputPathsItems.asJava))
  }

  def buildTargetJavacOptions(params: JavacOptionsParams): CompletableFuture[JavacOptionsResult] = {
    println(s"BSP buildTargetJavacOptions called ${params}")
    val javacOptionsItems = projectState.lastGood match {
      case Left(errorMessage) =>
        println(s"Cannot provide javac options: $errorMessage")
        List.empty
      case Right(projectStateData) =>
        val coreTasks = projectStateData.tasksRegistry.coreTasks
        params.getTargets().asScala.flatMap { targetId =>
          val moduleId = targetId.getUri.split("#").last
          val module = projectStateData.tasksResolver.modulesMap(moduleId)
          module match {
            case _: DederProject.JavaModule | _: DederProject.ScalaModule =>
              val javacOptions = projectState.executeTask(moduleId, coreTasks.javacOptionsTask, n => {})
              val compileClasspath = projectState
                .executeTask(moduleId, coreTasks.compileClasspathTask, n => {})
                .map { cpEntry => cpEntry.toNIO.toUri.toString }
                .toList
              val classesDir =
                projectState.executeTask(moduleId, coreTasks.classesDirTask, n => {}).toNIO.toUri.toString
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
    println(s"BSP buildTargetScalaMainClasses called ${params}")
    CompletableFuture.completedFuture(ScalaMainClassesResult(List.empty.asJava))
  }

  def buildTargetScalaTestClasses(params: ScalaTestClassesParams): CompletableFuture[ScalaTestClassesResult] = {
    println(s"BSP buildTargetScalaTestClasses called ${params}")
    CompletableFuture.completedFuture(ScalaTestClassesResult(List.empty.asJava))
  }

  def buildTargetScalacOptions(params: ScalacOptionsParams): CompletableFuture[ScalacOptionsResult] = {
    println(s"BSP buildTargetScalacOptions called ${params}")
    val scalacOptionsItems = projectState.lastGood match {
      case Left(errorMessage) =>
        println(s"Cannot provide scalac options: $errorMessage")
        List.empty
      case Right(projectStateData) =>
        val coreTasks = projectStateData.tasksRegistry.coreTasks
        params.getTargets().asScala.flatMap { targetId =>
          val moduleId = targetId.getUri.split("#").last
          val module = projectStateData.tasksResolver.modulesMap(moduleId)
          module match {
            case _: DederProject.JavaModule | _: DederProject.ScalaModule =>
              val scalacOptions = projectState.executeTask(moduleId, coreTasks.scalacOptionsTask, n => {})
              val compileClasspath = projectState
                .executeTask(moduleId, coreTasks.compileClasspathTask, n => {})
                .map { cpEntry => cpEntry.toNIO.toUri.toString }
                .toList
              val classesDir =
                projectState.executeTask(moduleId, coreTasks.classesDirTask, n => {}).toNIO.toUri.toString
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
    println(s"BSP buildTargetRun called ${params}")
    ???
  }

  def buildTargetTest(params: TestParams): CompletableFuture[TestResult] = {
    println(s"BSP buildTargetTest called ${params}")
    ???
  }

  def debugSessionStart(params: DebugSessionParams): CompletableFuture[DebugSessionAddress] = {
    println(s"BSP debugSessionStart called ${params}")
    ???
  }

  def workspaceReload(): CompletableFuture[Object] = {
    projectState.refreshProjectState()
    CompletableFuture.completedFuture(().asInstanceOf[Object])
  }

  def onRunReadStdin(params: ReadParams): Unit = {
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
    val tags = if (isTestModule) List(BuildTargetTag.TEST) else List(BuildTargetTag.LIBRARY)
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
        buildTarget.setDataKind(BuildTargetDataKind.JVM)
        val data = new JvmBuildTarget() // TODO set path & version
        buildTarget.setData(data)
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

}
