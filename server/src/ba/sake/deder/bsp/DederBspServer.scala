package ba.sake.deder.bsp

import java.util.concurrent._
import ch.epfl.scala.bsp4j._
import org.eclipse.lsp4j.jsonrpc.Launcher
import ba.sake.deder.DederProjectState

class DederBspServer(projectState: DederProjectState) extends BuildServer {
  println("DederBspServer created")
  var client: BuildClient = null // will be updated later
  def buildInitialize(params: InitializeBuildParams): CompletableFuture[InitializeBuildResult] = {
    println(s"BSP buildInitialize called ${params}")
    ???
  }
  def buildShutdown(): CompletableFuture[Object] = ???
  def buildTargetCleanCache(params: CleanCacheParams): CompletableFuture[CleanCacheResult] = ???
  def buildTargetCompile(params: CompileParams): CompletableFuture[CompileResult] = ???
  def buildTargetDependencySources(params: DependencySourcesParams): CompletableFuture[DependencySourcesResult] = ???
  def buildTargetDependencyModules(params: DependencyModulesParams): CompletableFuture[DependencyModulesResult] = ???
  def buildTargetInverseSources(params: InverseSourcesParams): CompletableFuture[InverseSourcesResult] = ???
  def buildTargetResources(params: ResourcesParams): CompletableFuture[ResourcesResult] = ???
  def buildTargetOutputPaths(params: OutputPathsParams): CompletableFuture[OutputPathsResult] = ???
  def buildTargetRun(params: RunParams): CompletableFuture[RunResult] = ???
  def buildTargetSources(params: SourcesParams): CompletableFuture[SourcesResult] = ???
  def buildTargetTest(params: TestParams): CompletableFuture[TestResult] = ???
  def debugSessionStart(params: DebugSessionParams): CompletableFuture[DebugSessionAddress] = ???
  def onBuildExit(): Unit = ???
  def onBuildInitialized(): Unit = ???
  def workspaceBuildTargets(): CompletableFuture[WorkspaceBuildTargetsResult] = ???
  def workspaceReload(): CompletableFuture[Object] = ???
  def onRunReadStdin(params: ReadParams): Unit = ???
}
