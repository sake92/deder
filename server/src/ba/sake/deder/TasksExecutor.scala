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

    for (taskInstances, stageIndex) <- stages.zipWithIndex do {
      val stageSpan = OTEL.TRACER.spanBuilder(s"Stage $stageIndex").startSpan()
      try {
        Using.resource(stageSpan.makeCurrent()) { _ =>
          val (skipped, executable) = taskInstances.partition(ti => moduleFailures.contains(ti.moduleId))

          skipped.foreach { ti =>
            if isTargetTask(ti, taskName, moduleIds) then
              targetResults += TaskExecResult.Skipped(ti, moduleFailures(ti.moduleId))
          }

          val outcomes: Seq[ExecutionOutcome] = supervised {
            val taskResultsSnapshot = taskResults.toMap
            executable.map { ti =>
              forkUser {
                executeSingleTask(ti, projectConfig, taskResultsSnapshot, args, watch, serverNotificationsLogger, dependencyResolver)
              }
            }.map(_.join())
          }

          outcomes.foreach { oc =>
            oc.taskResult.foreach(tr => taskResults += (oc.result.taskInstance.id -> tr))
            oc.result match {
              case s: TaskExecResult.Success =>
                if isTargetTask(s.taskInstance, taskName, moduleIds) then targetResults += s
              case f: TaskExecResult.Failure =>
                moduleFailures.getOrElseUpdate(f.taskInstance.moduleId, f)
                if isTargetTask(f.taskInstance, taskName, moduleIds) then targetResults += f
              case _: TaskExecResult.Skipped =>
            }
          }
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

    val taskStartNanos = System.nanoTime()
    val taskSpan = OTEL.TRACER.spanBuilder(ti.id).startSpan()
    try {
      Using.resource(taskSpan.makeCurrent()) { scope =>
        val (taskRes, changed) = ti.task.executeUnsafe(
          projectConfig, ti.module, depResults, transitiveResults,
          args, watch, serverNotificationsLogger, dependencyResolver
        )
        val taskDuration = Duration.ofNanos(System.nanoTime() - taskStartNanos)
        internals.recordTaskExecution(ti.task.name, taskDuration, !changed)
        ExecutionOutcome(TaskExecResult.Success(ti, taskRes.value, changed), Some(taskRes))
      }
    } catch {
      case NonFatal(e) =>
        val taskDuration = Duration.ofNanos(System.nanoTime() - taskStartNanos)
        internals.recordTaskExecution(ti.task.name, taskDuration, cacheHit = false)
        logger.error(s"Error during execution of task ${ti.id}", e)
        taskSpan.recordException(e)
        taskSpan.setStatus(StatusCode.ERROR)
        val errMsg = Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
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
}

enum TaskExecResult:
  case Success(taskInstance: TaskInstance, value: Any, changed: Boolean)
  case Failure(taskInstance: TaskInstance, error: String)
  case Skipped(taskInstance: TaskInstance, because: Failure)
