package ba.sake.deder

import scala.jdk.CollectionConverters.*
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.DederModule
import org.jgrapht.graph.{DefaultEdge, SimpleDirectedGraph}

class TasksExecutor(
    projectConfig: DederProject,
    tasksGraph: SimpleDirectedGraph[TaskInstance, DefaultEdge]
) {

  def execute(stages: Seq[Seq[TaskInstance]], logCallback: ServerNotification => Unit): Unit = {
    val serverNotificationsLogger = ServerNotificationsLogger(logCallback)
    var taskResults = Map.empty[String, TaskResult[?]]
    for taskInstances <- stages do {
      val taskExecutions = for taskInstance <- taskInstances yield { () =>
        val allTaskDeps = tasksGraph.outgoingEdgesOf(taskInstance).asScala.toSeq
        val (depResultOpts, transitiveResultOpts) = allTaskDeps.map { depEdge =>
          val d = tasksGraph.getEdgeTarget(depEdge)
          val depRes = taskResults(d.id)
          if d.module == taskInstance.module then Some(depRes) -> None
          else None -> Some(depRes)
        }.unzip
        val depResults = depResultOpts.flatten
        val transitiveResults = transitiveResultOpts.flatten
        val taskRes = taskInstance.task
          .executeUnsafe(projectConfig, taskInstance.module, depResults, transitiveResults, serverNotificationsLogger)
        taskInstance.id -> taskRes
      }
      val results = ox.par(taskExecutions)
      taskResults ++= results
    }
    serverNotificationsLogger.add(ServerNotification.RequestFinished(success = true))
  }
}
