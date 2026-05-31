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
   // tasksExecutorService: ExecutorService,
    dependencyResolver: DependencyResolver,
    internals: DederProjectInternalsImpl
) extends StrictLogging {

  // (taskInstance.id, Option[TaskResult], changed, Option[errorMessage])
  private type StageResult = (String, Option[TaskResult[?]], Boolean, Option[String])

  def execute(
      stages: Seq[Seq[TaskInstance]],
      moduleIds: Seq[String],
      taskName: String,
      args: Seq[String],
      watch: Boolean,
      serverNotificationsLogger: ServerNotificationsLogger
  ): Seq[TaskExecResult] = {
    var taskResults = Map.empty[String, TaskResult[?]] // taskInstance.id -> TaskResult
    val finalTaskResults = Seq.newBuilder[TaskExecResult]
    val failedModuleIds = ConcurrentHashMap.newKeySet[String]()
    for (taskInstances, stageIndex) <- stages.zipWithIndex do {
      val stageSpan = OTEL.TRACER.spanBuilder(s"Stage $stageIndex").startSpan()
      try {
        Using.resource(stageSpan.makeCurrent()) { _ =>
          // filter out tasks belonging to already-failed modules
          val executableTasks = taskInstances.filterNot(ti => failedModuleIds.contains(ti.moduleId))
          // record skipped tasks (for failed modules) with error results
          val skippedTasks = taskInstances.filter(ti => failedModuleIds.contains(ti.moduleId))
          skippedTasks.foreach { ti =>
            if ti.task.name == taskName && moduleIds.contains(ti.moduleId) then
              finalTaskResults.addOne(
                TaskExecResult(ti, null, changed = false, moduleError = Some(s"Module ${ti.moduleId} skipped due to earlier failure"))
              )
          }
          val taskExecutions: Seq[() => StageResult] = for taskInstance <- executableTasks yield {
            val allTaskDeps = tasksGraph.outgoingEdgesOf(taskInstance).asScala.toSeq
            val depResults = allTaskDeps.flatMap { depEdge =>
              val d = tasksGraph.getEdgeTarget(depEdge)
              val depRes = taskResults(d.id)
              Option.when(d.module == taskInstance.module)(depRes)
            }
            val transitiveResults = getTransitiveResults(taskInstance, taskResults, allTaskDeps)

            () =>
              val taskStartNanos = System.nanoTime()
              val taskSpan = OTEL.TRACER.spanBuilder(taskInstance.id).startSpan()
              try {
                Using.resource(taskSpan.makeCurrent()) { scope =>
                  val (taskRes, changed) = taskInstance.task
                    .executeUnsafe(
                      projectConfig,
                      taskInstance.module,
                      depResults,
                      transitiveResults,
                      args,
                      watch,
                      serverNotificationsLogger,
                      dependencyResolver
                    )
                  val taskDuration = Duration.ofNanos(System.nanoTime() - taskStartNanos)
                  internals.recordTaskExecution(taskInstance.task.name, taskDuration, !changed)
                  (taskInstance.id, Some(taskRes), changed, None)
                }
              } catch {
                case NonFatal(e) =>
                  val taskDuration = Duration.ofNanos(System.nanoTime() - taskStartNanos)
                  internals.recordTaskExecution(taskInstance.task.name, taskDuration, cacheHit = false)
                  logger.error(s"Error during execution of task ${taskInstance.id}", e)
                  taskSpan.recordException(e)
                  taskSpan.setStatus(StatusCode.ERROR)
                  // mark the module as failed — other modules continue
                  failedModuleIds.add(taskInstance.moduleId)
                  (taskInstance.id, None, false, Some(Option(e.getMessage).getOrElse(e.getClass.getSimpleName)))
              } finally {
                taskSpan.end()
              }
          }
          val results: Seq[StageResult] = supervised {
            val forks = taskExecutions.map(te => forkUser { te() })
            forks.map(_.join())
          }
          // collect successful results for dependency resolution
          val goodResults = results.collect { case (id, Some(taskRes), changed, None) => (id, taskRes, changed) }
          taskResults ++= goodResults.map { case (id, taskRes, _) => id -> taskRes }
          // record failed target tasks with error info
          val failedResults = results.collect { case (id, None, _, Some(errMsg)) => (id, errMsg) }
          failedResults.foreach { case (id, errMsg) =>
            val ti = executableTasks.find(_.id == id).get
            if ti.task.name == taskName && moduleIds.contains(ti.moduleId) then
              finalTaskResults.addOne(TaskExecResult(ti, null, changed = false, moduleError = Some(errMsg)))
          }
          // collect final task results on the caller thread (after all futures have completed)
          // to avoid a data race: multiple worker threads writing to finalTaskResults concurrently
          for (taskInstance, (_, taskResOpt, changed, errorOpt)) <- executableTasks.zip(results) do
            if taskInstance.task.name == taskName && moduleIds.contains(taskInstance.moduleId) && errorOpt.isEmpty then
              finalTaskResults.addOne(TaskExecResult(taskInstance, taskResOpt.get.value, changed))
        }
      } catch {
        case NonFatal(e) =>
          stageSpan.recordException(e)
          stageSpan.setStatus(StatusCode.ERROR)
          // don't re-throw — module-level failures are already handled above
      } finally stageSpan.end()
    }
    finalTaskResults.result()
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

case class TaskExecResult(
    taskInstance: TaskInstance,
    res: Any,
    changed: Boolean,
    moduleError: Option[String] = None
)
