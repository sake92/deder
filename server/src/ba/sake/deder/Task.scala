package ba.sake.deder

import scala.util.control.Breaks.{break, breakable}
import scala.Tuple.:*
import ba.sake.tupson.{given, *}

case class TaskBuilder[T: JsonRW: Hashable, Deps <: Tuple] private (
    name: String,
    taskDeps: Deps,
    // if it triggers upstream modules task with same name
    transitive: Boolean,
    cached: Boolean,
    supportedModuleTypes: Set[ModuleType]
)(using ev: TaskDeps[Deps] =:= true) {
  def dependsOn[T2](t: Task[T2, ?]): TaskBuilder[T, Deps :* Task[T2, ?]] =
    TaskBuilder(name, taskDeps :* t, transitive, cached, supportedModuleTypes)

  def build(execute: TaskExecContext[Deps] => T): Task[T, Deps] =
    Task(name, taskDeps, execute, transitive, cached, supportedModuleTypes)
}

object TaskBuilder {
  def make[T: JsonRW: Hashable](
      name: String,
      // if it triggers upstream modules task with same name
      transitive: Boolean = false,
      cached: Boolean = true,
      supportedModuleTypes: Set[ModuleType] = Set.empty
  ): TaskBuilder[T, EmptyTuple] = TaskBuilder(name, EmptyTuple, transitive, cached, supportedModuleTypes)
}

// why did I add this?? :DDDD
// type TaskDeps = Task[?, ?]
type TaskDeps[T <: Tuple] <: Boolean = T match {
  case EmptyTuple      => true
  case t :* Task[?, ?] => TaskDeps[t]
  case _               => false
}

type TaskDepResults[T <: Tuple] <: Tuple = T match {
  case EmptyTuple         => EmptyTuple
  case Task[t, ?] *: rest => t *: TaskDepResults[rest]
}

/*
type TaskDepResults[T <: Tuple] <: Tuple = T match {
  case EmptyTuple         => EmptyTuple
  case Task[t, ?] *: rest => t *: TaskDepResults[rest]
}
*/

case class TaskExecContext[Deps <: Tuple](
    module: Module,
    depResults: TaskDepResults[Deps],
    transitiveResults: Seq[?] // results from dependent modules
)(using ev: TaskDeps[Deps] =:= true)

case class Task[T: JsonRW: Hashable, Deps <: Tuple](
    name: String,
    taskDeps: Deps,
    execute: TaskExecContext[Deps] => T,
    // if it triggers upstream modules task with same name
    // the only way to reference a task across modules
    transitive: Boolean,
    cached: Boolean,
    supportedModuleTypes: Set[ModuleType]
)(using ev: TaskDeps[Deps] =:= true) {

  def executeUnsafe(
      module: Module,
      depResults: Seq[TaskResult[?]],
      transitiveResults: Seq[TaskResult[?]]
  ): TaskResult[T] = {
    val metadataFile = os.pwd / ".deder/out" / module.id / name / "metadata.json"

    val allDepResults = depResults ++ transitiveResults
    val inputsHash = HashUtils.hashStr(allDepResults.map(_.outputHash).mkString("-"))

    def computeTaskResult(): TaskResult[T] = {
      val depResultsUnsafe = Tuple.fromArray(depResults.map(_.value).toArray).asInstanceOf[TaskDepResults[Deps]]
      val transitiveResultsUnsafe = transitiveResults.map(_.value).asInstanceOf[Seq[T]]
      val res = execute(TaskExecContext(module, depResultsUnsafe, transitiveResultsUnsafe))
      val outputHash = Hashable[T].hashStr(res)
      val taskResult = TaskResult(res, inputsHash, outputHash)
      os.write.over(metadataFile, taskResult.toJson, createFolders = true)
      taskResult
    }

    if os.exists(metadataFile) then {
      val cachedTaskResult = os.read(metadataFile).parseJson[TaskResult[T]]
      val hasDeps = allDepResults.nonEmpty
      if cached && hasDeps && inputsHash == cachedTaskResult.inputsHash then
        println(s"[module ${module.id}] [task ${name}] Using cached result.")
        cachedTaskResult
      else computeTaskResult()
    } else {
      computeTaskResult()
    }
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
