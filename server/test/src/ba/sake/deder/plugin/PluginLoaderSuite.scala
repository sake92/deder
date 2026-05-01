package ba.sake.deder.plugin

import ba.sake.deder.*
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.*
import scala.jdk.CollectionConverters.*

class PluginLoaderSuite extends munit.FunSuite {

  private val emptyStrings = java.util.List.of[String]()
  private val emptyModules = java.util.List.of[DederModule]()
  private val emptyPlugins = java.util.List.of[Plugin]()
  private val emptyMap = java.util.Map.of[String, String]()
  private val emptyMapOfMaps = java.util.Map.of[String, java.util.Map[String, String]]()
  private val emptyMvnApps = java.util.Map.of[String, MvnApp]()
  private val emptyRepos = java.util.List.of[MavenRepository]()
  private val emptyManifest = ManifestSettings(emptyMap, emptyMapOfMaps)
  private val javaThenScala = CompileOrder.JAVA_THEN_SCALA
  private val javaType = ModuleType.JAVA

  private def mkModule(id: String, plugins: java.util.List[Plugin]): JavaModule =
    JavaModule(
      id,
      id, // root
      java.util.List.of("src"),
      emptyModules,
      plugins,
      javaType,
      emptyStrings,
      null, // javaHome
      emptyStrings,
      null, // javaVersion
      javaThenScala,
      emptyStrings,
      emptyMap,
      null, // mainClass
      emptyStrings,
      emptyStrings,
      "", // javaSemanticdbVersion
      false, // semanticdbEnabled
      emptyManifest,
      false, // publish
      null, // pomSettings
      null, // publishTo
      null, // publishLocalTo
      null, // graalvm
      emptyMvnApps
    )

  test("extract plugin deps from a project with one plugin") {
    val plugin = Plugin("hello", java.util.List.of("ba.sake::deder-hello-plugin:0.1.0"))
    val module = mkModule("test-module", java.util.List.of(plugin))
    val config = DederProject(java.util.List.of(module), emptyRepos, true)

    val deps = PluginLoader.extractDeps(config)
    assertEquals(deps, Seq("ba.sake::deder-hello-plugin:0.1.0"))
  }

  test("extract plugin deps from multiple modules and plugins") {
    val p1 = Plugin("a", java.util.List.of("org:a:1.0"))
    val p2 = Plugin("b", java.util.List.of("org:b:2.0"))
    val mod1 = mkModule("mod1", java.util.List.of(p1))
    val mod2 = mkModule("mod2", java.util.List.of(p2))
    val config = DederProject(java.util.List.of(mod1, mod2), emptyRepos, true)

    val deps = PluginLoader.extractDeps(config)
    assertEquals(deps, Seq("org:a:1.0", "org:b:2.0"))
  }

  test("empty plugins list returns empty deps") {
    val module = mkModule("m", emptyPlugins)
    val config = DederProject(java.util.List.of(module), emptyRepos, true)

    val deps = PluginLoader.extractDeps(config)
    assertEquals(deps, Seq.empty)
  }
}
