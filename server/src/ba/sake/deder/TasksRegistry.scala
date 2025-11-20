package ba.sake.deder

import ba.sake.deder.config.DederProject.ModuleType
import ba.sake.deder.zinc.ZincCompiler

class TasksRegistry(zincCompiler: ZincCompiler) {

  private val coreTasks = CoreTasks(zincCompiler)

  def resolve(moduleType: ModuleType): Seq[Task[?, ?]] = {
    coreTasks.all.filter { t =>
      t.supportedModuleTypes(moduleType)
    }
  }
}
