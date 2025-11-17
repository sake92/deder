package ba.sake.deder

import ba.sake.tupson.{given, *}

case class TaskBuilder[T: JsonRW: Hashable, Deps <: Tuple](
    name: String,
    taskDeps: Deps,
    // if it triggers upstream modules task with same name
    transitive: Boolean = false
)(using ev: TaskDeps[Deps] =:= true) {
  def dependsOn[T2](t: Task[T2, ?]): TaskBuilder[T, Task[T2, ?] *: Deps] =
    val newdeps = t *: taskDeps
    TaskBuilder(name, t *: taskDeps, transitive)
  def build(execute: TaskExecContext[Deps] => T): Task[T, Deps] =
    Task(name, taskDeps, execute, transitive)
}

object TaskBuilder {
  def make[T: JsonRW: Hashable](
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

case class TaskExecContext[Deps <: Tuple](
    module: Module,
    depResults: TaskDepResults[Deps],
    transitiveResults: Seq[?] // results from dependent modules
)(using ev: TaskDeps[Deps] =:= true)

case class Task[T : JsonRW: Hashable, Deps <: Tuple](
    name: String,
    taskDeps: Deps,
    execute: TaskExecContext[Deps] => T,
    // if it triggers upstream modules task with same name
    // the only way to reference a task across modules
    transitive: Boolean = false
)(using ev: TaskDeps[Deps] =:= true) {
  def executeUnsafe(module: Module, depResults: Seq[TaskResult[?]], transitiveResults: Seq[TaskResult[?]]): TaskResult[T] = {
    val depResultsUnsafe = Tuple.fromArray(depResults.map(_.value).toArray).asInstanceOf[TaskDepResults[Deps]]
    val transitiveResultsUnsafe = transitiveResults.map(_.value).asInstanceOf[Seq[T]]
    val res = execute(TaskExecContext(module, depResultsUnsafe, transitiveResultsUnsafe))
    val allDepResults = depResults ++ transitiveResults
    val inputsHash = HashUtils.hashStr(allDepResults.map(_.outputHash).mkString("-"))
    val outputHash = Hashable[T].hashStr(res)
    val taskResult = TaskResult(res, inputsHash, outputHash)
    os.write.over(os.pwd / "out_deder" / module.id / name / "metadata.json", taskResult.toJson, createFolders = true)
    taskResult
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
