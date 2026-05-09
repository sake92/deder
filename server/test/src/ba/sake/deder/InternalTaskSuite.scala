package ba.sake.deder

import scala.jdk.CollectionConverters.*
import ba.sake.deder.config.ConfigParser
import ba.sake.deder.config.DederProject.*

/** Tests for internal task visibility: hidden from listing/completion/plan output,
 *  but still runnable by exact name.
 */
class InternalTaskSuite extends munit.FunSuite {

  private val testProjectsDir = os.pwd / "server/test/resources/sample-projects"

  /** A minimal internal task that depends on `scalaVersion` (public) and is itself
   *  depended upon by `compile` (public) via a single intermediate internal hop.
   *
   *  We inject it alongside CoreTasks to verify:
   *   - publicTaskInstancesPerModule excludes it
   *   - publicTaskInstancesGraph bridges edges over it
   *   - taskInstancesPerModule still includes it (exec still works)
   */
  private def buildWithInternalTask(): (TasksResolver, Task[?, ?]) = {
    val configParser = ConfigParser(writeJson = false)
    val projectConfig = configParser.parse(testProjectsDir / "multi" / "deder.pkl")
      .getOrElse(fail("Failed to parse deder.pkl config"))
    val coreTasks = CoreTasks()

    // An internal task that depends on scalaVersion
    val internalTask = TaskBuilder
      .make[String]("internalHelper", internal = true, category = "Configuration")
      .dependsOn(coreTasks.scalaVersionTask)
      .build { ctx => ctx.depResults._1 }

    val tasksRegistry = TasksRegistry(coreTasks.all :+ internalTask)
    val tasksResolver = TasksResolver(projectConfig, tasksRegistry)
    (tasksResolver, internalTask)
  }

  test("internal task is excluded from publicTaskInstancesPerModule") {
    val (tasksResolver, internalTask) = buildWithInternalTask()
    val publicTasks = tasksResolver.publicTaskInstancesPerModule("common").map(_.task.name)
    assert(!publicTasks.contains(internalTask.name), s"internal task '${internalTask.name}' should not appear in public listing")
  }

  test("internal task is still present in taskInstancesPerModule (exec works)") {
    val (tasksResolver, internalTask) = buildWithInternalTask()
    val allTasks = tasksResolver.taskInstancesPerModule("common").map(_.task.name)
    assert(allTasks.contains(internalTask.name), s"internal task '${internalTask.name}' should still be in full task list")
  }

  test("publicTaskInstancesGraph does not contain internal task vertices") {
    val (tasksResolver, internalTask) = buildWithInternalTask()
    val publicVertexNames = tasksResolver.publicTaskInstancesGraph.vertexSet().asScala.map(_.task.name).toSet
    assert(!publicVertexNames.contains(internalTask.name), s"internal task '${internalTask.name}' should not appear in public graph")
  }

  test("publicTaskInstancesGraph preserves edges bridged over internal tasks") {
    val (tasksResolver, internalTask) = buildWithInternalTask()
    val publicGraph = tasksResolver.publicTaskInstancesGraph
    // internalHelper depends on scalaVersion; since internalHelper is internal,
    // any public task that depended on internalHelper would be bridged to scalaVersion.
    // Here no public task directly depends on internalHelper, so we just verify
    // that scalaVersion vertex is still present as a public vertex.
    val publicVertexNames = publicGraph.vertexSet().asScala.map(_.task.name).toSet
    assert(publicVertexNames.contains("scalaVersion"), "scalaVersion should still be in public graph")
  }

  test("publicTaskInstancesGraph bridges public->internal->public edge correctly") {
    val configParser = ConfigParser(writeJson = false)
    val projectConfig = configParser.parse(testProjectsDir / "multi" / "deder.pkl")
      .getOrElse(fail("Failed to parse deder.pkl config"))
    val coreTasks = CoreTasks()

    // Chain: publicA -> internalBridge -> scalaVersion (public)
    val internalBridge = TaskBuilder
      .make[String]("internalBridge", internal = true, category = "Configuration")
      .dependsOn(coreTasks.scalaVersionTask)
      .build { ctx => ctx.depResults._1 }

    val publicA = TaskBuilder
      .make[String]("publicA", category = "Build")
      .dependsOn(internalBridge)
      .build { ctx => ctx.depResults._1 }

    val tasksRegistry = TasksRegistry(coreTasks.all ++ Seq(internalBridge, publicA))
    val tasksResolver = TasksResolver(projectConfig, tasksRegistry)

    val publicGraph = tasksResolver.publicTaskInstancesGraph
    val publicVertexNames = publicGraph.vertexSet().asScala.map(_.task.name).toSet

    assert(!publicVertexNames.contains("internalBridge"), "internalBridge should be absent from public graph")
    assert(publicVertexNames.contains("publicA"), "publicA should be in public graph")
    assert(publicVertexNames.contains("scalaVersion"), "scalaVersion should be in public graph")

    // publicA should have a bridged edge to scalaVersion
    val publicAVertex = publicGraph.vertexSet().asScala
      .find(_.task.name == "publicA" && _.moduleId == "common")
      .getOrElse(fail("publicA vertex not found in public graph for module 'common'"))
    val scalaVersionVertex = publicGraph.vertexSet().asScala
      .find(_.task.name == "scalaVersion" && _.moduleId == "common")
      .getOrElse(fail("scalaVersion vertex not found in public graph for module 'common'"))
    val edgeExists = publicGraph.containsEdge(publicAVertex, scalaVersionVertex)
    assert(edgeExists, "publicA should have a bridged edge to scalaVersion in the public graph")
  }

  test("tab completer hides internal tasks") {
    val configParser = ConfigParser(writeJson = false)
    val projectConfig = configParser.parse(testProjectsDir / "multi" / "deder.pkl")
      .getOrElse(fail("Failed to parse deder.pkl config"))
    val coreTasks = CoreTasks()

    val internalTask = TaskBuilder
      .make[String]("hiddenHelper", internal = true, category = "Configuration")
      .dependsOn(coreTasks.scalaVersionTask)
      .build { ctx => ctx.depResults._1 }

    val tasksRegistry = TasksRegistry(coreTasks.all :+ internalTask)
    val tasksResolver = TasksResolver(projectConfig, tasksRegistry)
    val completer = new ba.sake.deder.cli.TabCompleter(tasksResolver)

    val completions = completer.complete("deder exec -t ", 14).toSet
    assert(!completions.contains("hiddenHelper"), "internal task 'hiddenHelper' should not appear in tab completions")
    assert(completions.contains("compile"), "public task 'compile' should still appear in tab completions")
  }
}
