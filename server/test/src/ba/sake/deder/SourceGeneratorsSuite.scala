package ba.sake.deder

import scala.jdk.CollectionConverters.*
import ba.sake.deder.config.DederProject.ModuleType

class SourceGeneratorsSuite extends munit.FunSuite {

  test("FanInTask reports its collectKind via dynamicDeps") {
    val genA = TaskBuilder
      .make[os.Path](name = "genA", kind = TaskKind.SourceGenerator)
      .build(_.out)
    val genB = TaskBuilder
      .make[os.Path](name = "genB", kind = TaskKind.ResourceGenerator)
      .build(_.out)
    val unrelated = TaskBuilder
      .make[os.Path](name = "unrelated")
      .build(_.out)

    val fanIn = FanInTask[os.Path](
      name = "allGen",
      collectKind = TaskKind.SourceGenerator
    )

    val siblings = Seq(genA, genB, unrelated)
    val resolved = fanIn.dynamicDeps(siblings, ModuleType.SCALA)
    assertEquals(resolved.map(_.name).toSet, Set("genA"))
  }

  test("FanInTask filters by supportedModuleTypes") {
    val javaOnly = TaskBuilder
      .make[os.Path](
        name = "javaOnlyGen",
        kind = TaskKind.SourceGenerator,
        supportedModuleTypes = Set(ModuleType.JAVA)
      )
      .build(_.out)
    val anyType = TaskBuilder
      .make[os.Path](name = "anyTypeGen", kind = TaskKind.SourceGenerator)
      .build(_.out)

    val fanIn = FanInTask[os.Path](
      name = "allGen",
      collectKind = TaskKind.SourceGenerator
    )
    val resolvedScala = fanIn.dynamicDeps(Seq(javaOnly, anyType), ModuleType.SCALA)
    assertEquals(resolvedScala.map(_.name).toSet, Set("anyTypeGen"))

    val resolvedJava = fanIn.dynamicDeps(Seq(javaOnly, anyType), ModuleType.JAVA)
    assertEquals(resolvedJava.map(_.name).toSet, Set("javaOnlyGen", "anyTypeGen"))
  }

  test("TasksResolver wires FanInTask edges to matching kind contributors") {
    val genA = TaskBuilder
      .make[os.Path](name = "genA", kind = TaskKind.SourceGenerator)
      .build(_.out)
    val genB = TaskBuilder
      .make[os.Path](name = "genB", kind = TaskKind.SourceGenerator)
      .build(_.out)
    val resGen = TaskBuilder
      .make[os.Path](name = "resGen", kind = TaskKind.ResourceGenerator)
      .build(_.out)
    val fanInSources = FanInTask[os.Path](
      name = "allGenSources",
      collectKind = TaskKind.SourceGenerator
    )

    val coreTasks = CoreTasks()
    val tasksRegistry = TasksRegistry(coreTasks.all ++ Seq(genA, genB, resGen, fanInSources))

    val configParser = ba.sake.deder.config.ConfigParser(writeJson = false)
    val testProjectsDir = os.pwd / "server/test/resources/sample-projects"
    val parsed = configParser.parse(testProjectsDir / "multi" / "deder.pkl").toOption.get
    val tasksResolver = TasksResolver(parsed, tasksRegistry)

    val graph = tasksResolver.taskInstancesGraph
    val edges = graph.edgeSet().asScala
      .map(e => (graph.getEdgeSource(e).id, graph.getEdgeTarget(e).id))
      .toSet
    Seq("common", "frontend", "backend", "uber").foreach { m =>
      assert(edges.contains((s"$m.allGenSources", s"$m.genA")), s"missing $m.allGenSources -> $m.genA")
      assert(edges.contains((s"$m.allGenSources", s"$m.genB")), s"missing $m.allGenSources -> $m.genB")
      assert(!edges.contains((s"$m.allGenSources", s"$m.resGen")), s"unexpected $m.allGenSources -> $m.resGen")
    }
  }
}
