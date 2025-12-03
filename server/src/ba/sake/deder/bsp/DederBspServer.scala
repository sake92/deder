package ba.sake.deder.bsp

import java.util.concurrent.*
import scala.jdk.CollectionConverters.*
import ch.epfl.scala.bsp4j.*
import org.eclipse.lsp4j.jsonrpc.Launcher
import ba.sake.deder.config.DederProject.DederModule
import ba.sake.deder.*
import ba.sake.deder.config.DederProject

class DederBspServer(projectState: DederProjectState, onExit: () => Unit) extends BuildServer {

  var client: BuildClient = null // set by DederBspProxyServer

  def buildInitialize(params: InitializeBuildParams): CompletableFuture[InitializeBuildResult] = {
    println(s"BSP buildInitialize called ${params}")
    val capabilities = new BuildServerCapabilities()
    capabilities.setCanReload(true)
    capabilities.setBuildTargetChangedProvider(true)
    capabilities.setJvmCompileClasspathProvider(true)
    capabilities.setCompileProvider(new CompileProvider(List("java", "scala").asJava))
    capabilities.setResourcesProvider(true)
    capabilities.setOutputPathsProvider(true)
    capabilities.setJvmRunEnvironmentProvider(true)
    // capabilities.setRunProvider()
    capabilities.setJvmTestEnvironmentProvider(true)
    // capabilities.setTestProvider()

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
    println(s"BSP onBuildInitialized called")
  }

  def workspaceBuildTargets(): CompletableFuture[WorkspaceBuildTargetsResult] = {
    println("BSP workspaceBuildTargets called")
    val buildTargets = projectState.projectStateData match {
      case Left(errorMessage) =>
        println(s"Cannot provide build targets: $errorMessage")
        List.empty
      case Right(projectStateData) =>
        projectStateData.projectConfig.modules.asScala.map(m => buildTarget(m, projectStateData)).toList
    }
    // println(s"Returning build targets: ${buildTargets} ")
    val result = new WorkspaceBuildTargetsResult(buildTargets.asJava)
    CompletableFuture.completedFuture(result)
  }

  def buildTargetSources(params: SourcesParams): CompletableFuture[SourcesResult] = {
    println(s"BSP buildTargetSources called ${params}")
    val sourcesItems = params.getTargets.asScala.flatMap { targetId =>
      val moduleId = targetId.getUri.split("#").last
      projectState.projectStateData match {
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
      projectState.projectStateData match {
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

  def buildShutdown(): CompletableFuture[Object] = {
    println(s"BSP buildShutdown called")
    CompletableFuture.completedFuture(null.asInstanceOf[Object])
  }

  def onBuildExit(): Unit = {
    println(s"BSP onBuildExit called")
    onExit()
  }

  def workspaceReload(): CompletableFuture[Object] = {
    println(s"BSP workspaceReload called")
    CompletableFuture.completedFuture(().asInstanceOf[Object])
  }

  def onRunReadStdin(params: ReadParams): Unit = {
    println(s"BSP onRunReadStdin called ${params}")
  }

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
