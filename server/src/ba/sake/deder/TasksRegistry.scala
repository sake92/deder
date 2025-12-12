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
  
  // TODO dynamic tasks added by plugins, at runtime or just restart server??
}
