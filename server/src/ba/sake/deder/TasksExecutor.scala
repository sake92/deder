package ba.sake.deder

import java.util
import java.util.Collections
import java.util.concurrent.{Callable, CopyOnWriteArrayList, ExecutorService}
import scala.util.control.NonFatal
import scala.jdk.CollectionConverters.*
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import org.jgrapht.graph.{DefaultEdge, SimpleDirectedGraph}
import com.typesafe.scalalogging.StrictLogging
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.DederModule

class TasksExecutor(
    projectConfig: DederProject,
    modulesGraph: SimpleDirectedGraph[DederModule, DefaultEdge],
    tasksGraph: SimpleDirectedGraph[TaskInstance, DefaultEdge],
    tasksExecutorService: ExecutorService
) extends StrictLogging {

  def execute(
      stages: Seq[Seq[TaskInstance]],
      args: Seq[String],
      serverNotificationsLogger: ServerNotificationsLogger
  ): (res: Any, changed: Boolean) = {
    var taskResults = Map.empty[String, TaskResult[?]] // taskInstance.id -> TaskResult
    var finalTaskResult: (res: Any, changed: Boolean) = (null, false)
    for (taskInstances, stageIndex) <- stages.zipWithIndex do {
      val taskExecutions: Seq[Callable[(String, TaskResult[?])]] = for taskInstance <- taskInstances yield {
        val allTaskDeps = tasksGraph.outgoingEdgesOf(taskInstance).asScala.toSeq
        val depResults = allTaskDeps.flatMap { depEdge =>
          val d = tasksGraph.getEdgeTarget(depEdge)
          val depRes = taskResults(d.id)
          Option.when(d.module == taskInstance.module)(depRes)
        }

        val transitiveResults = {
          var transitiveResultsMap = Map.empty[Int, Seq[(String, TaskResult[?])]]
          var maxDepth = 0
          def go(taskInstance: TaskInstance, depth: Int): Unit = {
            if depth > maxDepth then maxDepth = depth
            val taskRes = taskResults(taskInstance.id)
            transitiveResultsMap = transitiveResultsMap.updatedWith(depth) {
              case Some(values) =>
                Some(
                  if values.exists(_._1 == taskInstance.id) then values else values.appended(taskInstance.id -> taskRes)
                )
              case None => Some(Seq(taskInstance.id -> taskRes))
            }
            val depEdges = tasksGraph.outgoingEdgesOf(taskInstance).asScala.toSeq
            depEdges.foreach { depEdge =>
              val d = tasksGraph.getEdgeTarget(depEdge)
              if d.module != taskInstance.module then go(d, depth + 1)
            }
          }
          allTaskDeps.foreach { depEdge =>
            val d = tasksGraph.getEdgeTarget(depEdge)
            if d.module != taskInstance.module then go(d, 0)
          }
          val transitiveResults = for i <- 0 to maxDepth yield transitiveResultsMap.getOrElse(i, Seq.empty)
          transitiveResults.map(_.sortBy(_._1).map(_._2))
        }

        () =>
          try {
            val (taskRes, changed) = taskInstance.task
              .executeUnsafe(
                projectConfig,
                taskInstance.module,
                depResults,
                transitiveResults,
                args,
                serverNotificationsLogger
              )
            finalTaskResult = (taskRes.value, changed) // in last stage, last task's result will be returned
            taskInstance.id -> taskRes
          } catch {
            case NonFatal(e) =>
              logger.error(s"Error during execution of task ${taskInstance.id}", e)
              throw e
          }
      }
      val futures = taskExecutions.map(tasksExecutorService.submit)
      val results =
        try {
          futures.map(f => f.get())
        } catch {
          case NonFatal(e) =>
            // if one task fails, cancel all other tasks in this stage
            futures.foreach(_.cancel(true))
            throw e
        }
      taskResults ++= results
    }
    finalTaskResult
  }
}
