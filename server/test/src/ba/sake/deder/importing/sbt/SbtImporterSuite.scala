package ba.sake.deder.importing.sbt

import munit.FunSuite

class SbtImporterSuite extends FunSuite {

  test("isPluginDependency returns true for 'plugin' configuration") {
    val dep = DependencyExport(
      organization = "org.wartremover",
      name = "wartremover_3",
      revision = "3.5.0",
      extraAttributes = Map.empty,
      configurations = Some("plugin"),
      excludes = Seq.empty,
      crossVersion = "full"
    )
    assert(SbtImporter.isPluginDependency(dep))
  }

  test("isPluginDependency returns true for 'plugin->default' configuration") {
    val dep = DependencyExport(
      organization = "org.wartremover",
      name = "wartremover_3",
      revision = "3.5.0",
      extraAttributes = Map.empty,
      configurations = Some("plugin->default(compile)"),
      excludes = Seq.empty,
      crossVersion = "full"
    )
    assert(SbtImporter.isPluginDependency(dep))
  }

  test("isPluginDependency returns false for 'test' configuration") {
    val dep = DependencyExport(
      organization = "org.scalameta",
      name = "munit_3",
      revision = "1.2.1",
      extraAttributes = Map.empty,
      configurations = Some("test"),
      excludes = Seq.empty,
      crossVersion = "binary"
    )
    assert(!SbtImporter.isPluginDependency(dep))
  }

  test("isPluginDependency returns false for None configuration") {
    val dep = DependencyExport(
      organization = "org.jsoup",
      name = "jsoup",
      revision = "1.21.1",
      extraAttributes = Map.empty,
      configurations = None,
      excludes = Seq.empty,
      crossVersion = "none"
    )
    assert(!SbtImporter.isPluginDependency(dep))
  }

  test("formatDependency produces correct Maven coordinate for full crossVersion") {
    val dep = DependencyExport(
      organization = "org.wartremover",
      name = "wartremover",
      revision = "3.5.0",
      extraAttributes = Map.empty,
      configurations = Some("plugin"),
      excludes = Seq.empty,
      crossVersion = "full"
    )
    assertEquals(SbtImporter.formatDependency(dep), "org.wartremover:::wartremover:3.5.0")
  }

  test("formatDependency produces correct Maven coordinate for binary crossVersion") {
    val dep = DependencyExport(
      organization = "org.scalameta",
      name = "munit",
      revision = "1.2.1",
      extraAttributes = Map.empty,
      configurations = None,
      excludes = Seq.empty,
      crossVersion = "binary"
    )
    assertEquals(SbtImporter.formatDependency(dep), "org.scalameta::munit:1.2.1")
  }

  test("formatDependency produces correct Maven coordinate for Java dependency") {
    val dep = DependencyExport(
      organization = "org.jsoup",
      name = "jsoup",
      revision = "1.21.1",
      extraAttributes = Map.empty,
      configurations = None,
      excludes = Seq.empty,
      crossVersion = "none"
    )
    assertEquals(SbtImporter.formatDependency(dep), "org.jsoup:jsoup:1.21.1")
  }

  test("dependencies are correctly partitioned between deps and scalacPluginDeps") {
    val regularDep = DependencyExport(
      organization = "org.jsoup",
      name = "jsoup",
      revision = "1.21.1",
      extraAttributes = Map.empty,
      configurations = None,
      excludes = Seq.empty,
      crossVersion = "none"
    )
    val pluginDep = DependencyExport(
      organization = "org.wartremover",
      name = "wartremover",
      revision = "3.5.0",
      extraAttributes = Map.empty,
      configurations = Some("plugin"),
      excludes = Seq.empty,
      crossVersion = "full"
    )
    val testDep = DependencyExport(
      organization = "org.scalameta",
      name = "munit",
      revision = "1.2.1",
      extraAttributes = Map.empty,
      configurations = Some("test"),
      excludes = Seq.empty,
      crossVersion = "binary"
    )

    val allDeps = Seq(regularDep, pluginDep, testDep)
    val (plugins, regular) = allDeps.partition(SbtImporter.isPluginDependency)

    assertEquals(plugins.length, 1)
    assertEquals(plugins.head.name, "wartremover")
    assertEquals(regular.length, 2)
    assert(regular.exists(_.name == "jsoup"))
    assert(regular.exists(_.name == "munit"))
  }
}