package ba.sake.deder

import org.jgrapht.graph.*
import scala.jdk.CollectionConverters.*

class ExecutionPlanner(
    tasksGraph: SimpleDirectedGraph[TaskInstance, DefaultEdge],
    tasksPerModule: Map[String, Seq[TaskInstance]]
) {

  // build independent exec stages (~toposort)
  // TODO better plan for skewed graphs (some tasks have many deps, some none)
  def execStages(moduleId: String, taskName: String): Seq[Seq[TaskInstance]] = {
    val taskToExecute = tasksPerModule(moduleId).find(_.task.name == taskName).getOrElse {
      throw TaskNotFoundException(s"Task not found ${moduleId}.${taskName}")
    }
    var stages = Map.empty[Int, Seq[TaskInstance]]
    var maxDepth = 0

    def go(task: TaskInstance, depth: Int): Unit = {
      if depth > maxDepth then maxDepth = depth
      stages = stages.updatedWith(depth) {
        case Some(values) => Some(if values.exists(_.id == task.id) then values else values.appended(task))
        case None         => Some(Seq(task))
      }
      val depEdges = tasksGraph.outgoingEdgesOf(task).asScala.toSeq
      depEdges.foreach { depEdge =>
        val d = tasksGraph.getEdgeTarget(depEdge)
        go(d, depth + 1)
      }
    }

    go(taskToExecute, 0)
    val reversedStages = for i <- 0 to maxDepth yield stages(i)
    // TODO sort by id, so ordering is consistent for locking tasks !!!
    reversedStages.reverse
  }

  // this is just for debugging at the moment
  def getExecSubgraph(moduleId: String, taskName: String): AsSubgraph[TaskInstance, DefaultEdge] = {
    val execTasksSet = Set.newBuilder[TaskInstance]

    def go(moduleId: String, taskName: String): Unit = {
      val taskToExecute = tasksPerModule(moduleId).find(_.task.name == taskName).getOrElse {
        throw TaskNotFoundException(s"Task not found ${moduleId}.${taskName}")
      }
      val deps = tasksGraph.outgoingEdgesOf(taskToExecute).asScala.toSeq
      deps.foreach { depEdge =>
        val d = tasksGraph.getEdgeTarget(depEdge)
        go(d.moduleId, d.task.name)
      }
      execTasksSet.addOne(taskToExecute)
    }

    go(moduleId, taskName)
    val subgraph = new AsSubgraph(tasksGraph, execTasksSet.result().asJava)
    GraphUtils.checkNoCycles(subgraph, _.id)
    subgraph
  }

  def getTaskInstance(moduleId: String, taskName: String): TaskInstance = {
    tasksPerModule(moduleId).find(_.task.name == taskName).getOrElse {
      throw TaskNotFoundException(s"Task not found ${moduleId}.${taskName}")
    }
  }
}
