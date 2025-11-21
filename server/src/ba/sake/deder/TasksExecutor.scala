package ba.sake.deder

import scala.jdk.CollectionConverters.*
import ba.sake.deder.config.DederProject
import org.jgrapht.graph.{DefaultEdge, SimpleDirectedGraph}

class TasksExecutor(
    projectConfig: DederProject,
    tasksGraph: SimpleDirectedGraph[TaskInstance, DefaultEdge]
) {

  def execute(stages: Seq[Seq[TaskInstance]]): Unit = {
    // println("Starting execution... " + Instant.now())
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
        // println(s"Executing ${taskInstance.id} with args: ${depResults}")
        val taskRes = taskInstance.task.executeUnsafe(projectConfig, taskInstance.module, depResults, transitiveResults)
        taskInstance.id -> taskRes
      }
      val results = ox.par(taskExecutions)
      taskResults ++= results
    }
    // println("Execution finished successfully. " + Instant.now())
  }
}
