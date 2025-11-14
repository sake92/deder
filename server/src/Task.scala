package bla

case class TaskBuilder[T, Deps <: Tuple](
    name: String,
    taskDeps: Deps,
    // if it triggers upstream modules task with same name
    transitive: Boolean = false
)(using ev: TaskDeps[Deps] =:= true) {
  def dependsOn[T2](t: Task[T2, ?]): TaskBuilder[T, Task[T2, ?] *: Deps] =
    val newdeps = t *: taskDeps
    TaskBuilder(name, t *: taskDeps, transitive)
  def build(execute: TaskExecContext[T, Deps] => T): Task[T, Deps] =
    Task(name, taskDeps, execute, transitive)
}

object TaskBuilder {
  def make[T](
      name: String,
      // if it triggers upstream modules task with same name
      transitive: Boolean = false
  ): TaskBuilder[T, EmptyTuple] = TaskBuilder(name, EmptyTuple, transitive)
}

//
// type TaskDeps = Task[?, ?]
type TaskDeps[T <: Tuple] <: Boolean = T match {
  case EmptyTuple      => true
  case Task[?, ?] *: t => TaskDeps[t]
  case _               => false
}

type TaskDepResults[T <: Tuple] <: Tuple = T match {
  case EmptyTuple         => EmptyTuple
  case Task[t, ?] *: rest => t *: TaskDepResults[rest]
}

case class TaskExecContext[T, Deps <: Tuple](
    module: Module,
    depResults: TaskDepResults[Deps],
    transitiveResults: Seq[T] // results from dependent modules
)(using ev: TaskDeps[Deps] =:= true)

case class Task[T, Deps <: Tuple](
    name: String,
    taskDeps: Deps,
    execute: TaskExecContext[T, Deps] => T,
    // if it triggers upstream modules task with same name
    // the only way to reference a task across modules
    transitive: Boolean = false
)(using ev: TaskDeps[Deps] =:= true) {
  def executeUnsafe(module: Module, depResults: Seq[Any], transitiveResults: Seq[Any]): T = {
    val depResultsUnsafe = Tuple.fromArray(depResults.toArray).asInstanceOf[TaskDepResults[Deps]]
    val transitiveResultsUnsafe = transitiveResults.asInstanceOf[Seq[T]]
    execute(TaskExecContext(module, depResultsUnsafe, transitiveResultsUnsafe))
  }
}

// dynamic, for each module
case class TaskInstance(
    module: Module,
    task: Task[?, ?]
) {
  def moduleId: String = module.id
  def id: String = s"${moduleId}.${task.name}"
}
