package ba.sake.deder.plugin

import ba.sake.deder.*
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.*

class PluginLoaderSuite extends munit.FunSuite {

  private val emptyModules = java.util.List.of[DederProject.DederModule]()
  private val emptyPlugins = java.util.List.of[DederProject.DederPlugin]()
  private val emptyRepos = java.util.List.of[MavenRepository]()

  test("extract plugin deps from a project with one plugin") {
    val plugin = DederPlugin("hello", java.util.List.of("ba.sake::deder-hello-plugin:0.1.0"))
    val config = DederProject(emptyModules, java.util.List.of(plugin), emptyRepos, true)

    val deps = PluginLoader.extractDeps(config)
    assertEquals(deps, Seq("ba.sake::deder-hello-plugin:0.1.0"))
  }

  test("extract plugin deps from multiple modules and plugins") {
    val p1 = DederPlugin("a", java.util.List.of("org:a:1.0"))
    val p2 = DederPlugin("b", java.util.List.of("org:b:2.0"))
    val config = DederProject(emptyModules, java.util.List.of(p1, p2), emptyRepos, true)

    val deps = PluginLoader.extractDeps(config)
    assertEquals(deps, Seq("org:a:1.0", "org:b:2.0"))
  }

  test("empty plugins list returns empty deps") {
    val config = DederProject(emptyModules, emptyPlugins, emptyRepos, true)

    val deps = PluginLoader.extractDeps(config)
    assertEquals(deps, Seq.empty)
  }
}
