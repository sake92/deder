package ba.sake.deder

import ba.sake.deder.config.DederProject.ModuleType

class TasksRegistry(initialTasks: Seq[Task[?, ?]]) {
  ensureUnique(initialTasks)
  private var allTasks: Seq[Task[?, ?]] = initialTasks

  def resolve(moduleType: ModuleType): Seq[Task[?, ?]] =
    allTasks.filter { t =>
      // if empty -> supports all types...
      t.supportedModuleTypes.isEmpty ||
      t.supportedModuleTypes.contains(moduleType)
    }

  def add(task: Task[?, ?]): Unit = {
    val newTasks = allTasks.appended(task)
    ensureUnique(newTasks)
    allTasks = allTasks.appended(task)
  }

  private def ensureUnique(tasks: Seq[Task[?, ?]]): Unit = {
    // no duplicate task names per module type
    for moduleType <- ModuleType.values do {
      val tasksForType =
        tasks.filter(t => t.supportedModuleTypes.isEmpty || t.supportedModuleTypes.contains(moduleType))
      val names = tasksForType.map(_.name)
      val dups = names.diff(names.distinct)
      require(dups.isEmpty, s"Duplicate task names for ${moduleType}: ${dups.mkString(", ")}")
    }
  }

}
