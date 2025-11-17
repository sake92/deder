package ba.sake.deder

import org.jgrapht.*
import org.jgrapht.alg.cycle.CycleDetector
import org.jgrapht.graph.*
import org.jgrapht.nio.*
import org.jgrapht.nio.dot.*
import org.jgrapht.traverse.*
import ox.par

import java.io.*
import java.net.*
import java.time.Instant
import scala.jdk.CollectionConverters.*
import scala.jdk.FunctionConverters.*

@main def serverMain() = {

  if getMajorJavaVersion() < 21 then abort("Must use JDK >= 21")

  // parsed from build.yaml/json/whatever
  val a = JavaModule("a", Seq("b", "c"))
  val b = JavaModule("b", Seq("d"))
  val c = JavaModule("c", Seq("d"))
  val d = JavaModule("d", Seq.empty)
  val allModules = Seq(a, b, c, d)
  // TODO check unique module ids

  // these come from CLI/BSP
  val moduleId = "a"
  val taskName = "run"

  def buildModulesGraph(modules: Seq[Module]): SimpleDirectedGraph[Module, DefaultEdge] = {
    val graph = new SimpleDirectedGraph[Module, DefaultEdge](classOf[DefaultEdge])
    val modulesMap = modules.map(m => m.id -> m).toMap
    modules.foreach(graph.addVertex)
    modules.foreach { m =>
      m.moduleDeps.foreach { moduleDepId =>
        val moduleDep = modulesMap.getOrElse(
          moduleDepId,
          abort(s"Module referenced by '${m.id}' not found: '${moduleDepId}'")
        )
        // TODO check module type??
        // need to allow java<->scala deps both way
        graph.addEdge(m, moduleDep)
      }
    }
    graph
  }

  val modulesGraph = buildModulesGraph(allModules)
  checkNoCycles(modulesGraph, _.id)
  println("Modules graph:")
  println(generateDOT(modulesGraph, v => v.id, v => Map("label" -> v.id)))

  // make Tasks graph
  val tasksPerModule = allModules.map { module =>
    val taskInstances =
      ModuleTasksRegistry.getByModule(module).map(t => TaskInstance(module, t))
    module.id -> taskInstances
  }.toMap
  def buildTasksGraph(modules: Seq[Module]): SimpleDirectedGraph[TaskInstance, DefaultEdge] = {
    val graph = new SimpleDirectedGraph[TaskInstance, DefaultEdge](classOf[DefaultEdge])
    for module <- modules do {
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
          module.moduleDeps.foreach { moduleDepId =>
            tasksPerModule(moduleDepId).find(_.task.name == task.task.name).foreach { moduleDepTask =>
              graph.addVertex(moduleDepTask) // add if not already
              graph.addEdge(task, moduleDepTask)
            }
          }
      }
    }
    graph
  }

  val tasksGraph = buildTasksGraph(allModules)
  checkNoCycles(tasksGraph, _.id)
  println("Tasks graph:")
  println(generateDOT(tasksGraph, v => v.id, v => Map("label" -> v.id)))

  //////////
  // plan //
  //////////
  def buildExecSubgraph(moduleId: String, taskName: String): AsSubgraph[TaskInstance, DefaultEdge] = {
    val execTasksSet = Set.newBuilder[TaskInstance]
    def go(moduleId: String, taskName: String): Unit = {
      val taskToExecute = tasksPerModule(moduleId).find(_.task.name == taskName).getOrElse {
        abort(s"Task not found ${moduleId}.${taskName}")
      }
      val deps = tasksGraph.outgoingEdgesOf(taskToExecute).asScala.toSeq
      deps.foreach { depEdge =>
        val d = tasksGraph.getEdgeTarget(depEdge)
        go(d.moduleId, d.task.name)
      }
      execTasksSet.addOne(taskToExecute)
    }
    go(moduleId, taskName)
    new AsSubgraph(tasksGraph, execTasksSet.result().asJava)
  }

  val execSubgraph = buildExecSubgraph(moduleId, taskName)
  checkNoCycles(execSubgraph, _.id)
  println("Exec subgraph:")
  println(generateDOT(execSubgraph, v => v.id, v => Map("label" -> v.id)))

  // build independent exec stages (~toposort)
  def buildTaskExecStages(moduleId: String, taskName: String): Seq[Seq[TaskInstance]] = {
    val taskToExecute = tasksPerModule(moduleId).find(_.task.name == taskName).getOrElse {
      abort(s"Task not found ${moduleId}.${taskName}")
    }
    var stages = Map.empty[Int, Seq[TaskInstance]]
    var maxDepth = 0
    def go(task: TaskInstance, depth: Int): Unit = {
      if depth > maxDepth then maxDepth = depth
      stages = stages.updatedWith(depth) {
        case Some(values) => Some(if values.exists(_.id == task.id) then values else values.appended(task))
        case None         => Some(Seq(task))
      }
      val deps = tasksGraph.outgoingEdgesOf(task).asScala.toSeq
      deps.foreach { depEdge =>
        val d = tasksGraph.getEdgeTarget(depEdge)
        go(d, depth + 1)
      }
    }
    go(taskToExecute, 0)
    val res = for i <- 0 to maxDepth yield stages(i)
    res.toSeq
  }
  val taskExecStages = buildTaskExecStages(moduleId, taskName).reverse
  println("Exec stages:")
  println(taskExecStages.map(_.map(_.id)))

  /////////////
  // execute //
  /////////////
  def executeTasks(stages: Seq[Seq[TaskInstance]]): Unit = {
    println("Starting execution... " + Instant.now())
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
        println(s"Executing ${taskInstance.id} with args: ${depResults}")
        val taskRes = taskInstance.task.executeUnsafe(taskInstance.module, depResults, transitiveResults)
        taskInstance.id -> taskRes
      }
      val results = ox.par(taskExecutions)
      taskResults ++= results
    }
    println("Execution finished successfully. " + Instant.now())
  }

  executeTasks(taskExecStages)

}

def checkNoCycles[V, E](g: Graph[V, E], getName: V => String): Unit = {
  val cycleDetector = new CycleDetector[V, E](g)
  val cycles = cycleDetector.findCycles().asScala
  if cycles.nonEmpty then abort(s"Cycle detected: ${cycles.map(getName).mkString("->")}")
}

def generateDOT[V, E](
    g: Graph[V, E],
    vertexIdProvider: V => String,
    vertexAttributeProvider: V => Map[String, String]
): String = {
  val vertexIdProvider0 = (v: V) => vertexIdProvider(v).replaceAll("[-.]", "_")
  val exporter = new DOTExporter[V, E](vertexIdProvider0.asJava)
  exporter.setVertexAttributeProvider { v =>
    vertexAttributeProvider(v)
      .mapValues(DefaultAttribute.createAttribute)
      .toMap
      .asJava
  }
  val writer = new StringWriter()
  exporter.exportGraph(g, writer)
  writer.toString()
}

def abort(message: String) =
  throw new RuntimeException(message)

def getMajorJavaVersion(): Int = {
  // Java 9+ versions are just the major number (e.g., "17")
  // Java 8- versions start with "1." (e.g., "1.8.0_202")
  val parts = scala.util.Properties.javaVersion.split('.')
  if (parts.length > 1 && parts(0) == "1") {
    // For Java 8 and earlier (e.g., "1.8.x"), the major version is the second part ("8")
    parts(1).toIntOption.getOrElse(0)
  } else {
    // For Java 9+ (e.g., "9", "11", "17.0.1"), the major version is the first part
    parts(0).toIntOption.getOrElse(0)
  }
}
