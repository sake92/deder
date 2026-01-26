package ba.sake.deder

import org.jgrapht.graph.*
import scala.jdk.CollectionConverters.*

class ExecutionPlanner(
    tasksGraph: SimpleDirectedGraph[TaskInstance, DefaultEdge],
    tasksPerModule: Map[String, Seq[TaskInstance]]
) {

  def getExecStages(moduleId: String, taskName: String): Seq[Seq[TaskInstance]] = {
    getExecStages(Seq(moduleId), taskName)
  }

  // build independent exec stages (~toposort)
  // assumes tasks exist on given modules
  def getExecStages(moduleIds: Seq[String], taskName: String): Seq[Seq[TaskInstance]] = {
    
    val execSubgraph = getExecSubgraph(moduleIds, taskName)

    def findStartTaskInstances(taskInstance: TaskInstance): Seq[TaskInstance] = {
      val depEdges = execSubgraph.outgoingEdgesOf(taskInstance).asScala.toSeq
      if depEdges.isEmpty then Seq(taskInstance)
      else
        depEdges.flatMap { depEdge =>
          val d = execSubgraph.getEdgeTarget(depEdge)
          findStartTaskInstances(d)
        }
    }

    // Kahn's algorithm variant
    // start from leaves and go backwards
    val taskInstancesToExecute = moduleIds.map { moduleId =>
      getTaskInstance(moduleId, taskName)
    }
    var currentTaskInstances = taskInstancesToExecute.flatMap { tiToExecute =>
      findStartTaskInstances(tiToExecute)
    }.distinct.sortBy(_.id)
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

    stages
  }

  // plan execution
  def getExecSubgraph(moduleId: String, taskName: String): AsSubgraph[TaskInstance, DefaultEdge] =
    getExecSubgraph(Seq(moduleId), taskName)

  def getExecSubgraph(moduleIds: Seq[String], taskName: String): AsSubgraph[TaskInstance, DefaultEdge] = {
    // collect every task to execute
    val execTasksSet = Set.newBuilder[TaskInstance]
    def go(moduleId: String, taskName: String): Unit = {
      val taskInstanceToExecute = getTaskInstance(moduleId, taskName)
      val deps = tasksGraph.outgoingEdgesOf(taskInstanceToExecute).asScala.toSeq
      deps.foreach { depEdge =>
        val d = tasksGraph.getEdgeTarget(depEdge)
        go(d.moduleId, d.task.name)
      }
      execTasksSet.addOne(taskInstanceToExecute)
    }
    moduleIds.foreach { moduleId =>
      go(moduleId, taskName)
    }
    val subgraph = new AsSubgraph(tasksGraph, execTasksSet.result().asJava)
    GraphUtils.checkNoCycles(subgraph, _.id)
    subgraph
  }

  /* WATCH MODE UTILS */
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
      val taskInstance = getTaskInstance(moduleId, taskName)
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

  /* UTILS */
  def getTaskInstanceOpt(moduleId: String, taskName: String): Option[TaskInstance] =
    tasksPerModule.getOrElse(moduleId, Seq.empty).find(_.task.name == taskName)
  
  def getTaskInstance(moduleId: String, taskName: String): TaskInstance =
    getTaskInstanceOpt(moduleId, taskName).getOrElse {
      throw TaskNotFoundException(s"Task not found ${moduleId}.${taskName}")
    }
  
}
