package ba.sake.deder

import scala.util.control.Breaks.{break, breakable}
import scala.Tuple.:*
import java.time.Duration
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import os.write.over
import ba.sake.tupson.{*, given}
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.{DederModule, ModuleType}
import ba.sake.deder.deps.DependencyResolverApi

enum TaskKind {
  case Standard, SourceGenerator, ResourceGenerator
}

case class TaskBuilder[T: JsonRW: Hashable, Deps <: Tuple, S] private (
    name: String,
    taskDeps: Deps,
    // if it triggers upstream modules task with same name
    transitive: Boolean,
    singleton: Boolean,
    supportedModuleTypes: Set[ModuleType],
    enabled: PartialFunction[DederModule, Boolean],
    category: String,
    kind: TaskKind,
    internal: Boolean
)(using ev: TaskDeps[Deps] =:= true, summarizable: Summarizable[T, S]) {
  def dependsOn[T2](t: AbstractTask[T2]): TaskBuilder[T, Deps :* AbstractTask[T2], S] =
    TaskBuilder(name, taskDeps :* t, transitive, singleton, supportedModuleTypes, enabled, category, kind, internal)

  def build(execute: TaskExecContext[T, Deps] => T): Task[T, Deps, S] =
    TaskImpl(
      name,
      execute,
      taskDeps,
      transitive,
      singleton,
      supportedModuleTypes,
      enabled = enabled,
      category = category,
      kind = kind,
      internal = internal
    )

  def buildSummarized[S2](
      execute: TaskExecContext[T, Deps] => T,
      isResultSuccessful: T => Boolean = _ => true
  )(using Summarizable[T, S2]): Task[T, Deps, S2] =
    TaskImpl(
      name,
      execute,
      taskDeps,
      transitive,
      singleton,
      supportedModuleTypes,
      enabled = enabled,
      category = category,
      kind = kind,
      isResultSuccessful = isResultSuccessful,
      internal = internal
    )
}

object TaskBuilder {
  def make[T: JsonRW: Hashable](
      name: String,
      // if it triggers upstream modules task with same name
      transitive: Boolean = false,
      singleton: Boolean = false,
      supportedModuleTypes: Set[ModuleType] = Set.empty,
      enabled: PartialFunction[DederModule, Boolean] = { case _ => true },
      category: String = "",
      kind: TaskKind = TaskKind.Standard,
      internal: Boolean = false
  )(using Summarizable[T, MultiModuleResults[T]]): TaskBuilder[T, EmptyTuple, MultiModuleResults[T]] =
    new TaskBuilder[T, EmptyTuple, MultiModuleResults[T]](
      name,
      EmptyTuple,
      transitive,
      singleton,
      supportedModuleTypes,
      enabled,
      category,
      kind,
      internal
    )

}

case class CachedTaskBuilder[T: JsonRW: Hashable, Deps <: Tuple, S] private (
    name: String,
    taskDeps: Deps,
    // if it triggers upstream modules task with same name
    transitive: Boolean,
    singleton: Boolean,
    supportedModuleTypes: Set[ModuleType],
    enabled: PartialFunction[DederModule, Boolean],
    category: String,
    kind: TaskKind,
    internal: Boolean,
    cliReplayFn: Option[(T, String, ServerNotificationsLogger) => Unit] = None
)(using ev: TaskDeps[Deps] =:= true, summarizable: Summarizable[T, S]) {
  def dependsOn[T2](t: AbstractTask[T2]): CachedTaskBuilder[T, Deps :* AbstractTask[T2], S] =
    CachedTaskBuilder(
      name,
      taskDeps :* t,
      transitive,
      singleton,
      supportedModuleTypes,
      enabled,
      category,
      kind,
      internal,
      cliReplayFn
    )

  def withCliReplay(f: (T, String, ServerNotificationsLogger) => Unit): CachedTaskBuilder[T, Deps, S] =
    CachedTaskBuilder(
      name,
      taskDeps,
      transitive,
      singleton,
      supportedModuleTypes,
      enabled,
      category,
      kind,
      internal,
      cliReplayFn = Some(f)
    )

  /** Requires nonempty dependencies because caching only makes sense if there are inputs to hash. If there are no
    * dependencies, the task will always execute...
    */
  def build(execute: TaskExecContext[T, Deps] => T)(using Deps <:< NonEmptyTuple): Task[T, Deps, S] =
    CachedTask(
      name,
      execute,
      taskDeps,
      transitive,
      singleton,
      supportedModuleTypes,
      enabled = enabled,
      category = category,
      kind = kind,
      internal = internal,
      cliReplayFn = cliReplayFn
    )

  // TODO can we do this in build function?
  /** Like [[build]], but attaches a custom summary type `S2` instead of the default `MultiModuleResults[T]`. Requires
    * nonempty dependencies (caching needs inputs to hash).
    */
  def buildSummarized[S2](
      execute: TaskExecContext[T, Deps] => T,
      isResultSuccessful: T => Boolean = (_: T) => true
  )(using Deps <:< NonEmptyTuple, Summarizable[T, S2]): Task[T, Deps, S2] =
    CachedTask(
      name,
      execute,
      taskDeps,
      transitive,
      singleton,
      supportedModuleTypes,
      enabled = enabled,
      category = category,
      kind = kind,
      isResultSuccessful = isResultSuccessful,
      internal = internal,
      cliReplayFn = cliReplayFn
    )
}

object CachedTaskBuilder {
  def make[T: JsonRW: Hashable](
      name: String,
      // if it triggers upstream modules task with same name
      transitive: Boolean = false,
      singleton: Boolean = false,
      supportedModuleTypes: Set[ModuleType] = Set.empty,
      enabled: PartialFunction[DederModule, Boolean] = { case _ => true },
      category: String = "",
      kind: TaskKind = TaskKind.Standard,
      internal: Boolean = false
  )(using Summarizable[T, MultiModuleResults[T]]): CachedTaskBuilder[T, EmptyTuple, MultiModuleResults[T]] =
    new CachedTaskBuilder[T, EmptyTuple, MultiModuleResults[T]](
      name,
      EmptyTuple,
      transitive,
      singleton,
      supportedModuleTypes,
      enabled,
      category,
      kind,
      internal
    )
}

// this is to make sure that Deps are AbstractTask-s and not arbitrary types
type TaskDeps[T <: Tuple] <: Boolean = T match {
  case EmptyTuple           => true
  case t :* AbstractTask[?] => TaskDeps[t]
  case _                    => false
}

type TaskDepResults[T <: Tuple] <: Tuple = T match {
  case EmptyTuple              => EmptyTuple
  case AbstractTask[t] *: rest => t *: TaskDepResults[rest]
}

// needs a T because of transitive results
case class TaskExecContext[T, Deps <: Tuple](
    project: DederProject,
    module: DederModule,
    depResults: TaskDepResults[Deps],
    transitiveResults: Seq[Seq[T]], // results from dependent modules
    dependentModulesTree: Seq[Seq[DederModule]], // transitive module deps, topological levels, nearest-first
    args: Seq[String], // external args, like run args
    watch: Boolean,
    notifications: ServerNotificationsLogger,
    out: os.Path,
    dependencyResolver: DependencyResolverApi,
    // This task's own cache inputsHash (hash of all dependency outputHashes + args). Set for
    // CachedTask; empty for always-run tasks. Lets a producer embed it in its result as a stable,
    // cheap "output token" (see CompileResult.inputsHash) instead of content-hashing big outputs.
    inputsHash: String = ""
)(using ev: TaskDeps[Deps] =:= true)

/** Public-facing base trait for a task, without exposing the `Deps` type parameter. Use this type in plugin APIs so
  * callers don't need to know (or spell out) the dependency tuple.
  */
sealed trait AbstractTask[T] {
  def name: String
  def description: String
  def category: String
  def kind: TaskKind
  def supportedModuleTypes: Set[ModuleType]
  def enabled: PartialFunction[DederModule, Boolean]
  def transitive: Boolean
  def singleton: Boolean

  /** When true, this task is hidden from listing/completion/plan output but can still be executed directly by name.
    */
  def internal: Boolean = false

  def featureTags: Seq[FeatureTag] =
    val b = Seq.newBuilder[FeatureTag]
    if this.isInstanceOf[SourceFileTask] || this.isInstanceOf[SourceFilesTask] then b += FeatureTag.SourceAware
    if this.isInstanceOf[ConfigValueTask[?]] then b += FeatureTag.ConfigAware
    if this.isInstanceOf[FanInTask[?]] then b += FeatureTag.FanIn
    if this.isInstanceOf[CachedTask[?, ?, ?]] then b += FeatureTag.Cached
    b.result()

  def isResultSuccessful: T => Boolean
}

sealed trait Task[T, Deps <: Tuple, S](using
    val rw: JsonRW[T],
    val summarizable: Summarizable[T, S],
    ev: TaskDeps[Deps] =:= true
) extends AbstractTask[T] {
  type Res = T
  def castRes(res: Any): T = res.asInstanceOf[T]

  def taskDeps: Deps

  /** Tasks whose results should be appended to depResults at execution time, computed from the registry rather than
    * statically declared via `dependsOn`. Default: empty. Used by FanInTask to collect all tasks of a given kind for
    * the current module.
    */
  def dynamicDeps(siblingTasks: Seq[Task[?, ?, ?]], moduleType: ModuleType): Seq[Task[?, ?, ?]] = Seq.empty
  def execute: TaskExecContext[T, Deps] => T
  override def isResultSuccessful: T => Boolean = _ => true
  private[deder] def executeUnsafe(
      project: DederProject,
      module: DederModule,
      depResults: Seq[TaskResult[?]],
      transitiveResults: Seq[Seq[TaskResult[?]]],
      dependentModulesTree: Seq[Seq[DederModule]],
      args: Seq[String],
      watch: Boolean,
      serverNotificationsLogger: ServerNotificationsLogger,
      dependencyResolver: DependencyResolverApi
  ): (res: TaskResult[T], changed: Boolean, fromCache: Boolean)

  /** Type-erased cross-module aggregation returning the summary value. */
  private[deder] def summarizeValueUnsafe(
      results: Seq[(String, Any)],
      failures: Seq[ModuleFailure],
      totalDuration: Duration
  ): S =
    summarizable.summarize(results.asInstanceOf[Seq[(String, T)]], failures, totalDuration)

  /** Type-erased success check for use by the execution engine */
  private[deder] def isResultSuccessfulUnsafe(result: Any): Boolean =
    isResultSuccessful(result.asInstanceOf[T])
}

class TaskImpl[T: JsonRW: Hashable, Deps <: Tuple, S](
    val name: String,
    val execute: TaskExecContext[T, Deps] => T,
    val taskDeps: Deps = EmptyTuple,
    // if it triggers upstream modules task with same name
    // the only way to reference a task across modules
    val transitive: Boolean = false,
    val singleton: Boolean = false,
    val supportedModuleTypes: Set[ModuleType] = Set.empty,
    override val enabled: PartialFunction[DederModule, Boolean] = { case _ => true },
    val description: String = "",
    val category: String = "",
    val kind: TaskKind = TaskKind.Standard,
    override val isResultSuccessful: T => Boolean = (_: T) => true,
    override val internal: Boolean = false
)(using
    summarizable: Summarizable[T, S],
    ev: TaskDeps[Deps] =:= true
) extends Task[T, Deps, S](using summon[JsonRW[T]], summarizable, ev) {
  override private[deder] def executeUnsafe(
      project: DederProject,
      module: DederModule,
      depResults: Seq[TaskResult[?]],
      transitiveResults: Seq[Seq[TaskResult[?]]],
      dependentModulesTree: Seq[Seq[DederModule]],
      args: Seq[String],
      watch: Boolean,
      serverNotificationsLogger: ServerNotificationsLogger,
      dependencyResolver: DependencyResolverApi
  ): (res: TaskResult[T], changed: Boolean, fromCache: Boolean) = {
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
        dependentModulesTree,
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
    (taskResult, true, false)
  }

  override def toString(): String = s"TaskImpl($name)"
}

class CachedTask[T: JsonRW: Hashable, Deps <: Tuple, S](
    val name: String,
    val execute: TaskExecContext[T, Deps] => T,
    val taskDeps: Deps = EmptyTuple,
    // if it triggers upstream modules task with same name
    // the only way to reference a task across modules
    val transitive: Boolean = false,
    val singleton: Boolean = false,
    val supportedModuleTypes: Set[ModuleType] = Set.empty,
    override val enabled: PartialFunction[DederModule, Boolean] = { case _ => true },
    val description: String = "",
    val category: String = "",
    val kind: TaskKind = TaskKind.Standard,
    override val isResultSuccessful: T => Boolean = (_: T) => true,
    override val internal: Boolean = false,
    val cliReplayFn: Option[(T, String, ServerNotificationsLogger) => Unit] = None
)(using
    summarizable: Summarizable[T, S],
    ev: TaskDeps[Deps] =:= true
) extends Task[T, Deps, S](using summon[JsonRW[T]], summarizable, ev) {

  override def castRes(res: Any): T = res.asInstanceOf[T]

  private[deder] override def executeUnsafe(
      project: DederProject,
      module: DederModule,
      depResults: Seq[TaskResult[?]],
      transitiveResults: Seq[Seq[TaskResult[?]]],
      dependentModulesTree: Seq[Seq[DederModule]],
      args: Seq[String],
      watch: Boolean,
      serverNotificationsLogger: ServerNotificationsLogger,
      dependencyResolver: DependencyResolverApi
  ): (res: TaskResult[T], changed: Boolean, fromCache: Boolean) = {

    serverNotificationsLogger.add(ServerNotification.logDebug(s"Executing ${name}", Some(module.id)))

    val metadataFile = DederGlobals.projectRootDir / ".deder/out" / module.id / name / "metadata.json"
    val outDir = DederGlobals.projectRootDir / ".deder/out" / module.id / name

    val allDepResults = depResults ++ transitiveResults.headOption.getOrElse(Seq.empty) // only first level for hashing
    val inputsPayload = CacheInputsHash(allDepResults.map(_.outputHash), args)
    val inputsHash = HashUtils.hashStr(inputsPayload.toJson(spaces = 0, sort = true))

    def computeTaskResult(): TaskResult[T] = {
      val depResultsUnsafe = Tuple.fromArray(depResults.map(_.value).toArray).asInstanceOf[TaskDepResults[Deps]]
      val transitiveResultsUnsafe = transitiveResults.asInstanceOf[Seq[Seq[TaskResult[T]]]]
      val res = execute(
        TaskExecContext(
          project,
          module,
          depResultsUnsafe,
          transitiveResultsUnsafe.map(_.map(_.value)),
          dependentModulesTree,
          args,
          watch,
          serverNotificationsLogger,
          outDir,
          dependencyResolver,
          inputsHash
        )
      )
      val outputHash = Hashable[T].hashStr(res)
      val taskResult = TaskResult(res, inputsHash, outputHash)
      os.write.over(metadataFile, taskResult.toJson(spaces = 2, sort = true), createFolders = true)
      serverNotificationsLogger.add(
        ServerNotification.logDebug(s"Computed result for ${name}", Some(module.id))
      )
      taskResult
    }

    if os.exists(metadataFile) then {
      try {
        val cachedTaskResult = os.read(metadataFile).parseJson[TaskResult[T]]
        val hasDeps = allDepResults.nonEmpty
        if hasDeps && inputsHash == cachedTaskResult.inputsHash then
          serverNotificationsLogger.add(
            ServerNotification.logDebug(s"Using cached result for ${name}", Some(module.id))
          )
          (cachedTaskResult, false, true)
        else
          val newRes = computeTaskResult()
          (newRes, newRes.outputHash != cachedTaskResult.outputHash, false)
      } catch {
        case _: TupsonException =>
          // if metadata file is corrupted, just recompute
          (computeTaskResult(), true, false)
      }
    } else {
      (computeTaskResult(), true, false)
    }
  }

  override def toString(): String = s"CachedTask($name)"

}

// specialized task just for source file
// so we can easily retrigger watched tasks
class SourceFileTask(
    name: String,
    supportedModuleTypes: Set[ModuleType] = Set.empty,
    enabled: PartialFunction[DederModule, Boolean] = { case _ => true },
    execute: TaskExecContext[DederPath, EmptyTuple] => DederPath,
    description: String = "",
    category: String = "",
    override val internal: Boolean = false
) extends TaskImpl[DederPath, EmptyTuple, MultiModuleResults[DederPath]](
      name,
      execute,
      taskDeps = EmptyTuple,
      transitive = false,
      singleton = false,
      supportedModuleTypes,
      enabled = enabled,
      description,
      category,
      internal = internal
    ) {
  override def toString(): String = s"SourceFileTask($name)"
}

class SourceFilesTask(
    name: String,
    execute: TaskExecContext[Seq[DederPath], EmptyTuple] => Seq[DederPath],
    supportedModuleTypes: Set[ModuleType] = Set.empty,
    enabled: PartialFunction[DederModule, Boolean] = { case _ => true },
    description: String = "",
    category: String = "",
    override val internal: Boolean = false
) extends TaskImpl[Seq[DederPath], EmptyTuple, MultiModuleResults[Seq[DederPath]]](
      name,
      execute,
      taskDeps = EmptyTuple,
      transitive = false,
      singleton = false,
      supportedModuleTypes,
      enabled = enabled,
      description,
      category,
      internal = internal
    ) {
  override def toString(): String = s"SourceFilesTask($name)"
}

class ConfigValueTask[T: JsonRW: Hashable](
    name: String,
    execute: TaskExecContext[T, EmptyTuple] => T,
    supportedModuleTypes: Set[ModuleType] = Set.empty,
    enabled: PartialFunction[DederModule, Boolean] = { case _ => true },
    description: String = "",
    category: String = "",
    override val internal: Boolean = false
) extends TaskImpl[T, EmptyTuple, MultiModuleResults[T]](
      name,
      execute,
      taskDeps = EmptyTuple,
      transitive = false,
      singleton = false,
      supportedModuleTypes,
      enabled = enabled,
      description,
      category,
      internal = internal
    ) {
  override def toString(): String = s"ConfigValueTask($name)"
}

/** Aggregator task: at DAG-build time, depends on every registered task with matching `collectKind` for the current
  * module's type. Result is the Seq of contributors' results. Empty Seq if no contributors.
  */
class FanInTask[T: JsonRW: Hashable](
    val name: String,
    val collectKind: TaskKind,
    val supportedModuleTypes: Set[ModuleType] = Set.empty,
    val enabled: PartialFunction[DederModule, Boolean] = { case _ => true },
    val description: String = "",
    val category: String = "",
    override val internal: Boolean = false
)(using S: Summarizable[Seq[T], MultiModuleResults[Seq[T]]])
    extends Task[Seq[T], EmptyTuple, MultiModuleResults[Seq[T]]](using
      summon[JsonRW[Seq[T]]],
      S,
      summon[TaskDeps[EmptyTuple] =:= true]
    ) {
  val taskDeps: EmptyTuple = EmptyTuple
  val transitive: Boolean = false
  val singleton: Boolean = false
  val kind: TaskKind = TaskKind.Standard
  val execute: TaskExecContext[Seq[T], EmptyTuple] => Seq[T] = _ => Seq.empty

  override def dynamicDeps(siblingTasks: Seq[Task[?, ?, ?]], moduleType: ModuleType): Seq[Task[?, ?, ?]] =
    siblingTasks.filter { t =>
      t.kind == collectKind &&
      (t.supportedModuleTypes.isEmpty || t.supportedModuleTypes.contains(moduleType))
    }

  override private[deder] def executeUnsafe(
      project: DederProject,
      module: DederModule,
      depResults: Seq[TaskResult[?]],
      transitiveResults: Seq[Seq[TaskResult[?]]],
      dependentModulesTree: Seq[Seq[DederModule]],
      args: Seq[String],
      watch: Boolean,
      serverNotificationsLogger: ServerNotificationsLogger,
      dependencyResolver: DependencyResolverApi
  ): (res: TaskResult[Seq[T]], changed: Boolean, fromCache: Boolean) = {
    serverNotificationsLogger.add(ServerNotification.logDebug(s"Executing ${name}", Some(module.id)))
    val collected =
      try depResults.map(_.value.asInstanceOf[T])
      catch {
        case e: ClassCastException =>
          serverNotificationsLogger.add(
            ServerNotification.logError(
              s"Task ${name} failed because it returns wrong result type for ${collectKind}. This is likely a plugin bug, please report it to the plugin author.",
              Some(module.id),
              source = Some("server")
            )
          )
          throw e
      }
    val outputHash = Hashable[Seq[T]].hashStr(collected)
    (TaskResult(collected, "", outputHash), true, false)
  }

  override def toString(): String = s"FanInTask($name, kind=$collectKind)"
}

// dynamic, for each module
class TaskInstance(
    val module: DederModule,
    val task: Task[?, ?, ?],
    val lock: ReentrantLock
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
  def apply(module: DederModule, task: Task[?, ?, ?]): TaskInstance =
    new TaskInstance(module, task, new ReentrantLock())
}

private case class CacheInputsHash(depOutputHashes: Seq[String], args: Seq[String]) derives JsonRW

enum FeatureTag(val emoji: String, val jsonKey: String, val description: String):
  case SourceAware extends FeatureTag("📁", "source-aware", "watches sources")
  case ConfigAware extends FeatureTag("⚙", "config-aware", "watches config")
  case FanIn extends FeatureTag("🔀", "fan-in", "fan-in")
  case Cached extends FeatureTag("⚡", "cached", "cached")
