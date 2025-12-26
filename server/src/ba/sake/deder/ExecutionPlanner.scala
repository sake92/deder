package ba.sake.deder

import org.jgrapht.graph.*
import scala.jdk.CollectionConverters.*

class ExecutionPlanner(
    tasksGraph: SimpleDirectedGraph[TaskInstance, DefaultEdge],
    tasksPerModule: Map[String, Seq[TaskInstance]]
) {

  // build independent exec stages (~toposort)
  def getExecStages(moduleId: String, taskName: String): Seq[Seq[TaskInstance]] = {
    val taskToExecute = tasksPerModule.getOrElse(moduleId, Seq.empty).find(_.task.name == taskName).getOrElse {
      throw TaskNotFoundException(s"Task not found ${moduleId}.${taskName}")
    }

    val execSubgraph = getExecSubgraph(moduleId, taskName)

    def findStartTaskInstances(task: TaskInstance): Seq[TaskInstance] = {
      val depEdges = execSubgraph.outgoingEdgesOf(task).asScala.toSeq
      if depEdges.isEmpty then Seq(task)
      else
        depEdges.flatMap { depEdge =>
          val d = execSubgraph.getEdgeTarget(depEdge)
          findStartTaskInstances(d)
        }
    }

    // Kahn's algorithm variant
    // start from leaves and go backwards
    var currentTaskInstances = findStartTaskInstances(taskToExecute).distinct.sortBy(_.id)
    var visitedTaskIds = currentTaskInstances.map(_.id).toSet
    var stages = Seq(currentTaskInstances)

    def getStages(currentTaskInstances: Seq[TaskInstance]) = {
      val res = currentTaskInstances.flatMap { currentTI =>
        val incomingEdges = execSubgraph.incomingEdgesOf(currentTI).asScala.toSet
        incomingEdges.flatMap { inEdge =>
          val dependingTI = execSubgraph.getEdgeSource(inEdge)
          val dependingTIDepIds = execSubgraph.outgoingEdgesOf(dependingTI).asScala.toSet
          val allDepsSatisfied = dependingTIDepIds.forall { outEdge =>
            val targetTI = execSubgraph.getEdgeTarget(outEdge)
            visitedTaskIds.contains(targetTI.id)
          }
          Option.when(allDepsSatisfied)(dependingTI)
        }
      }
      visitedTaskIds = visitedTaskIds ++ res.map(_.id)
      res.distinct.sortBy(_.id) // keep order, for test stability
    }

    while currentTaskInstances.nonEmpty do {
      val nextTaskInstances = getStages(currentTaskInstances)
      if nextTaskInstances.nonEmpty then stages = stages.appended(nextTaskInstances)
      currentTaskInstances = nextTaskInstances
    }

    stages.toSeq
  }

  // plan execution
  def getExecSubgraph(moduleId: String, taskName: String): AsSubgraph[TaskInstance, DefaultEdge] = {
    val execTasksSet = Set.newBuilder[TaskInstance]

    def go(moduleId: String, taskName: String): Unit = {
      val taskToExecute = tasksPerModule.getOrElse(moduleId, Seq.empty).find(_.task.name == taskName).getOrElse {
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

  // used for watch mode - find which source files affect the given task
  def getAffectingSourceFileTasks(moduleId: String, taskName: String): Set[TaskInstance] =
    getRootDepTasks(moduleId, taskName).filter(_.task match {
      case _: SourceFileTask  => true
      case _: SourceFilesTask => true
      case _                  => false
    })

  // used for watch mode - find which config values affect the given task
  def getAffectingConfigValueTasks(moduleId: String, taskName: String): Set[TaskInstance] =
    getRootDepTasks(moduleId, taskName).filter(_.task match {
      case _: ConfigValueTask[?] => true
      case _                     => false
    })

  private def getRootDepTasks(moduleId: String, taskName: String): Set[TaskInstance] = {
    val affectingTasks = Set.newBuilder[TaskInstance]
    def go(moduleId: String, taskName: String): Unit = {
      val taskInstance = tasksPerModule.getOrElse(moduleId, Seq.empty).find(_.task.name == taskName).getOrElse {
        throw TaskNotFoundException(s"Task not found ${moduleId}.${taskName}")
      }
      val deps = tasksGraph.outgoingEdgesOf(taskInstance).asScala.toSeq
      deps.foreach { depEdge =>
        val d = tasksGraph.getEdgeTarget(depEdge)
        go(d.moduleId, d.task.name)
      }
      taskInstance.task match {
        case _: SourceFileTask     => affectingTasks.addOne(taskInstance)
        case _: SourceFilesTask    => affectingTasks.addOne(taskInstance)
        case _: ConfigValueTask[?] => affectingTasks.addOne(taskInstance)
        case _                     =>
      }
    }
    go(moduleId, taskName)
    affectingTasks.result()
  }

  def getTaskInstance(moduleId: String, taskName: String): TaskInstance = {
    tasksPerModule.getOrElse(moduleId, Seq.empty).find(_.task.name == taskName).getOrElse {
      throw TaskNotFoundException(s"Task not found ${moduleId}.${taskName}")
    }
  }
}
