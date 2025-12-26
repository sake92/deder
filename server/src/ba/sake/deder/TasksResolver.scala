package ba.sake.deder

import scala.jdk.CollectionConverters.*
import org.jgrapht.graph.*
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.DederModule

class TasksResolver(
    projectConfig: DederProject,
    tasksRegistry: TasksRegistry
) {

  val allModules: Seq[DederModule] = projectConfig.modules.asScala.sortBy(_.id).toSeq

  val modulesMap: Map[String, DederModule] =
    allModules.map(m => m.id -> m).toMap

  lazy val modulesGraph: SimpleDirectedGraph[DederModule, DefaultEdge] = {
    val graph = new SimpleDirectedGraph[DederModule, DefaultEdge](classOf[DefaultEdge])
    allModules.foreach(graph.addVertex)
    allModules.foreach { m =>
      m.moduleDeps.asScala.foreach { moduleDep =>
        // TODO check platform type??
        // need to allow java<->scala deps both way
        // but not scala->scalajs etc
        graph.addEdge(m, moduleDep)
      }
    }
    GraphUtils.checkNoCycles(graph, _.id)
    graph
  }

  lazy val taskInstancesPerModule: Map[String, Seq[TaskInstance]] = {
    // make Tasks graph
    allModules.map { module =>
      val taskInstances = tasksRegistry.resolve(module.`type`).map(t => TaskInstance(module, t))
      module.id -> taskInstances
    }.toMap
  }

  lazy val taskInstancesGraph: SimpleDirectedGraph[TaskInstance, DefaultEdge] = {
    val graph = new SimpleDirectedGraph[TaskInstance, DefaultEdge](classOf[DefaultEdge])
    for module <- allModules do {
      val taskInstances = taskInstancesPerModule(module.id)
      val tasksMap = taskInstances.map(t => t.id -> t).toMap
      taskInstances.foreach { taskInstance =>
        graph.addVertex(taskInstance)
        taskInstance.task.taskDeps.toList.asInstanceOf[List[Task[?, ?]]].foreach { taskDep =>
          val taskDepId = s"${module.id}.${taskDep.name}"
          val taskDepInstance = tasksMap.getOrElse(
            taskDepId,
            throw DederException(s"Task referenced by '${taskInstance.id}' not found: '${taskDepId}'")
          )
          graph.addEdge(taskInstance, taskDepInstance)
        }
        // if this task triggers a task in depending module, e.g. compile->compile
        if taskInstance.task.transitive then
          module.moduleDeps.asScala.foreach { moduleDep =>
            taskInstancesPerModule(moduleDep.id).find(_.task.name == taskInstance.task.name).foreach { moduleDepTask =>
              graph.addVertex(moduleDepTask) // add if not already
              graph.addEdge(taskInstance, moduleDepTask)
            }
          }
      }
    }
    GraphUtils.checkNoCycles(graph, _.id)
    graph
  }

}
