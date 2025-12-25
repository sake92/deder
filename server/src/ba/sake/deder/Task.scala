package ba.sake.deder

import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.{DederModule, ModuleType}

import scala.util.control.Breaks.{break, breakable}
import scala.Tuple.:*
import ba.sake.tupson.{*, given}
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import os.write.over

case class TaskBuilder[T: JsonRW, Deps <: Tuple] private (
    name: String,
    taskDeps: Deps,
    // if it triggers upstream modules task with same name
    transitive: Boolean,
    supportedModuleTypes: Set[ModuleType]
)(using ev: TaskDeps[Deps] =:= true) {
  def dependsOn[T2](t: Task[T2, ?]): TaskBuilder[T, Deps :* Task[T2, ?]] =
    TaskBuilder(name, taskDeps :* t, transitive, supportedModuleTypes)

  def build(execute: TaskExecContext[T, Deps] => T): Task[T, Deps] =
    TaskImpl(name, execute, taskDeps, transitive, supportedModuleTypes)
}

object TaskBuilder {
  def make[T: JsonRW](
      name: String,
      // if it triggers upstream modules task with same name
      transitive: Boolean = false,
      supportedModuleTypes: Set[ModuleType] = Set.empty
  ): TaskBuilder[T, EmptyTuple] = TaskBuilder(name, EmptyTuple, transitive, supportedModuleTypes)
}

case class CachedTaskBuilder[T: JsonRW: Hashable, Deps <: Tuple] private (
    name: String,
    taskDeps: Deps,
    // if it triggers upstream modules task with same name
    transitive: Boolean,
    supportedModuleTypes: Set[ModuleType]
)(using ev: TaskDeps[Deps] =:= true) {
  def dependsOn[T2](t: Task[T2, ?]): CachedTaskBuilder[T, Deps :* Task[T2, ?]] =
    CachedTaskBuilder(name, taskDeps :* t, transitive, supportedModuleTypes)

  def build(execute: TaskExecContext[T, Deps] => T): Task[T, Deps] =
    CachedTask(name, execute, taskDeps, transitive, supportedModuleTypes)
}

object CachedTaskBuilder {
  def make[T: JsonRW: Hashable](
      name: String,
      // if it triggers upstream modules task with same name
      transitive: Boolean = false,
      supportedModuleTypes: Set[ModuleType] = Set.empty
  ): CachedTaskBuilder[T, EmptyTuple] = CachedTaskBuilder(name, EmptyTuple, transitive, supportedModuleTypes)
}

// this is to make sure that Deps are Task-s and not arbitrary types
type TaskDeps[T <: Tuple] <: Boolean = T match {
  case EmptyTuple      => true
  case t :* Task[?, ?] => TaskDeps[t]
  case _               => false
}

type TaskDepResults[T <: Tuple] <: Tuple = T match {
  case EmptyTuple         => EmptyTuple
  case Task[t, ?] *: rest => t *: TaskDepResults[rest]
}

// needs a T because of transitive results
case class TaskExecContext[T, Deps <: Tuple](
    project: DederProject,
    module: DederModule,
    depResults: TaskDepResults[Deps],
    transitiveResults: Seq[Seq[T]], // results from dependent modules
    args: Seq[String], // external args, like run args
    watch: Boolean,
    notifications: ServerNotificationsLogger,
    out: os.Path
)(using ev: TaskDeps[Deps] =:= true)

sealed trait Task[T, Deps <: Tuple](using val rw: JsonRW[T], ev: TaskDeps[Deps] =:= true) {
  def name: String
  def supportedModuleTypes: Set[ModuleType]
  def transitive: Boolean
  def taskDeps: Deps
  def execute: TaskExecContext[T, Deps] => T
  private[deder] def executeUnsafe(
      project: DederProject,
      module: DederModule,
      depResults: Seq[TaskResult[?]],
      transitiveResults: Seq[Seq[TaskResult[?]]],
      args: Seq[String],
      watch: Boolean,
      serverNotificationsLogger: ServerNotificationsLogger
  ): (res: TaskResult[T], changed: Boolean)
}

class TaskImpl[T: JsonRW, Deps <: Tuple](
    val name: String,
    val execute: TaskExecContext[T, Deps] => T,
    val taskDeps: Deps = EmptyTuple,
    // if it triggers upstream modules task with same name
    // the only way to reference a task across modules
    val transitive: Boolean = false,
    val supportedModuleTypes: Set[ModuleType] = Set.empty
)(using ev: TaskDeps[Deps] =:= true)
    extends Task[T, Deps] {
  override private[deder] def executeUnsafe(
      project: DederProject,
      module: DederModule,
      depResults: Seq[TaskResult[?]],
      transitiveResults: Seq[Seq[TaskResult[?]]],
      args: Seq[String],
      watch: Boolean,
      serverNotificationsLogger: ServerNotificationsLogger
  ): (res: TaskResult[T], changed: Boolean) = {
    serverNotificationsLogger.add(
      ServerNotification.logDebug(s"Executing ${name}", Some(module.id))
    )
    val depResultsUnsafe = Tuple.fromArray(depResults.map(_.value).toArray).asInstanceOf[TaskDepResults[Deps]]
    val transitiveResultsUnsafe = transitiveResults.asInstanceOf[Seq[Seq[TaskResult[T]]]]
    val outDir = DederGlobals.projectRootDir / ".deder/out" / module.id / name
    val res = execute(
      TaskExecContext(
        project,
        module,
        depResultsUnsafe,
        transitiveResultsUnsafe.map(_.map(_.value)),
        args,
        watch,
        serverNotificationsLogger,
        outDir
      )
    )
    val taskResult = TaskResult(res, "", "" /*, transitiveResultsUnsafe*/ )
    serverNotificationsLogger.add(
      ServerNotification.logDebug(s"Computed result for ${name}", Some(module.id))
    )
    (taskResult, true)
  }

  override def toString(): String = s"TaskImpl($name)"
}

class CachedTask[T: JsonRW: Hashable, Deps <: Tuple](
    val name: String,
    val execute: TaskExecContext[T, Deps] => T,
    val taskDeps: Deps = EmptyTuple,
    // if it triggers upstream modules task with same name
    // the only way to reference a task across modules
    val transitive: Boolean = false,
    val supportedModuleTypes: Set[ModuleType] = Set.empty
)(using ev: TaskDeps[Deps] =:= true)
    extends Task[T, Deps] {

  private[deder] override def executeUnsafe(
      project: DederProject,
      module: DederModule,
      depResults: Seq[TaskResult[?]],
      transitiveResults: Seq[Seq[TaskResult[?]]],
      args: Seq[String],
      watch: Boolean,
      serverNotificationsLogger: ServerNotificationsLogger
  ): (res: TaskResult[T], changed: Boolean) = {

    serverNotificationsLogger.add(ServerNotification.logDebug(s"Executing ${name}", Some(module.id)))

    val metadataFile = DederGlobals.projectRootDir / ".deder/out" / module.id / name / "metadata.json"
    val outDir = DederGlobals.projectRootDir / ".deder/out" / module.id / name

    val allDepResults = depResults ++ transitiveResults.headOption.getOrElse(Seq.empty) // only first level for hashing
    val inputsHash = HashUtils.hashStr(allDepResults.map(_.outputHash).mkString("-"))

    def computeTaskResult(): TaskResult[T] = {
      val depResultsUnsafe = Tuple.fromArray(depResults.map(_.value).toArray).asInstanceOf[TaskDepResults[Deps]]
      val transitiveResultsUnsafe = transitiveResults.asInstanceOf[Seq[Seq[TaskResult[T]]]]
      val res = execute(
        TaskExecContext(
          project,
          module,
          depResultsUnsafe,
          transitiveResultsUnsafe.map(_.map(_.value)),
          args,
          watch,
          serverNotificationsLogger,
          outDir
        )
      )
      val outputHash = Hashable[T].hashStr(res)
      val taskResult = TaskResult(res, inputsHash, outputHash)
      os.write.over(metadataFile, taskResult.toJson, createFolders = true)
      serverNotificationsLogger.add(
        ServerNotification.logDebug(s"Computed result for ${name}", Some(module.id))
      )
      taskResult
    }

    if os.exists(metadataFile) then {
      val cachedTaskResult = os.read(metadataFile).parseJson[TaskResult[T]]
      val hasDeps = allDepResults.nonEmpty
      val newRes = if hasDeps && inputsHash == cachedTaskResult.inputsHash then
        serverNotificationsLogger.add(ServerNotification.logDebug(s"Using cached result for ${name}", Some(module.id)))
        cachedTaskResult
      else computeTaskResult()
      val changed = newRes.outputHash != cachedTaskResult.outputHash
      (newRes, changed)
    } else {
      (computeTaskResult(), true)
    }
  }

  override def toString(): String = s"CachedTask($name)"

}

// specialized task just for source file
// so we can easily retrigger watched tasks
class SourceFileTask(
    name: String,
    supportedModuleTypes: Set[ModuleType] = Set.empty,
    execute: TaskExecContext[DederPath, EmptyTuple] => DederPath
) extends CachedTask[DederPath, EmptyTuple](
      name,
      execute,
      taskDeps = EmptyTuple,
      transitive = false,
      supportedModuleTypes
    ) {
  override def toString(): String = s"SourceFileTask($name)"
}

class SourceFilesTask(
    name: String,
    execute: TaskExecContext[Seq[DederPath], EmptyTuple] => Seq[DederPath],
    supportedModuleTypes: Set[ModuleType] = Set.empty
) extends CachedTask[Seq[DederPath], EmptyTuple](
      name,
      execute,
      taskDeps = EmptyTuple,
      transitive = false,
      supportedModuleTypes
    ) {
  override def toString(): String = s"SourceFilesTask($name)"
}

class ConfigValueTask[T: JsonRW: Hashable](
    name: String,
    execute: TaskExecContext[T, EmptyTuple] => T,
    supportedModuleTypes: Set[ModuleType] = Set.empty
) extends CachedTask[T, EmptyTuple](name, execute, taskDeps = EmptyTuple, transitive = false, supportedModuleTypes) {
  override def toString(): String = s"ConfigValueTask($name)"
}

// dynamic, for each module
class TaskInstance(
    val module: DederModule,
    val task: Task[?, ?],
    val lock: Lock
) {
  def moduleId: String = module.id

  def id: String = s"${moduleId}.${task.name}"

  override def equals(that: Any): Boolean =
    that match {
      case other: TaskInstance => this.id == other.id
      case _                   => false
    }

  override def hashCode(): Int = id.hashCode

  override def toString(): String = s"TaskInstance(${id}, ${task})"
}

object TaskInstance {
  def apply(module: DederModule, task: Task[?, ?]): TaskInstance =
    new TaskInstance(module, task, new ReentrantLock())
}
