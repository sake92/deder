package ba.sake.deder

import scala.jdk.CollectionConverters.*
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.DederModule
import org.jgrapht.graph.{DefaultEdge, SimpleDirectedGraph}

import java.util
import java.util.Collections
import java.util.concurrent.{Callable, CopyOnWriteArrayList, ExecutorService}

class TasksExecutor(
    projectConfig: DederProject,
    modulesGraph: SimpleDirectedGraph[DederModule, DefaultEdge],
    tasksGraph: SimpleDirectedGraph[TaskInstance, DefaultEdge],
    tasksExecutorTP: ExecutorService
) {

  def execute(stages: Seq[Seq[TaskInstance]], logCallback: ServerNotification => Unit): Unit = {
    val serverNotificationsLogger = ServerNotificationsLogger(logCallback)
    var taskResults = Map.empty[String, TaskResult[?]] // taskInstance.id -> TaskResult
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

        () => {
          val taskRes = taskInstance.task
            .executeUnsafe(
              projectConfig,
              taskInstance.module,
              depResults,
              transitiveResults,
              serverNotificationsLogger
            )
          taskInstance.id -> taskRes
        }
      }
      val futures = taskExecutions.map(tasksExecutorTP.submit)
      val results = futures.map(_.get())
      taskResults ++= results
    }
    // TODO propagate errors to client
    serverNotificationsLogger.add(ServerNotification.RequestFinished(success = true))
  }
}
