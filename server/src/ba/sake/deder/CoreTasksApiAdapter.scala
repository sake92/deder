package ba.sake.deder

import ba.sake.deder.deps.{DepTree, Dependency}
import ba.sake.deder.scalajs.ScalaJsTasks
import ba.sake.deder.scalanative.ScalaNativeTasks

/** Adapts the internal [[CoreTasks]] to the public [[CoreTasksApi]] interface.
 *  This keeps [[CoreTasks]] free of the [[CoreTasksApi]] inheritance (which
 *  would cause Scala to widen task-val types to `AbstractTask[T]`).
 */
class CoreTasksApiAdapter(coreTasks: CoreTasks, runTasks: RunTasks) extends CoreTasksApi {
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
  def compileTask: AbstractTask[CompileResult] = coreTasks.compileTask
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
}

/** Adapts the internal [[ScalaJsTasks]] to the public [[ScalaJsTasksApi]] interface without widening the task vals. */
class ScalaJsTasksApiAdapter(scalaJsTasks: ScalaJsTasks) extends ScalaJsTasksApi {
  def fastLinkJsTask: AbstractTask[String] = scalaJsTasks.fastLinkJsTask
  def fullLinkJsTask: AbstractTask[String] = scalaJsTasks.fullLinkJsTask
  def linkJsTask: AbstractTask[String] = scalaJsTasks.linkJsTask
}

/** Adapts the internal [[ScalaNativeTasks]] to the public [[ScalaNativeTasksApi]] interface without widening the task
  * vals.
  */
class ScalaNativeTasksApiAdapter(scalaNativeTasks: ScalaNativeTasks) extends ScalaNativeTasksApi {
  def fastNativeLinkTask: AbstractTask[String] = scalaNativeTasks.fastNativeLinkTask
  def fullNativeLinkTask: AbstractTask[String] = scalaNativeTasks.fullNativeLinkTask
  def nativeLinkTask: AbstractTask[String] = scalaNativeTasks.nativeLinkTask
}
