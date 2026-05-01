package ba.sake.deder

import ba.sake.deder.deps.Dependency
import ba.sake.deder.config.DederProject.Plugin

/** Trait that plugins implement to register additional tasks. */
trait DederPlugin {
  def id: String

  /** @param coreTasks Access to built-in server tasks (compile, classes, deps, etc.)
   *  @param config    The plugin's evaluated Pkl configuration (base Plugin type).
   *                   Cast to your specific Plugin subclass if needed.
   */
  def tasks(coreTasks: CoreTasksApi, config: Plugin): Seq[AbstractTask[?]]
}

/** Typed access to built-in server tasks, available to plugins as dependency targets. */
trait CoreTasksApi {
  def sourcesTask: AbstractTask[Seq[DederPath]]
  def sourceFilesTask: AbstractTask[Seq[DederPath]]
  def resourcesTask: AbstractTask[Seq[DederPath]]
  def classesTask: AbstractTask[os.Path]
  def allClassesDirsTask: AbstractTask[Seq[os.Path]]
  def compileTask: AbstractTask[DederPath]
  def allDependenciesTask: AbstractTask[Seq[Dependency]]
  def compileClasspathTask: AbstractTask[Seq[os.Path]]
}
