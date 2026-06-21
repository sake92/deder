package ba.sake.deder

import ba.sake.deder.config.DederProject.ModuleType

class TasksRegistryApiAdapter(registry: TasksRegistry) extends TasksRegistryApi {

  override def allTasks: Seq[TaskInfo] =
    registry.all.map(toTaskInfo)

  override def tasksFor(moduleType: ModuleType): Seq[TaskInfo] =
    registry.resolve(moduleType).map(toTaskInfo)

  private def toTaskInfo(t: Task[?, ?, ?]): TaskInfo = TaskInfo(
    name = t.name,
    description = t.description,
    category = t.category,
    kind = t.kind,
    supportedModuleTypes = t.supportedModuleTypes.toSeq,
    transitive = t.transitive,
    singleton = t.singleton,
    internal = t.internal,
    featureTags = t.featureTags
  )
}
