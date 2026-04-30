package ba.sake.deder

import ba.sake.deder.deps.Dependency

/** Trait that plugins implement to register additional tasks. */
trait DederPlugin {
  def id: String
  def tasks(coreTasks: CoreTasksApi): Seq[AbstractTask[?]]
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
