package ba.sake.deder

import scala.jdk.CollectionConverters.*
import org.jgrapht.graph.*
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.DederModule

class TasksResolver(
    projectConfig: DederProject,
    tasksRegistry: TasksRegistry
) {

  private val allModules = projectConfig.modules.asScala.toSeq

  val modulesGraph: SimpleDirectedGraph[DederModule, DefaultEdge] = {
    val graph = new SimpleDirectedGraph[DederModule, DefaultEdge](classOf[DefaultEdge])
    val modulesMap = allModules.map(m => m.id -> m).toMap
    allModules.foreach(graph.addVertex)
    allModules.foreach { m =>
      m.moduleDeps.asScala.foreach { moduleDep =>
        // TODO check module type??
        // need to allow java<->scala deps both way
        graph.addEdge(m, moduleDep)
      }
    }
    GraphUtils.checkNoCycles(graph, _.id)
    graph
  }
  
  val modulesMap: Map[String, DederModule] =
    allModules.map(m => m.id -> m).toMap

  val tasksPerModule: Map[String, Seq[TaskInstance]] = {
    // make Tasks graph
    allModules.map { module =>
      val taskInstances = tasksRegistry.resolve(module.`type`).map(t => TaskInstance(module, t))
      // println(taskInstances)
      module.id -> taskInstances
    }.toMap
  }

  val tasksGraph: SimpleDirectedGraph[TaskInstance, DefaultEdge] = {
    val graph = new SimpleDirectedGraph[TaskInstance, DefaultEdge](classOf[DefaultEdge])
    for module <- allModules do {
      val tasks = tasksPerModule(module.id)
      val tasksMap = tasks.map(t => t.id -> t).toMap
      tasks.foreach { task =>
        graph.addVertex(task)
        task.task.taskDeps.toList.asInstanceOf[List[Task[?, ?]]].foreach { taskDep =>
          val taskDepId = s"${module.id}.${taskDep.name}"
          val taskDepInstance = tasksMap.getOrElse(
            taskDepId,
            abort(s"Task referenced by '${task.id}' not found: '${taskDepId}'")
          )
          graph.addEdge(task, taskDepInstance)
        }
        // if this task triggers a task in depending module, e.g. compile->compile
        if task.task.transitive then
          module.moduleDeps.asScala.foreach { moduleDep =>
            tasksPerModule(moduleDep.id).find(_.task.name == task.task.name).foreach { moduleDepTask =>
              graph.addVertex(moduleDepTask) // add if not already
              graph.addEdge(task, moduleDepTask)
            }
          }
      }
    }
    GraphUtils.checkNoCycles(graph, _.id)
    graph
  }

}
