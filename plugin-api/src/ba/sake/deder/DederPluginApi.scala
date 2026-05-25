package ba.sake.deder

import ba.sake.deder.deps.Dependency

/** Trait that plugins implement to register additional tasks. */
trait DederPluginApi {
  def id: String

  /** @param params
    * @return
    *   Either an error message (Left) or a sequence of tasks to register (Right). If an error occurs during plugin
    *   loading, the message will be logged and the plugin will be skipped, but the server will continue running with
    *   previously loaded plugins (if any).
    */
  def tasks(params: PluginTasksParams): Either[String, Seq[AbstractTask[?]]]

  def onClose(): Unit = ()
}

/** @param configText
  *   The plugin's evaluated Pkl configuration serialized as PCF. Parse it with Pkl to get your typed config.
  * @param coreTasks
  *   Access to built-in server tasks (compile, classes, deps, etc.)
  */
case class PluginTasksParams(
    configText: String,
    coreTasks: CoreTasksApi
)

/** Typed access to built-in server tasks, available to plugins as dependency targets. */
// TODO expand this with more tasks as needed, e.g. test sources, test resources, test compile, etc.
trait CoreTasksApi {
  def sourcesTask: AbstractTask[Seq[DederPath]]
  def sourceFilesTask: AbstractTask[Seq[DederPath]]
  def resourcesTask: AbstractTask[Seq[DederPath]]
  def classesTask: AbstractTask[DederPath]
  def allClassesDirsTask: AbstractTask[Seq[DederPath]]
  def compileTask: AbstractTask[DederPath]
  def allDependenciesTask: AbstractTask[Seq[Dependency]]
  def compileClasspathTask: AbstractTask[Seq[os.Path]]
}
