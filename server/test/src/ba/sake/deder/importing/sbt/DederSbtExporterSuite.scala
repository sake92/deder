package ba.sake.deder.importing.sbt

import munit.FunSuite
import ba.sake.deder.ServerNotificationsLogger

class DederSbtExporterSuite extends FunSuite {

  private val noopLogger = ServerNotificationsLogger(_ => ())

  /** Helper: builds a minimal ProjectExport with sensible defaults. */
  private def baseModule(
      id: String,
      base: String,
      name: String,
      scalaVersion: String = "3.3.5",
      externalDeps: Seq[DependencyExport] = Seq.empty,
      interProjectDeps: Seq[InterProjectDependencyExport] = Seq.empty,
      plugins: Seq[String] = Seq.empty,
  ): ProjectExport = ProjectExport(
    id = id,
    base = base,
    name = name,
    javacOptions = Seq.empty,
    scalaVersion = scalaVersion,
    crossScalaVersions = Seq.empty,
    scalacOptions = Seq.empty,
    interProjectDependencies = interProjectDeps,
    externalDependencies = externalDeps,
    repositories = Seq.empty,
    sourceDirs = Seq("src/main/scala"),
    testSourceDirs = Seq("src/test/scala"),
    resourceDirs = Seq.empty,
    testResourceDirs = Seq.empty,
    plugins = plugins,
    organization = "com.example",
    artifactName = name,
    artifactType = "jar",
    artifactClassifier = None,
    version = "0.1.0",
    description = "",
    homepage = None,
    developers = Seq.empty,
    licenses = Seq.empty,
    scmInfo = None,
  )

  // ---- Core generation tests ----

  test("generates correct Pkl header with amends directive") {
    val mod = baseModule("core", os.pwd.toString, "core")
    val exporter = new DederSbtExporter(IndexedSeq(mod), noopLogger)
    val result = exporter.generateBuild()
    assert(result.contains("""amends "https://sake92.github.io/deder/config/v0.7.3/DederProject.pkl""""))
  }

  test("single Scala module generates CreateScalaModules builder and modules block") {
    val mod = baseModule("myapp", os.pwd.toString, "myapp")
    val exporter = new DederSbtExporter(IndexedSeq(mod), noopLogger)
    val result = exporter.generateBuild()
    assert(result.contains("local const myapp = new CreateScalaModules"))
    assert(result.contains("modules {"))
    assert(result.contains("...myapp.all"))
  }

  // ---- Dependency filtering tests ----

  test("ignores scala-lang auto-added dependencies") {
    val scalaDep = DependencyExport(
      organization = "org.scala-lang", name = "scala3-library", revision = "2.13.15",
      extraAttributes = Map.empty, configurations = None, excludes = Seq.empty, crossVersion = "binary"
    )
    val normalDep = DependencyExport(
      organization = "org.jsoup", name = "jsoup", revision = "1.21.1",
      extraAttributes = Map.empty, configurations = None, excludes = Seq.empty, crossVersion = "none"
    )
    val mod = baseModule("app", os.pwd.toString, "app", externalDeps = Seq(scalaDep, normalDep))
    val exporter = new DederSbtExporter(IndexedSeq(mod), noopLogger)
    val result = exporter.generateBuild()
    assert(!result.contains("scala3-library"), "scala3-library should be filtered out")
    assert(result.contains("org.jsoup:jsoup:1.21.1"), "normal dep should be present")
  }

  test("ignores scala-js and scala-native auto-added dependencies") {
    val scalaJsDep = DependencyExport(
      organization = "org.scala-js", name = "scalajs-library", revision = "1.18.2",
      extraAttributes = Map.empty, configurations = None, excludes = Seq.empty, crossVersion = "binary"
    )
    val scalaNativeDep = DependencyExport(
      organization = "org.scala-native", name = "scala3lib", revision = "0.5.10",
      extraAttributes = Map.empty, configurations = None, excludes = Seq.empty, crossVersion = "binary"
    )
    val normalDep = DependencyExport(
      organization = "org.jsoup", name = "jsoup", revision = "1.21.1",
      extraAttributes = Map.empty, configurations = None, excludes = Seq.empty, crossVersion = "none"
    )
    val mod = baseModule("app", os.pwd.toString, "app", externalDeps = Seq(scalaJsDep, scalaNativeDep, normalDep))
    val exporter = new DederSbtExporter(IndexedSeq(mod), noopLogger)
    val result = exporter.generateBuild()
    assert(!result.contains("scalajs-library"))
    assert(!result.contains("scala3lib"))
    assert(result.contains("org.jsoup:jsoup:1.21.1"))
  }

  // ---- Dependency type partitioning tests ----

  test("places regular compile deps in deps block") {
    val dep = DependencyExport(
      organization = "org.jsoup", name = "jsoup", revision = "1.21.1",
      extraAttributes = Map.empty, configurations = None, excludes = Seq.empty, crossVersion = "none"
    )
    val mod = baseModule("app", os.pwd.toString, "app", externalDeps = Seq(dep))
    val exporter = new DederSbtExporter(IndexedSeq(mod), noopLogger)
    val result = exporter.generateBuild()
    assert(result.contains("""deps {"""))
    assert(result.contains(""""org.jsoup:jsoup:1.21.1""""))
  }

  test("places compiler plugin deps in scalacPluginDeps block") {
    val pluginDep = DependencyExport(
      organization = "org.wartremover", name = "wartremover", revision = "3.5.0",
      extraAttributes = Map.empty, configurations = Some("plugin"), excludes = Seq.empty, crossVersion = "full"
    )
    val mod = baseModule("app", os.pwd.toString, "app", externalDeps = Seq(pluginDep))
    val exporter = new DederSbtExporter(IndexedSeq(mod), noopLogger)
    val result = exporter.generateBuild()
    // scalacPluginDeps block should be present in the main template
    assert(result.contains("""scalacPluginDeps {"""))
    assert(result.contains(""""org.wartremover:::wartremover:3.5.0""""))
    // Main template should NOT have a regular deps block (testTemplate has default deps)
    val templateIdx = result.indexOf("template = new ScalaModule")
    val testTemplateIdx = result.indexOf("testTemplate = (template.asTest())")
    val between = result.substring(templateIdx, testTemplateIdx)
    assert(between.contains("scalacPluginDeps"), "main template should have scalacPluginDeps")
    assert(!between.contains("deps {"), "main template should not have regular deps")
  }

  test("test deps are excluded from main template, appear in testTemplate only") {
    val testDep = DependencyExport(
      organization = "org.scalameta", name = "munit", revision = "1.2.1",
      extraAttributes = Map.empty, configurations = Some("test"), excludes = Seq.empty, crossVersion = "binary"
    )
    val mod = baseModule("app", os.pwd.toString, "app", externalDeps = Seq(testDep))
    val exporter = new DederSbtExporter(IndexedSeq(mod), noopLogger)
    val result = exporter.generateBuild()

    // Main template should NOT have a deps block (no compile deps)
    val mainTemplateIdx = result.indexOf("template = new ScalaModule")
    val testTemplateIdx = result.indexOf("testTemplate = (template.asTest())")
    assert(mainTemplateIdx >= 0, "main template should exist")
    assert(testTemplateIdx >= 0, "test template should exist")
    val between = result.substring(mainTemplateIdx, testTemplateIdx)
    assert(!between.contains("org.scalameta::munit"), "test dep should not leak into main template")

    // Test template should contain the test dep
    val afterTest = result.substring(testTemplateIdx)
    assert(afterTest.contains("""org.scalameta::munit:1.2.1"""), "test dep should be in test template")
  }

  // ---- Inter-project module dependency tests ----

  test("resolves compile-scoped inter-project dependencies into moduleDeps") {
    val libMod = baseModule("lib", (os.pwd / "lib").toString, "lib")
    val appMod = baseModule("app", (os.pwd / "app").toString, "app",
      interProjectDeps = Seq(InterProjectDependencyExport("lib", "default"))
    )
    val exporter = new DederSbtExporter(IndexedSeq(libMod, appMod), noopLogger)
    val result = exporter.generateBuild()

    // app's builder should reference lib via moduleDeps
    assert(result.contains("""moduleDeps {"""))
    assert(result.contains("lib.main"))
    // lib should appear before app in sorted output (topological order)
    val libIdx = result.indexOf("local const lib")
    val appIdx = result.indexOf("local const app")
    assert(libIdx < appIdx, s"lib should appear before app in topo sort (lib=$libIdx, app=$appIdx)")
  }

  test("resolves test-scoped inter-project dependencies with test suffix mapping") {
    val libMod = baseModule("lib", (os.pwd / "lib").toString, "lib")
    val appMod = baseModule("app", (os.pwd / "app").toString, "app",
      interProjectDeps = Seq(InterProjectDependencyExport("lib", "test"))
    )
    val exporter = new DederSbtExporter(IndexedSeq(libMod, appMod), noopLogger)
    val result = exporter.generateBuild()

    // app's testTemplate should reference lib.test via moduleDeps
    assert(result.contains("lib.test"))
  }

  // ---- Cross-project test (requires real temp directories) ----

  test("cross-project JVM+JS generates CreateCrossModules with jsTemplate") {
    val tmpDir = os.temp.dir()
    os.makeDir(tmpDir / ".jvm")
    os.makeDir(tmpDir / ".js")

    val jvmMod = ProjectExport(
      id = "coreJVM", base = (tmpDir / ".jvm").toString, name = "core",
      javacOptions = Seq.empty, scalaVersion = "3.3.5", crossScalaVersions = Seq.empty,
      scalacOptions = Seq.empty,
      interProjectDependencies = Seq.empty, externalDependencies = Seq.empty,
      repositories = Seq.empty,
      sourceDirs = Seq("src/main/scala"), testSourceDirs = Seq("src/test/scala"),
      resourceDirs = Seq.empty, testResourceDirs = Seq.empty,
      plugins = Seq.empty,
      organization = "com.example", artifactName = "core", artifactType = "jar",
      artifactClassifier = None, version = "0.1.0", description = "",
      homepage = None, developers = Seq.empty, licenses = Seq.empty, scmInfo = None,
    )
    val jsMod = ProjectExport(
      id = "coreJS", base = (tmpDir / ".js").toString, name = "core",
      javacOptions = Seq.empty, scalaVersion = "3.3.5", crossScalaVersions = Seq.empty,
      scalacOptions = Seq.empty,
      interProjectDependencies = Seq.empty, externalDependencies = Seq.empty,
      repositories = Seq.empty,
      sourceDirs = Seq("src/main/scala"), testSourceDirs = Seq("src/test/scala"),
      resourceDirs = Seq.empty, testResourceDirs = Seq.empty,
      plugins = Seq("ScalaJSPlugin"),
      organization = "com.example", artifactName = "core", artifactType = "jar",
      artifactClassifier = None, version = "0.1.0", description = "",
      homepage = None, developers = Seq.empty, licenses = Seq.empty, scmInfo = None,
    )

    val exporter = new DederSbtExporter(IndexedSeq(jvmMod, jsMod), noopLogger)
    val result = exporter.generateBuild()

    assert(result.contains("new CreateCrossModules"))
    assert(result.contains("""layout = "sbt-cross-pure""""))
    assert(result.contains("jsTemplate = (template.asJs())"))
    assert(result.contains("""scalaJsVersion = "1.18.2""""))
    assert(result.contains("core.jvm"))
    assert(result.contains("core.js"))
  }

  // ---- topological sort test ----

  test("topological sort: independent modules appear before dependents") {
    val a = baseModule("a", (os.pwd / "a").toString, "a")
    val b = baseModule("b", (os.pwd / "b").toString, "b",
      interProjectDeps = Seq(InterProjectDependencyExport("a", "default"))
    )
    val c = baseModule("c", (os.pwd / "c").toString, "c",
      interProjectDeps = Seq(InterProjectDependencyExport("b", "default"))
    )
    val exporter = new DederSbtExporter(IndexedSeq(c, b, a), noopLogger)
    val result = exporter.generateBuild()

    val idxA = result.indexOf("local const a")
    val idxB = result.indexOf("local const b")
    val idxC = result.indexOf("local const c")
    assert(idxA < idxB && idxB < idxC,
      s"Expected a ($idxA) < b ($idxB) < c ($idxC) in topo sort")
  }

  // ---- Sanitized module name test ----

  test("sanitizes module names with dots and hyphens into valid Pkl identifiers") {
    val dep = DependencyExport(
      organization = "org.jsoup", name = "jsoup", revision = "1.21.1",
      extraAttributes = Map.empty, configurations = None, excludes = Seq.empty, crossVersion = "none"
    )
    // name contains dot and hyphen — these aren't valid in Pkl identifiers
    val mod = baseModule("my-lib.ext", os.pwd.toString, "my-lib.ext", externalDeps = Seq(dep))
    val exporter = new DederSbtExporter(IndexedSeq(mod), noopLogger)
    val result = exporter.generateBuild()

    // Dots and hyphens should be replaced with underscores
    assert(result.contains("local const my_lib_ext"))
    assert(result.contains("""id = "my_lib_ext""""))
    assert(result.contains("...my_lib_ext.all"))
  }
}
