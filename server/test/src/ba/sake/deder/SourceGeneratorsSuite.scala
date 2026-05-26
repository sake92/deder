package ba.sake.deder

import scala.jdk.CollectionConverters.*
import ba.sake.deder.config.DederProject.ModuleType
import ba.sake.deder.publish.PublishTasks
import ba.sake.deder.graalvm.GraalVmNativeImageTasks

class SourceGeneratorsSuite extends munit.FunSuite {

  private var testProjectDir: os.Path = scala.compiletime.uninitialized

  val coreTasks = CoreTasks()
  val runTasks = RunTasks(coreTasks)
  val publishTasks = PublishTasks(coreTasks)
  val scalaJsTasks = scalajs.ScalaJsTasks(coreTasks)
  val scalaNativeTasks = scalanative.ScalaNativeTasks(coreTasks)
  val graalvmNativeImageTasks = GraalVmNativeImageTasks(coreTasks)

  override def beforeAll(): Unit = {
    testProjectDir = os.pwd / "server/test/resources/sample-projects/multi"
    System.setProperty("DEDER_PROJECT_ROOT_DIR", testProjectDir.toString)
  }

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

    val tasksRegistry = TasksRegistry(coreTasks.all ++ Seq(genA, genB, resGen, fanInSources))

    val configParser = ba.sake.deder.config.ConfigParser(writeJson = false)
    val testProjectsDir = os.pwd / "server/test/resources/sample-projects"
    val parsed = configParser.parse(testProjectsDir / "multi" / "deder.pkl").toOption.get
    val tasksResolver = TasksResolver(parsed, tasksRegistry)

    val graph = tasksResolver.taskInstancesGraph
    val edges = graph
      .edgeSet()
      .asScala
      .map(e => (graph.getEdgeSource(e).id, graph.getEdgeTarget(e).id))
      .toSet
    Seq("common", "frontend", "backend", "uber").foreach { m =>
      assert(edges.contains((s"$m.allGenSources", s"$m.genA")), s"missing $m.allGenSources -> $m.genA")
      assert(edges.contains((s"$m.allGenSources", s"$m.genB")), s"missing $m.allGenSources -> $m.genB")
      assert(!edges.contains((s"$m.allGenSources", s"$m.resGen")), s"unexpected $m.allGenSources -> $m.resGen")
    }
  }

  test("FanInTask collects contributors' results when executed") {
    val gen = TaskBuilder
      .make[os.Path](name = "syntheticGen", kind = TaskKind.SourceGenerator)
      .build { ctx =>
        os.makeDir.all(ctx.out)
        os.write.over(ctx.out / "marker.txt", "hello", createFolders = true)
        ctx.out
      }
    val fanIn = FanInTask[os.Path](
      name = "syntheticFanIn",
      collectKind = TaskKind.SourceGenerator
    )

    val tasksRegistry = TasksRegistry(coreTasks.all ++ Seq(gen, fanIn))
    val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
    try {
      val state =
        DederProjectState(
          coreTasks,
          runTasks,
          scalaJsTasks,
          scalaNativeTasks,
          graalvmNativeImageTasks,
          tasksRegistry,
          Int.MaxValue,
          pool,
          () => (),
          configFile = testProjectDir / "deder.pkl"
        )
      val notif = new ServerNotificationsLogger(_ => ())
      val results = state.executeTasks(
        ctx = CliClientContext(
          clientId = java.util.UUID.randomUUID().toString,
          requestId = java.util.UUID.randomUUID().toString
        ),
        moduleIds = Seq("common"),
        taskName = "syntheticFanIn",
        args = Seq.empty,
        watch = false,
        serverNotificationsLogger = notif,
        useLastGood = false
      )
      assertEquals(results.size, 1)
      val collected = results.head.res.asInstanceOf[Seq[os.Path]]
      assertEquals(collected.size, 1)
      assert(os.exists(collected.head / "marker.txt"))
    } finally pool.shutdownNow()
  }
}
