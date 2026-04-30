package ba.sake.deder

import ba.sake.deder.deps.Dependency

/** Adapts the internal [[CoreTasks]] to the public [[CoreTasksApi]] interface.
 *  This keeps [[CoreTasks]] free of the [[CoreTasksApi]] inheritance (which
 *  would cause Scala to widen task-val types to `AbstractTask[T]`).
 */
class CoreTasksApiAdapter(coreTasks: CoreTasks) extends CoreTasksApi {
  def sourcesTask: AbstractTask[Seq[DederPath]] = coreTasks.sourcesTask
  def sourceFilesTask: AbstractTask[Seq[DederPath]] = coreTasks.sourceFilesTask
  def resourcesTask: AbstractTask[Seq[DederPath]] = coreTasks.resourcesTask
  def classesTask: AbstractTask[os.Path] = coreTasks.classesTask
  def allClassesDirsTask: AbstractTask[Seq[os.Path]] = coreTasks.allClassesDirsTask
  def compileTask: AbstractTask[DederPath] = coreTasks.compileTask
  def allDependenciesTask: AbstractTask[Seq[Dependency]] = coreTasks.allDependenciesTask
  def compileClasspathTask: AbstractTask[Seq[os.Path]] = coreTasks.compileClasspathTask
}
