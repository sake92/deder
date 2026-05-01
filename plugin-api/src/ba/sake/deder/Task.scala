package ba.sake.deder

import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.{DederModule, ModuleType}
import ba.sake.deder.deps.DependencyResolverApi

import scala.util.control.Breaks.{break, breakable}
import scala.Tuple.:*
import ba.sake.tupson.{*, given}
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import os.write.over

case class TaskBuilder[T: JsonRW: Hashable, Deps <: Tuple] private (
    name: String,
    taskDeps: Deps,
    // if it triggers upstream modules task with same name
    transitive: Boolean,
    singleton: Boolean,
    supportedModuleTypes: Set[ModuleType]
)(using ev: TaskDeps[Deps] =:= true) {
  def dependsOn[T2](t: AbstractTask[T2]): TaskBuilder[T, Deps :* AbstractTask[T2]] =
    TaskBuilder(name, taskDeps :* t, transitive, singleton, supportedModuleTypes)

  def build(execute: TaskExecContext[T, Deps] => T): Task[T, Deps] =
    TaskImpl(name, execute, taskDeps, transitive, singleton, supportedModuleTypes)

  def buildWithSummary(
      execute: TaskExecContext[T, Deps] => T,
      isResultSuccessful: T => Boolean = _ => true,
      summarize: (Seq[(DederModule, T)], ServerNotificationsLogger) => Unit
  ): Task[T, Deps] =
    TaskImpl(
      name,
      execute,
      taskDeps,
      transitive,
      singleton,
      supportedModuleTypes,
      isResultSuccessful = isResultSuccessful,
      summarize = summarize
    )
}

object TaskBuilder {
  def make[T: JsonRW: Hashable](
      name: String,
      // if it triggers upstream modules task with same name
      transitive: Boolean = false,
      singleton: Boolean = false,
      supportedModuleTypes: Set[ModuleType] = Set.empty
  ): TaskBuilder[T, EmptyTuple] = TaskBuilder(name, EmptyTuple, transitive, singleton, supportedModuleTypes)
}

case class CachedTaskBuilder[T: JsonRW: Hashable, Deps <: Tuple] private (
    name: String,
    taskDeps: Deps,
    // if it triggers upstream modules task with same name
    transitive: Boolean,
    singleton: Boolean,
    supportedModuleTypes: Set[ModuleType]
)(using ev: TaskDeps[Deps] =:= true) {
  def dependsOn[T2](t: AbstractTask[T2]): CachedTaskBuilder[T, Deps :* AbstractTask[T2]] =
    CachedTaskBuilder(name, taskDeps :* t, transitive, singleton, supportedModuleTypes)

  def build(execute: TaskExecContext[T, Deps] => T)(using Deps <:< NonEmptyTuple): Task[T, Deps] =
    CachedTask(name, execute, taskDeps, transitive, singleton, supportedModuleTypes)
}

object CachedTaskBuilder {
  def make[T: JsonRW: Hashable](
      name: String,
      // if it triggers upstream modules task with same name
      transitive: Boolean = false,
      singleton: Boolean = false,
      supportedModuleTypes: Set[ModuleType] = Set.empty
  ): CachedTaskBuilder[T, EmptyTuple] = CachedTaskBuilder(name, EmptyTuple, transitive, singleton, supportedModuleTypes)
}

// this is to make sure that Deps are AbstractTask-s and not arbitrary types
type TaskDeps[T <: Tuple] <: Boolean = T match {
  case EmptyTuple           => true
  case t :* AbstractTask[?] => TaskDeps[t]
  case _                    => false
}

type TaskDepResults[T <: Tuple] <: Tuple = T match {
  case EmptyTuple                => EmptyTuple
  case AbstractTask[t] *: rest   => t *: TaskDepResults[rest]
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
    out: os.Path,
    dependencyResolver: DependencyResolverApi
)(using ev: TaskDeps[Deps] =:= true)

/** Public-facing base for a task, without exposing the `Deps` type parameter.
 *  Use this type in plugin APIs and `CoreTasksApi` so callers don't need to
 *  know (or spell out) the dependency tuple.
 */
trait AbstractTask[T] {
  def name: String
  def description: String
  def transitive: Boolean
  def singleton: Boolean
  def supportedModuleTypes: Set[ModuleType]
  def isResultSuccessful: T => Boolean
}

sealed trait Task[T, Deps <: Tuple](using val rw: JsonRW[T], ev: TaskDeps[Deps] =:= true)
    extends AbstractTask[T] {
  type Res = T
  def taskDeps: Deps
  def execute: TaskExecContext[T, Deps] => T
  def summarize: (Seq[(DederModule, T)], ServerNotificationsLogger) => Unit
  override def isResultSuccessful: T => Boolean = _ => true
  private[deder] def executeUnsafe(
      project: DederProject,
      module: DederModule,
      depResults: Seq[TaskResult[?]],
      transitiveResults: Seq[Seq[TaskResult[?]]],
      args: Seq[String],
      watch: Boolean,
      serverNotificationsLogger: ServerNotificationsLogger,
      dependencyResolver: DependencyResolverApi
  ): (res: TaskResult[T], changed: Boolean)

  /** Type-erased summarize for use by the execution engine */
  private[deder] def summarizeUnsafe(
      results: Seq[(DederModule, Any)],
      serverNotificationsLogger: ServerNotificationsLogger
  ): Unit = summarize(results.asInstanceOf[Seq[(DederModule, T)]], serverNotificationsLogger)

  /** Type-erased success check for use by the execution engine */
  private[deder] def isResultSuccessfulUnsafe(result: Any): Boolean =
    isResultSuccessful(result.asInstanceOf[T])
}

class TaskImpl[T: JsonRW: Hashable, Deps <: Tuple](
    val name: String,
    val execute: TaskExecContext[T, Deps] => T,
    val taskDeps: Deps = EmptyTuple,
    // if it triggers upstream modules task with same name
    // the only way to reference a task across modules
    val transitive: Boolean = false,
    val singleton: Boolean = false,
    val supportedModuleTypes: Set[ModuleType] = Set.empty,
    val description: String = "",
    override val isResultSuccessful: T => Boolean = (_: T) => true,
    val summarize: (Seq[(DederModule, T)], ServerNotificationsLogger) => Unit =
      (_: Seq[(DederModule, T)], _: ServerNotificationsLogger) => ()
)(using ev: TaskDeps[Deps] =:= true)
    extends Task[T, Deps] {
  override private[deder] def executeUnsafe(
      project: DederProject,
      module: DederModule,
      depResults: Seq[TaskResult[?]],
      transitiveResults: Seq[Seq[TaskResult[?]]],
      args: Seq[String],
      watch: Boolean,
      serverNotificationsLogger: ServerNotificationsLogger,
      dependencyResolver: DependencyResolverApi
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
        outDir,
        dependencyResolver
      )
    )
    val taskResult = TaskResult(res, "", Hashable[T].hashStr(res))
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
    val singleton: Boolean = false,
    val supportedModuleTypes: Set[ModuleType] = Set.empty,
    val description: String = "",
    val summarize: (Seq[(DederModule, T)], ServerNotificationsLogger) => Unit =
      (_: Seq[(DederModule, T)], _: ServerNotificationsLogger) => ()
)(using ev: TaskDeps[Deps] =:= true)
    extends Task[T, Deps] {

  private[deder] override def executeUnsafe(
      project: DederProject,
      module: DederModule,
      depResults: Seq[TaskResult[?]],
      transitiveResults: Seq[Seq[TaskResult[?]]],
      args: Seq[String],
      watch: Boolean,
      serverNotificationsLogger: ServerNotificationsLogger,
      dependencyResolver: DependencyResolverApi
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
          outDir,
          dependencyResolver
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
      try {
        val cachedTaskResult = os.read(metadataFile).parseJson[TaskResult[T]]
        val hasDeps = allDepResults.nonEmpty
        val newRes = if hasDeps && inputsHash == cachedTaskResult.inputsHash then
          serverNotificationsLogger.add(
            ServerNotification.logDebug(s"Using cached result for ${name}", Some(module.id))
          )
          cachedTaskResult
        else computeTaskResult()
        val changed = newRes.outputHash != cachedTaskResult.outputHash
        (newRes, changed)
      } catch {
        case _: TupsonException =>
          // if metadata file is corrupted, just recompute
          (computeTaskResult(), true)
      }
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
    execute: TaskExecContext[DederPath, EmptyTuple] => DederPath,
    description: String = ""
) extends TaskImpl[DederPath, EmptyTuple](
      name,
      execute,
      taskDeps = EmptyTuple,
      transitive = false,
      singleton = false,
      supportedModuleTypes,
      description
    ) {
  override def toString(): String = s"SourceFileTask($name)"
}

class SourceFilesTask(
    name: String,
    execute: TaskExecContext[Seq[DederPath], EmptyTuple] => Seq[DederPath],
    supportedModuleTypes: Set[ModuleType] = Set.empty,
    description: String = ""
) extends TaskImpl[Seq[DederPath], EmptyTuple](
      name,
      execute,
      taskDeps = EmptyTuple,
      transitive = false,
      singleton = false,
      supportedModuleTypes,
      description
    ) {
  override def toString(): String = s"SourceFilesTask($name)"
}

class ConfigValueTask[T: JsonRW: Hashable](
    name: String,
    execute: TaskExecContext[T, EmptyTuple] => T,
    supportedModuleTypes: Set[ModuleType] = Set.empty,
    description: String = ""
) extends TaskImpl[T, EmptyTuple](
      name,
      execute,
      taskDeps = EmptyTuple,
      transitive = false,
      singleton = false,
      supportedModuleTypes,
      description
    ) {
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
