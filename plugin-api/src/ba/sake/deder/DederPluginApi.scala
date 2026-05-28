package ba.sake.deder

import ba.sake.deder.deps.{DepTree, Dependency}
import ba.sake.deder.config.DederProject

/** Trait that plugins implement to register additional tasks. */
trait DederPluginApi {
  def id: String

  /** Called on every (re)load. Receives full context including plugin config, core task APIs,
    * server internals, and the full DederProject. Returns tasks to register (or Seq.empty for
    * sidecar-only plugins like dashboards).
    *
    * @return Left(error) → plugin is skipped (error logged, no tasks registered,
    *         server continues with other plugins).
    *         Right(tasks) → tasks are registered in the build DAG.
    */
  def init(params: PluginInitParams): Either[String, Seq[AbstractTask[?]]] = Right(Seq.empty)

  /** Called when the plugin is unloaded (config reload or server shutdown). */
  def close(): Unit = ()
}

/** @param configText
  *   The plugin's evaluated Pkl configuration serialized as PCF. Parse it with Pkl to get your typed config.
  * @param coreTasks
  *   Access to the stable JVM/core built-in tasks that plugins may depend on.
  * @param sjsTasks
  *   Access to the stable Scala.js linking tasks that plugins may depend on.
  * @param snTasks
  *   Access to the stable Scala Native linking tasks that plugins may depend on.
  * @param internals
  *   Access to server introspection (request metrics, uptime, etc.).
  * @param project
  *   The full parsed DederProject config (modules, server properties, repositories, etc.).
  */
case class PluginInitParams(
    configText: String,
    coreTasks: CoreTasksApi,
    sjsTasks: ScalaJsTasksApi,
    snTasks: ScalaNativeTasksApi,
    internals: DederProjectInternals,
    project: DederProject
)

/** Typed access to the curated, stable core task surface that plugins may depend on. Internal tasks and actual
  * execution entrypoint tasks (such as `run`, `test`, or `repl`) are intentionally kept out, while stable
  * dependency/configuration tasks such as classpaths, main-class selection, JVM options, and REPL jars remain
  * available.
  */
trait CoreTasksApi {
  def sourcesTask: AbstractTask[Seq[DederPath]]
  def sourceFilesTask: AbstractTask[Seq[DederPath]]
  def generatedSourcesTask: AbstractTask[DederPath]
  def resourcesTask: AbstractTask[Seq[DederPath]]
  def javaHomeTask: AbstractTask[Option[os.Path]]
  def javaVersionTask: AbstractTask[Option[String]]
  def javacOptionsTask: AbstractTask[Seq[String]]
  def scalacOptionsTask: AbstractTask[Seq[String]]
  def scalaVersionTask: AbstractTask[String]
  def depsTask: AbstractTask[Seq[String]]
  def repositoriesTask: AbstractTask[Seq[String]]
  def compileOnlyDepsTask: AbstractTask[Seq[String]]
  def depsTreeTask: AbstractTask[DepTree]
  def classesTask: AbstractTask[DederPath]
  def semanticdbDirTask: AbstractTask[DederPath]
  def compileTask: AbstractTask[DederPath]
  def compileClasspathTask: AbstractTask[Seq[os.Path]]
  def javaSemanticdbVersionTask: AbstractTask[String]
  def scalaSemanticdbVersionTask: AbstractTask[String]
  def semanticdbEnabledTask: AbstractTask[Boolean]
  def javacAnnotationProcessorDepsTask: AbstractTask[Seq[String]]
  def javacAnnotationProcessorsTask: AbstractTask[Seq[os.Path]]
  def scalacPluginDepsTask: AbstractTask[Seq[String]]
  def scalacPluginsTask: AbstractTask[Seq[os.Path]]
  def compilerDepsTask: AbstractTask[Seq[Dependency]]
  def jvmOptionsTask: AbstractTask[Seq[String]]
  def runClasspathTask: AbstractTask[Seq[os.Path]]
  def mainClassesTask: AbstractTask[Seq[String]]
  def mainClassTask: AbstractTask[Option[String]]
  def finalMainClassTask: AbstractTask[Option[String]]
  def replDepsTask: AbstractTask[Seq[Dependency]]
  def replJarsTask: AbstractTask[Seq[os.Path]]
}

/** Typed access to the curated, stable Scala.js task surface that plugins may depend on. */
trait ScalaJsTasksApi {
  def fastLinkJsTask: AbstractTask[String]
  def fullLinkJsTask: AbstractTask[String]
  def linkJsTask: AbstractTask[String]
}

/** Typed access to the curated, stable Scala Native task surface that plugins may depend on. */
trait ScalaNativeTasksApi {
  def fastNativeLinkTask: AbstractTask[String]
  def fullNativeLinkTask: AbstractTask[String]
  def nativeLinkTask: AbstractTask[String]
}
