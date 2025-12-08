package ba.sake.deder

import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.{DederModule, ModuleType}

import scala.util.control.Breaks.{break, breakable}
import scala.Tuple.:*
import ba.sake.tupson.{*, given}
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import os.write.over

case class TaskBuilder[T, Deps <: Tuple] private (
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
  def make[T](
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

case class TaskExecContext[T, Deps <: Tuple](
    project: DederProject,
    module: DederModule,
    depResults: TaskDepResults[Deps],
    transitiveResults: Seq[Seq[T]], // results from dependent modules
    notifications: ServerNotificationsLogger,
    out: os.Path
)(using ev: TaskDeps[Deps] =:= true)

// TODO add "args", .e.g. for run task
// TODO make T: JsonRW mandatory, to show in --json and web server..
sealed trait Task[T, Deps <: Tuple](using ev: TaskDeps[Deps] =:= true) {
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
      serverNotificationsLogger: ServerNotificationsLogger
  ): TaskResult[T]
}

case class TaskImpl[T, Deps <: Tuple](
    name: String,
    execute: TaskExecContext[T, Deps] => T,
    taskDeps: Deps = EmptyTuple,
    // if it triggers upstream modules task with same name
    // the only way to reference a task across modules
    transitive: Boolean = false,
    supportedModuleTypes: Set[ModuleType] = Set.empty
)(using ev: TaskDeps[Deps] =:= true)
    extends Task[T, Deps](using ev) {
  override private[deder] def executeUnsafe(
      project: DederProject,
      module: DederModule,
      depResults: Seq[TaskResult[?]],
      transitiveResults: Seq[Seq[TaskResult[?]]],
      serverNotificationsLogger: ServerNotificationsLogger
  ): TaskResult[T] = {
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
        serverNotificationsLogger,
        outDir
      )
    )
    val taskResult = TaskResult(res, "", "" /*, transitiveResultsUnsafe*/ )
    serverNotificationsLogger.add(
      ServerNotification.logDebug(s"Computed result for ${name}", Some(module.id))
    )
    taskResult
  }
}

case class CachedTask[T: JsonRW: Hashable, Deps <: Tuple](
    name: String,
    execute: TaskExecContext[T, Deps] => T,
    taskDeps: Deps = EmptyTuple,
    // if it triggers upstream modules task with same name
    // the only way to reference a task across modules
    transitive: Boolean = false,
    supportedModuleTypes: Set[ModuleType] = Set.empty
)(using ev: TaskDeps[Deps] =:= true)
    extends Task[T, Deps](using ev) {

  private[deder] override def executeUnsafe(
      project: DederProject,
      module: DederModule,
      depResults: Seq[TaskResult[?]],
      transitiveResults: Seq[Seq[TaskResult[?]]],
      serverNotificationsLogger: ServerNotificationsLogger
  ): TaskResult[T] = {

    serverNotificationsLogger.add(
      ServerNotification.logDebug(s"Executing ${name}", Some(module.id))
    )

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
      if hasDeps && inputsHash == cachedTaskResult.inputsHash then
        serverNotificationsLogger.add(
          ServerNotification.logDebug(
            s"Using cached result for ${name}",
            Some(module.id)
          )
        )
        cachedTaskResult
      else computeTaskResult()
    } else {
      computeTaskResult()
    }
  }
}

// TODO SourceFileTask
// TODO ConfigValueTask

// dynamic, for each module
case class TaskInstance(
    module: DederModule,
    task: Task[?, ?],
    lock: Lock
) {
  def moduleId: String = module.id

  def id: String = s"${moduleId}.${task.name}"

  override def canEqual(that: Any): Boolean = that.isInstanceOf[TaskInstance]

  override def equals(that: Any): Boolean =
    that match {
      case other: TaskInstance => this.id == other.id
      case _                   => false
    }

  override def hashCode(): Int = id.hashCode

}

object TaskInstance {
  def apply(module: DederModule, task: Task[?, ?]): TaskInstance =
    TaskInstance(module, task, new ReentrantLock())
}
