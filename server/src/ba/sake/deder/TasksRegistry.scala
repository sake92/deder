package ba.sake.deder

import ba.sake.deder.config.DederProject.ModuleType

class TasksRegistry() {

  val coreTasks: CoreTasks = CoreTasks()

  def resolve(moduleType: ModuleType): Seq[Task[?, ?]] = {
    coreTasks.all.filter { t =>
      t.supportedModuleTypes.contains(moduleType)
    }
  }
  // TODO ensure unique task names per module type
  
  // TODO handle tasks dynamically added/removed by plugins
}
