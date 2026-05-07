package ba.sake.deder

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
}
