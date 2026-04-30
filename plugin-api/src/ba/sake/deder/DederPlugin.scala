package ba.sake.deder

import ba.sake.deder.deps.Dependency

/** Trait that plugins implement to register additional tasks. */
trait DederPlugin {
  def id: String
  def tasks(coreTasks: CoreTasksApi): Seq[Task[?, ?]]
}

/** Typed access to built-in server tasks, available to plugins as dependency targets. */
trait CoreTasksApi {
  def sourcesTask: Task[Seq[DederPath], ?]
  def sourceFilesTask: Task[Seq[DederPath], ?]
  def resourcesTask: Task[Seq[DederPath], ?]
  def classesTask: Task[os.Path, ?]
  def allClassesDirsTask: Task[Seq[os.Path], ?]
  def compileTask: Task[DederPath, ?]
  def allDependenciesTask: Task[Seq[Dependency], ?]
  def compileClasspathTask: Task[Seq[os.Path], ?]
}
