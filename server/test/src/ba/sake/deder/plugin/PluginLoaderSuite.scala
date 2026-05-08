package ba.sake.deder.plugin

import java.net.URLClassLoader
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

  test("plugin evaluator resolves modulepath resources from the plugin classloader before the app classloader") {
    val pluginDir = os.temp.dir(prefix = "plugin-modulepath-test-")
    os.write.over(
      pluginDir / "HelloPlugin.pkl",
      """module plugin.test
        |
        |class HelloPluginConfig {
        |  greeting: String = "Hello, Deder!"
        |}
        |
        |config: HelloPluginConfig = new {}
        |""".stripMargin
    )

    val pluginClassLoader = new URLClassLoader(Array(pluginDir.toIO.toURI.toURL), getClass.getClassLoader)
    val greeting = PluginConfigEvaluators
      .evaluateModulePathConfig(
        pluginClassLoader,
        modulePath = "HelloPlugin.pkl",
        configText =
          """config {
            |  greeting = "Hello from test!"
            |}
            |""".stripMargin
      )
      .get("config")
      .get("greeting")
      .as(classOf[String])

    assertEquals(greeting, "Hello from test!")
  }
}
