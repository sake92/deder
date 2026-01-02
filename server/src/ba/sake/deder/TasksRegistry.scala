package ba.sake.deder

import ba.sake.deder.config.DederProject.ModuleType

class TasksRegistry(val coreTasks: CoreTasks) {

  private var allTasks = coreTasks.all

  def resolve(moduleType: ModuleType): Seq[Task[?, ?]] =
    allTasks.filter { t =>
      t.supportedModuleTypes.contains(moduleType)
    }

  def add(task: Task[?, ?]): Unit =
    allTasks = allTasks.appended(task)

  // TODO ensure unique task names per module type

  // TODO handle tasks dynamically added/removed by plugins
}
