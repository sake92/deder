package ba.sake.deder

import java.util.concurrent.{Callable, ConcurrentHashMap, ExecutionException, ExecutorService, TimeUnit}
import java.time.Duration
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal
import scala.util.Using
import org.jgrapht.graph.{DefaultEdge, SimpleDirectedGraph}
import com.typesafe.scalalogging.StrictLogging
import ox.{supervised, forkUser}
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.DederModule
import ba.sake.deder.deps.DependencyResolver
import io.opentelemetry.api.trace.StatusCode

class TasksExecutor(
    projectConfig: DederProject,
    modulesGraph: SimpleDirectedGraph[DederModule, DefaultEdge],
    tasksGraph: SimpleDirectedGraph[TaskInstance, DefaultEdge],
    dependencyResolver: DependencyResolver,
    internals: DederProjectInternalsImpl
) extends StrictLogging {

  private case class ExecutionOutcome(result: TaskExecResult, taskResult: Option[TaskResult[?]])

  private def isTargetTask(ti: TaskInstance, taskName: String, moduleIds: Seq[String]): Boolean =
    ti.task.name == taskName && moduleIds.contains(ti.moduleId)

  def execute(
      requestId: String,
      stages: Seq[Seq[TaskInstance]],
      moduleIds: Seq[String],
      taskName: String,
      args: Seq[String],
      watch: Boolean,
      serverNotificationsLogger: ServerNotificationsLogger
  ): Seq[TaskExecResult] = {
    val taskResults = scala.collection.mutable.Map.empty[String, TaskResult[?]]
    val moduleFailures = scala.collection.mutable.Map.empty[String, TaskExecResult.Failure]
    val targetResults = Seq.newBuilder[TaskExecResult]

    val allCompleted = scala.collection.mutable.ArrayBuffer.empty[String]
    val allFailed = scala.collection.mutable.ArrayBuffer.empty[String]
    val allSkipped = scala.collection.mutable.ArrayBuffer.empty[String]

    def propagateModuleFailure(failedModuleId: String, cause: TaskExecResult.Failure): Unit = {
      val visited = scala.collection.mutable.Set.empty[String]
      val depMsg = cause.taskInstance.moduleId
      def dfs(moduleId: String): Unit = {
        if visited.contains(moduleId) then return
        visited += moduleId
        modulesGraph.vertexSet().asScala.find(_.id == moduleId).foreach { mod =>
          modulesGraph.incomingEdgesOf(mod).asScala.foreach { edge =>
            val dependent = modulesGraph.getEdgeSource(edge)
            moduleFailures.getOrElseUpdate(dependent.id,
              TaskExecResult.Failure(cause.taskInstance, depMsg))
            dfs(dependent.id)
          }
        }
      }
      dfs(failedModuleId)
    }

    for (taskInstances, stageIndex) <- stages.zipWithIndex do {
      val stageSpan = OTEL.TRACER.spanBuilder(s"Stage $stageIndex").startSpan()
      try {
        Using.resource(stageSpan.makeCurrent()) { _ =>
          val (skipped, executable) = taskInstances.partition(ti => moduleFailures.contains(ti.moduleId))

          skipped.foreach { ti =>
            if isTargetTask(ti, taskName, moduleIds) then
              targetResults += TaskExecResult.Skipped(ti, moduleFailures(ti.moduleId))
          }

          // Report stage start
          internals.updateStageProgress(requestId, TaskStageProgress(
            currentStage = stageIndex + 1,
            totalStages = stages.size,
            completed = allCompleted.toSeq,
            failed = allFailed.toSeq,
            skipped = allSkipped.toSeq,
            running = executable.map(_.id),
            pending = if stageIndex + 1 < stages.size then stages(stageIndex + 1).map(_.id) else Seq.empty
          ))

          val outcomes: Seq[ExecutionOutcome] = supervised {
            val taskResultsSnapshot = taskResults.toMap
            executable.map { ti =>
              forkUser {
                executeSingleTask(ti, projectConfig, taskResultsSnapshot, args, watch, serverNotificationsLogger, dependencyResolver)
              }
            }.map(_.join())
          }

          outcomes.foreach { oc =>
            oc.result match {
              case s: TaskExecResult.Success =>
                oc.taskResult.foreach(tr => taskResults += (s.taskInstance.id -> tr))
                if !s.taskInstance.task.isResultSuccessfulUnsafe(s.value) then
                  // Task produced an unsuccessful result (e.g. compile with errors).
                  // Mark module as failed so downstream tasks get skipped, but keep
                  // the Success in targetResults so the task's own summary still works.
                  val f: TaskExecResult.Failure = TaskExecResult.Failure(s.taskInstance, "result was unsuccessful")
                  moduleFailures.getOrElseUpdate(s.taskInstance.moduleId, f)
                  propagateModuleFailure(s.taskInstance.moduleId, f)
                if isTargetTask(s.taskInstance, taskName, moduleIds) then targetResults += s
              case f: TaskExecResult.Failure =>
                moduleFailures.getOrElseUpdate(f.taskInstance.moduleId, f)
                propagateModuleFailure(f.taskInstance.moduleId, f)
                if isTargetTask(f.taskInstance, taskName, moduleIds) then targetResults += f
              case _: TaskExecResult.Skipped =>
            }
          }

          // Update progress accumulators
          outcomes.foreach { oc =>
            oc.result match
              case s: TaskExecResult.Success => allCompleted += s.taskInstance.id
              case f: TaskExecResult.Failure => allFailed += f.taskInstance.id
              case _ =>
          }
          allSkipped ++= skipped.map(_.id)
          
          // Report stage-end progress
          val nextStagePending = if stageIndex + 1 < stages.size then stages(stageIndex + 1).map(_.id) else Seq.empty
          internals.updateStageProgress(requestId, TaskStageProgress(
            currentStage = stageIndex + 1,
            totalStages = stages.size,
            completed = allCompleted.toSeq,
            failed = allFailed.toSeq,
            skipped = allSkipped.toSeq,
            running = Seq.empty,
            pending = nextStagePending
          ))
        }
      } catch {
        case NonFatal(e) =>
          stageSpan.recordException(e)
          stageSpan.setStatus(StatusCode.ERROR)
      } finally stageSpan.end()
    }

    targetResults.result()
  }

  private def executeSingleTask(
      ti: TaskInstance,
      projectConfig: DederProject,
      taskResults: Map[String, TaskResult[?]],
      args: Seq[String],
      watch: Boolean,
      serverNotificationsLogger: ServerNotificationsLogger,
      dependencyResolver: DependencyResolver
  ): ExecutionOutcome = {
    val allTaskDeps = tasksGraph.outgoingEdgesOf(ti).asScala.toSeq
    val depResults = allTaskDeps.flatMap { depEdge =>
      val d = tasksGraph.getEdgeTarget(depEdge)
      val depRes = taskResults(d.id)
      Option.when(d.module == ti.module)(depRes)
    }
    val transitiveResults = getTransitiveResults(ti, taskResults, allTaskDeps)
    val dependentModulesTree = dependentModulesTreeFor(ti.module)

    val taskStartNanos = System.nanoTime()
    val taskSpan = OTEL.TRACER.spanBuilder(ti.id).startSpan()
    try {
      Using.resource(taskSpan.makeCurrent()) { scope =>
        val (taskRes, changed, fromCache) = ti.task.executeUnsafe(
          projectConfig, ti.module, depResults, transitiveResults, dependentModulesTree,
          args, watch, serverNotificationsLogger, dependencyResolver
        )
        val taskDuration = Duration.ofNanos(System.nanoTime() - taskStartNanos)
        val isUnsuccessful = !ti.task.isResultSuccessfulUnsafe(taskRes.value)
        val errMsg = if isUnsuccessful then Some("result was unsuccessful") else None
        internals.recordTaskExecution(ti.task.name, taskDuration, !changed, errorMessage = errMsg)
        ExecutionOutcome(TaskExecResult.Success(ti, taskRes.value, changed, fromCache), Some(taskRes))
      }
    } catch {
      case NonFatal(e) =>
        val taskDuration = Duration.ofNanos(System.nanoTime() - taskStartNanos)
        val errMsg = Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
        internals.recordTaskExecution(ti.task.name, taskDuration, cacheHit = false, errorMessage = Some(errMsg))
        logger.error(s"Error during execution of task ${ti.id}", e)
        taskSpan.recordException(e)
        taskSpan.setStatus(StatusCode.ERROR)
        ExecutionOutcome(TaskExecResult.Failure(ti, errMsg), None)
    } finally {
      taskSpan.end()
    }
  }

  private def getTransitiveResults(
      taskInstance: TaskInstance,
      taskResults: Map[String, TaskResult[?]],
      allTaskDeps: Seq[DefaultEdge]
  ) = {
    var transitiveResultsMap = Map.empty[Int, Seq[(String, TaskResult[?])]]
    var maxDepth = 0
    val visited = scala.collection.mutable.Set.empty[String]
    def go(ti: TaskInstance, depth: Int): Unit = {
      if visited.contains(ti.id) then return
      visited += ti.id
      if depth > maxDepth then maxDepth = depth
      val taskRes = taskResults(ti.id)
      transitiveResultsMap = transitiveResultsMap.updatedWith(depth) {
        case Some(values) =>
          Some(
            if values.exists(_._1 == ti.id) then values else values.appended(ti.id -> taskRes)
          )
        case None => Some(Seq(ti.id -> taskRes))
      }
      val depEdges = tasksGraph.outgoingEdgesOf(ti).asScala.toSeq
      depEdges.foreach { depEdge =>
        val d = tasksGraph.getEdgeTarget(depEdge)
        if d.module != ti.module then go(d, depth + 1)
      }
    }
    allTaskDeps.foreach { depEdge =>
      val d = tasksGraph.getEdgeTarget(depEdge)
      if d.module != taskInstance.module then go(d, 0)
    }
    val transitiveResults = for i <- 0 to maxDepth yield transitiveResultsMap.getOrElse(i, Seq.empty)
    transitiveResults.map(_.sortBy(_._1).map(_._2))
  }

  /** The current module's transitive dependency modules, grouped into topological levels using
    * **longest-path ("max depth")** layering: a module sits one level below the deepest dependent
    * that reaches it. Consequently, flattening the levels yields an order in which every module
    * comes strictly after all modules that depend on it — i.e. shared foundational dependencies
    * come LAST, which is the correct classpath-shadowing order. Each module appears once; sorted
    * by id within a level for determinism. Derived from `modulesGraph` (the DederModule DAG),
    * independent of the task graph — so it is available to every task regardless of `transitive`. */
  private def dependentModulesTreeFor(module: DederModule): Seq[Seq[DederModule]] = {
    val depthOf = scala.collection.mutable.Map.empty[String, Int]
    val byId = scala.collection.mutable.Map.empty[String, DederModule]
    def go(m: DederModule, depth: Int): Unit = {
      val deeper = depthOf.get(m.id).forall(depth > _)
      if deeper then {
        depthOf(m.id) = depth
        byId(m.id) = m
        // re-descend whenever we found a longer path, so descendants are pushed deeper too
        // (DAG is acyclic — checked in TasksResolver — so this terminates)
        modulesGraph.outgoingEdgesOf(m).asScala.foreach(e => go(modulesGraph.getEdgeTarget(e), depth + 1))
      }
    }
    modulesGraph.outgoingEdgesOf(module).asScala.foreach(e => go(modulesGraph.getEdgeTarget(e), 0))
    if depthOf.isEmpty then Seq.empty
    else {
      val grouped = depthOf.toSeq.groupBy(_._2)
      (0 to depthOf.values.max)
        .map(d => grouped.getOrElse(d, Seq.empty).map(_._1).sorted.flatMap(byId.get))
        .filter(_.nonEmpty)
    }
  }
}

enum TaskExecResult:
  case Success(taskInstance: TaskInstance, value: Any, changed: Boolean, fromCache: Boolean)
  case Failure(taskInstance: TaskInstance, error: String)
  case Skipped(taskInstance: TaskInstance, because: Failure)
