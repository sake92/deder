package ba.sake.deder

import ba.sake.deder.deps.{DepTree, Dependency}
import ba.sake.deder.graalvm.GraalVmNativeImageTasks
import ba.sake.deder.jvm.ManifestEntries
import ba.sake.deder.publish.{PublishTasks, PomSettings, PublishArtifactsRes}
import ba.sake.deder.scalajs.ScalaJsTasks
import ba.sake.deder.scalanative.ScalaNativeTasks
import ba.sake.deder.testing.{DederTestResults, DiscoveredFrameworkTests}

/** Adapts the internal [[CoreTasks]] to the public [[CoreTasksApi]] interface.
 *  This keeps [[CoreTasks]] free of the [[CoreTasksApi]] inheritance (which
 *  would cause Scala to widen task-val types to `AbstractTask[T]`).
 */
class CoreTasksApiAdapter(
    coreTasks: CoreTasks,
    runTasks: RunTasks,
    publishTasks: PublishTasks,
    graalvmTasks: GraalVmNativeImageTasks
) extends CoreTasksApi {
  def sourcesTask: AbstractTask[Seq[DederPath]] = coreTasks.sourcesTask
  def sourceFilesTask: AbstractTask[Seq[DederPath]] = coreTasks.sourceFilesTask
  def generatedSourcesTask: AbstractTask[DederPath] = coreTasks.generatedSourcesTask
  def resourcesTask: AbstractTask[Seq[DederPath]] = coreTasks.resourcesTask
  def javaHomeTask: AbstractTask[Option[os.Path]] = coreTasks.javaHomeTask
  def javaVersionTask: AbstractTask[Option[String]] = coreTasks.javaVersionTask
  def javacOptionsTask: AbstractTask[Seq[String]] = coreTasks.javacOptionsTask
  def scalacOptionsTask: AbstractTask[Seq[String]] = coreTasks.scalacOptionsTask
  def scalaVersionTask: AbstractTask[String] = coreTasks.scalaVersionTask
  def depsTask: AbstractTask[Seq[String]] = coreTasks.depsTask
  def repositoriesTask: AbstractTask[Seq[String]] = coreTasks.repositoriesTask
  def compileOnlyDepsTask: AbstractTask[Seq[String]] = coreTasks.compileOnlyDepsTask
  def depsTreeTask: AbstractTask[DepTree] = coreTasks.depsTreeTask
  def classesTask: AbstractTask[DederPath] = coreTasks.classesTask
  def semanticdbDirTask: AbstractTask[DederPath] = coreTasks.semanticdbDirTask
  def compileTask: AbstractTask[DederPath] = coreTasks.compileTask
  def compileClasspathTask: AbstractTask[Seq[os.Path]] = coreTasks.compileClasspathTask
  def javaSemanticdbVersionTask: AbstractTask[String] = coreTasks.javaSemanticdbVersionTask
  def scalaSemanticdbVersionTask: AbstractTask[String] = coreTasks.scalaSemanticdbVersionTask
  def semanticdbEnabledTask: AbstractTask[Boolean] = coreTasks.semanticdbEnabledTask
  def javacAnnotationProcessorDepsTask: AbstractTask[Seq[String]] = coreTasks.javacAnnotationProcessorDepsTask
  def javacAnnotationProcessorsTask: AbstractTask[Seq[os.Path]] = coreTasks.javacAnnotationProcessorsTask
  def scalacPluginDepsTask: AbstractTask[Seq[String]] = coreTasks.scalacPluginDepsTask
  def scalacPluginsTask: AbstractTask[Seq[os.Path]] = coreTasks.scalacPluginsTask
  def compilerDepsTask: AbstractTask[Seq[Dependency]] = coreTasks.compilerDepsTask
  def jvmOptionsTask: AbstractTask[Seq[String]] = coreTasks.jvmOptionsTask
  def runClasspathTask: AbstractTask[Seq[os.Path]] = coreTasks.runClasspathTask
  def mainClassesTask: AbstractTask[Seq[String]] = coreTasks.mainClassesTask
  def mainClassTask: AbstractTask[Option[String]] = coreTasks.mainClassTask
  def finalMainClassTask: AbstractTask[Option[String]] = coreTasks.finalMainClassTask
  def replDepsTask: AbstractTask[Seq[Dependency]] = runTasks.replDepsTask
  def replJarsTask: AbstractTask[Seq[os.Path]] = runTasks.replJarsTask

  // Run/REPL entrypoints
  def runTask: AbstractTask[Seq[String]] = runTasks.runTask
  def runMainTask: AbstractTask[Seq[String]] = runTasks.runMainTask
  def runMvnAppTask: AbstractTask[Seq[String]] = runTasks.runMvnAppTask
  def replTask: AbstractTask[Seq[String]] = runTasks.replTask

  // Test
  def testTask: AbstractTask[DederTestResults] = coreTasks.testTask
  def testInMemoryTask: AbstractTask[DederTestResults] = coreTasks.testInMemoryTask
  def testClassesTask: AbstractTask[Seq[DiscoveredFrameworkTests]] = coreTasks.testClassesTask

  // Fix
  def fixTask: AbstractTask[Seq[String]] = coreTasks.fixTask
  def fixCheckTask: AbstractTask[Seq[String]] = coreTasks.fixCheckTask

  // Publishing
  def versionTask: AbstractTask[String] = publishTasks.versionTask
  def manifestTask: AbstractTask[ManifestEntries] = publishTasks.manifestSettingsTask
  def pomSettingsTask: AbstractTask[Option[PomSettings]] = publishTasks.pomSettingsTask
  def finalManifestTask: AbstractTask[ManifestEntries] = publishTasks.finalManifestSettingsTask
  def jarTask: AbstractTask[DederPath] = publishTasks.jarTask
  def allJarsTask: AbstractTask[Seq[DederPath]] = publishTasks.allJarsTask
  def assemblyDepsTask: AbstractTask[os.Path] = publishTasks.assemblyDepsTask
  def assemblyTask: AbstractTask[DederPath] = publishTasks.assemblyTask
  def moduleDepsPomSettingsTask: AbstractTask[Seq[Seq[PomSettings]]] = publishTasks.moduleDepsPomSettingsTask
  def sourcesJarTask: AbstractTask[Option[DederPath]] = publishTasks.sourcesJarTask
  def javadocJarTask: AbstractTask[Option[DederPath]] = publishTasks.javadocJarTask
  def publishArtifactsTask: AbstractTask[Option[PublishArtifactsRes]] = publishTasks.publishArtifactsTask
  def publishLocalTask: AbstractTask[Option[os.Path]] = publishTasks.publishLocalTask
  def publishTask: AbstractTask[String] = publishTasks.publishTask

  // GraalVM
  def graalvmHomeTask: AbstractTask[Option[os.Path]] = graalvmTasks.graalvmHomeTask
  def nativeImageOptionsTask: AbstractTask[Seq[String]] = graalvmTasks.nativeImageOptionsTask
  def nativeIncludedResourcesOptionsTask: AbstractTask[Seq[String]] = graalvmTasks.nativeIncludedResourcesOptionsTask
  def graalvmReachabilityMetadataOptionsTask: AbstractTask[Seq[String]] = graalvmTasks.graalvmReachabilityMetadataOptionsTask
  def graalvmNativeImageTask: AbstractTask[os.Path] = graalvmTasks.graalvmNativeImageTask

  // Internal tasks
  def allGeneratedSourcesTask: AbstractTask[Seq[DederPath]] = coreTasks.allGeneratedSourcesTask
  def allGeneratedSourceFilesTask: AbstractTask[Seq[DederPath]] = coreTasks.allGeneratedSourceFilesTask
  def allGeneratedResourcesTask: AbstractTask[Seq[DederPath]] = coreTasks.allGeneratedResourcesTask
  def compileOnlyDependenciesTask: AbstractTask[Seq[Dependency]] = coreTasks.compileOnlyDependenciesTask
  def dependenciesTask: AbstractTask[Seq[Dependency]] = coreTasks.dependenciesTask
  def allDependenciesTask: AbstractTask[Seq[Dependency]] = coreTasks.allDependenciesTask
  def mandatoryDependenciesTask: AbstractTask[Seq[Dependency]] = coreTasks.mandatoryDependenciesTask
  def allClassesDirsTask: AbstractTask[Seq[DederPath]] = coreTasks.allClassesDirsTask
  def compilerJarsTask: AbstractTask[Seq[os.Path]] = coreTasks.compilerJarsTask
}

/** Adapts the internal [[ScalaJsTasks]] to the public [[ScalaJsTasksApi]] interface without widening the task vals. */
class ScalaJsTasksApiAdapter(scalaJsTasks: ScalaJsTasks) extends ScalaJsTasksApi {
  def fastLinkJsTask: AbstractTask[String] = scalaJsTasks.fastLinkJsTask
  def fullLinkJsTask: AbstractTask[String] = scalaJsTasks.fullLinkJsTask
  def linkJsTask: AbstractTask[String] = scalaJsTasks.linkJsTask
  def runJsTask: AbstractTask[Seq[String]] = scalaJsTasks.runJsTask
  def testTask: AbstractTask[DederTestResults] = scalaJsTasks.testJsTask
}

/** Adapts the internal [[ScalaNativeTasks]] to the public [[ScalaNativeTasksApi]] interface without widening the task
  * vals.
  */
class ScalaNativeTasksApiAdapter(scalaNativeTasks: ScalaNativeTasks) extends ScalaNativeTasksApi {
  def fastNativeLinkTask: AbstractTask[String] = scalaNativeTasks.fastNativeLinkTask
  def fullNativeLinkTask: AbstractTask[String] = scalaNativeTasks.fullNativeLinkTask
  def nativeLinkTask: AbstractTask[String] = scalaNativeTasks.nativeLinkTask
  def runNativeTask: AbstractTask[Seq[String]] = scalaNativeTasks.runNativeTask
  def testTask: AbstractTask[DederTestResults] = scalaNativeTasks.testNativeTask
}
