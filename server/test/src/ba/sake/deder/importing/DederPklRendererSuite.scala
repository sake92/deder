package ba.sake.deder.importing

import munit.FunSuite
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.*
import ba.sake.deder.config.ConfigParser
import scala.jdk.CollectionConverters.*

class DederPklRendererSuite extends FunSuite {

  // ---- structural helpers ----

  /** Renders and evaluates Pkl, returning DederProject. Resolves schema from local files. */
  private def evaluateRender(build: DederBuild): DederProject = {
    val pklText = DederPklRenderer.render(build)
    val configDir = os.pwd / "config"
    val baseUrl = s"https://sake92.github.io/deder/config/${DederPklRenderer.DederVersion}"
    val rewritten = pklText
      .replace(s""""$baseUrl/DederProject.pkl"""", s""""${configDir / "DederProject.pkl"}"""")
      .replace(s""""$baseUrl/DederTpolecat.pkl"""", s""""${configDir / "DederTpolecat.pkl"}"""")
      .replace(s""""$baseUrl/DederTypelevel.pkl"""", s""""${configDir / "DederTypelevel.pkl"}"""")
    val tmpDir = os.temp.dir()
    try {
      val pklFile = tmpDir / "deder.pkl"
      os.write(pklFile, rewritten)
      val result = new ConfigParser(writeJson = false).parse(pklFile)
      result match {
        case Right(project) => project
        case Left(err)      => fail(s"Rendered Pkl is invalid:\n$err\n\nOutput:\n$pklText")
      }
    } finally {
      try { os.remove.all(tmpDir) } catch { case _: Exception => () }
    }
  }

  private def renderedModules(build: DederBuild): Seq[DederModule] =
    evaluateRender(build).modules.asScala.toSeq

  private def renderedModuleIds(build: DederBuild): Seq[String] =
    renderedModules(build).map(_.id)

  private def findBy(suffix: String, mods: Seq[DederModule]): DederModule =
    mods.find(_.id.endsWith(suffix)).getOrElse(fail(s"No module ending with '$suffix' in ${mods.map(_.id)}"))

  private def findModule(id: String, mods: Seq[DederModule]): DederModule =
    mods.find(_.id == id).getOrElse(fail(s"No module with id '$id' in ${mods.map(_.id)}"))

  private def scalaMod(mod: DederModule): ScalaModule =
    mod.asInstanceOf[ScalaModule]

  // ---- data builders ----

  private def emptyModule(
      scalaVersion: String = "3.3.5",
      scalacOptions: Seq[String] = Seq.empty,
      javacOptions: Seq[String] = Seq.empty,
      deps: Seq[DepDef] = Seq.empty,
      scalacPluginDeps: Seq[DepDef] = Seq.empty,
      testDeps: Seq[DepDef] = Seq.empty,
      moduleDeps: Seq[ModuleDepRef] = Seq.empty,
      testModuleDeps: Seq[ModuleDepRef] = Seq.empty,
      publish: Option[PublishInfo] = None,
      scalaJsVersion: Option[String] = None,
      scalaNativeVersion: Option[String] = None
  ): ModuleDef = ModuleDef(
    scalaVersion = scalaVersion,
    scalacOptions = scalacOptions,
    javacOptions = javacOptions,
    deps = deps,
    scalacPluginDeps = scalacPluginDeps,
    testDeps = testDeps,
    moduleDeps = moduleDeps,
    testModuleDeps = testModuleDeps,
    scalaJsVersion = scalaJsVersion,
    scalaNativeVersion = scalaNativeVersion,
    publish = publish,
    sources = Seq.empty,
    testSources = Seq.empty,
    resources = Seq.empty,
    testResources = Seq.empty
  )

  private def singleModuleBuild(
      name: String = "myapp",
      jvmMod: ModuleDef = emptyModule(),
      jsMod: Option[ModuleDef] = None,
      nativeMod: Option[ModuleDef] = None,
      layout: DederProject.DirLayout = DederProject.DirLayout.SBT,
      crossScalaVersions: Seq[String] = Seq.empty,
      dederVersion: String = DederPklRenderer.DederVersion
  ): DederBuild = DederBuild(
    moduleGroups = Seq(
      ModuleGroup(
        builderVarName = name,
        root = ".",
        layout = layout,
        crossScalaVersions = crossScalaVersions,
        jvmModule = jvmMod,
        jsModule = jsMod,
        nativeModule = nativeMod,
        hasJsModule = jsMod.isDefined,
        hasNativeModule = nativeMod.isDefined,
        usesTpolecat = false,
        usesTypelevel = false
      )
    ),
    repositories = Seq.empty
  )

  private def concreteCrossGroup(
      name: String,
      versions: Seq[String],
      slices: Seq[(String, String, ModuleDef)],
      layout: DederProject.DirLayout = DederProject.DirLayout.SBT,
      root: String = "."
  ): ModuleGroup =
    ModuleGroup(
      builderVarName = name,
      root = root,
      layout = layout,
      crossScalaVersions = versions,
      concreteModules = slices.map { (scalaVersion, platform, module) =>
        ConcreteModule(
          sbtProjectId = name,
          scalaVersion = scalaVersion,
          platform = platform,
          module = module
        )
      }
    )

  private def dep(
      org: String,
      name: String,
      version: String,
      crossVersion: String = "none",
      platform: Option[String] = None
  ): DepDef = {
    val scalaColon = crossVersion match {
      case "full"   => ":::"
      case "binary" => "::"
      case _        => ":"
    }
    val platformColon = platform match {
      case Some(_) => "::"
      case None    => ":"
    }
    DepDef(
      formatted = s"$org$scalaColon$name$platformColon$version",
      organization = org,
      name = name
    )
  }

  // ---- module structure tests ----

  test("single Scala module has correct structure") {
    val build = singleModuleBuild(name = "myapp")
    val mods = renderedModules(build)
    assertEquals(mods.length, 2)
    assert(mods.exists(_.id == "myapp"), s"ids: ${mods.map(_.id)}")
    assert(mods.exists(_.id == "myapp-test"))
    // main is SCALA, test is SCALA_TEST
    val main = findModule("myapp", mods)
    val test = findModule("myapp-test", mods)
    assertEquals(main.`type`, ModuleType.SCALA)
    assertEquals(test.`type`, ModuleType.SCALA_TEST)
  }

  test("cross-platform with JS and native has correct module count and IDs") {
    val jvmMod = emptyModule(scalaVersion = "3.3.5")
    val jsMod = emptyModule(scalaVersion = "3.3.5", scalaJsVersion = Some("1.18.2"))
    val nativeMod = emptyModule(scalaVersion = "3.3.5", scalaNativeVersion = Some("0.5.10"))
    val build = DederBuild(
      moduleGroups = Seq(
        ModuleGroup("core", ".", DederProject.DirLayout.SBT_CROSS_FULL, Seq.empty,
          jvmMod, Some(jsMod), Some(nativeMod), true, true, false, false)
      ),
      repositories = Seq.empty
    )
    val ids = renderedModuleIds(build)
    assertEquals(ids.length, 6)
    assert(ids.contains("core-jvm-3.3.5"))
    assert(ids.contains("core-jvm-test-3.3.5"))
    assert(ids.contains("core-js-3.3.5"))
    assert(ids.contains("core-js-test-3.3.5"))
    assert(ids.contains("core-native-3.3.5"))
    assert(ids.contains("core-native-test-3.3.5"))
  }

  test("sanitizes module names into valid Pkl identifiers and version-last IDs") {
    val mod = emptyModule(scalaVersion = "2.13.18")
    val build = DederBuild(
      moduleGroups = Seq(
        ModuleGroup("my_lib_ext", ".", DederProject.DirLayout.SBT, Seq.empty,
          mod, None, None, false, false, false, false)
      ),
      repositories = Seq.empty
    )
    val ids = renderedModuleIds(build)
    assert(ids.contains("my_lib_ext"))
    assert(ids.contains("my_lib_ext-test"))
  }

  // ---- scalacOptions / javacOptions ----

  test("scalacOptions are present on rendered module") {
    val mod = emptyModule(scalacOptions = Seq("-deprecation", "-Xfatal-warnings"))
    val build = singleModuleBuild(jvmMod = mod)
    val m = scalaMod(findModule("myapp", renderedModules(build)))
    assertEquals(m.scalacOptions.asScala.toSeq, Seq("-deprecation", "-Xfatal-warnings"))
  }

  test("javacOptions are present on rendered module") {
    val mod = emptyModule(javacOptions = Seq("-Xlint:unchecked"))
    val build = singleModuleBuild(jvmMod = mod)
    val m = scalaMod(findModule("myapp", renderedModules(build)))
    assertEquals(m.javacOptions.asScala.toSeq, Seq("-Xlint:unchecked"))
  }

  test("empty scalacOptions produces empty list") {
    val mod = emptyModule(scalacOptions = Seq.empty)
    val build = singleModuleBuild(jvmMod = mod)
    val m = scalaMod(findModule("myapp", renderedModules(build)))
    assert(m.scalacOptions.isEmpty)
  }

  test("empty javacOptions produces empty list") {
    val mod = emptyModule(javacOptions = Seq.empty)
    val build = singleModuleBuild(jvmMod = mod)
    val m = scalaMod(findModule("myapp", renderedModules(build)))
    assert(m.javacOptions.isEmpty)
  }

  // ---- deps / scalacPluginDeps / testDeps ----

  test("regular compile deps appear on main module only") {
    val d = dep("org.jsoup", "jsoup", "1.21.1")
    val mod = emptyModule(deps = Seq(d))
    val build = singleModuleBuild(jvmMod = mod)
    val main = scalaMod(findModule("myapp", renderedModules(build)))
    val test = scalaMod(findModule("myapp-test", renderedModules(build)))
    assert(main.deps.asScala.toSeq.contains("org.jsoup:jsoup:1.21.1"))
    // test module should NOT have the compile dep (it has its own munit dep)
    assert(!test.deps.asScala.toSeq.contains("org.jsoup:jsoup:1.21.1"))
  }

  test("compiler plugin deps appear in scalacPluginDeps on main module only") {
    val pluginD = dep("org.wartremover", "wartremover", "3.5.0", crossVersion = "full")
    val mod = emptyModule(scalacPluginDeps = Seq(pluginD))
    val build = singleModuleBuild(jvmMod = mod)
    val main = scalaMod(findModule("myapp", renderedModules(build)))
    assert(main.scalacPluginDeps.asScala.toSeq.contains("org.wartremover:::wartremover:3.5.0"))
  }

  test("test deps appear on test module only") {
    val testD = dep("org.scalameta", "munit", "1.2.1", crossVersion = "binary")
    val mod = emptyModule(testDeps = Seq(testD))
    val build = singleModuleBuild(jvmMod = mod)
    val main = scalaMod(findModule("myapp", renderedModules(build)))
    val test = scalaMod(findModule("myapp-test", renderedModules(build)))
    assert(main.deps.asScala.toSeq.isEmpty, s"main deps should be empty, got ${main.deps}")
    assert(test.deps.asScala.toSeq.contains("org.scalameta::munit:1.2.1"))
  }

  // ---- moduleDeps ----

  test("compile-scoped moduleDeps reference existing module") {
    val libMod = emptyModule()
    val appMod = emptyModule(moduleDeps = Seq(ModuleDepRef("lib", "main", isTest = false)))
    val lib = ModuleGroup("lib", ".", DederProject.DirLayout.SBT, Seq.empty,
      libMod, None, None, false, false, false, false)
    val app = ModuleGroup("app", ".", DederProject.DirLayout.SBT, Seq.empty,
      appMod, None, None, false, false, false, false)
    val build = DederBuild(Seq(lib, app), Seq.empty)
    val mods = renderedModules(build)
    val appMain = scalaMod(findModule("app", mods))
    val depIds = appMain.moduleDeps.asScala.toSeq.map(_.id)
    assert(depIds.contains("lib"), s"app should depend on lib, got: $depIds")
  }

  test("test-scoped moduleDeps reference test module") {
    val libMod = emptyModule()
    val appMod = emptyModule(testModuleDeps = Seq(ModuleDepRef("lib", "main", isTest = true)))
    val lib = ModuleGroup("lib", ".", DederProject.DirLayout.SBT, Seq.empty,
      libMod, None, None, false, false, false, false)
    val app = ModuleGroup("app", ".", DederProject.DirLayout.SBT, Seq.empty,
      appMod, None, None, false, false, false, false)
    val build = DederBuild(Seq(lib, app), Seq.empty)
    val mods = renderedModules(build)
    val appTest = scalaMod(findModule("app-test", mods))
    val depIds = appTest.moduleDeps.asScala.toSeq.map(_.id)
    assert(depIds.contains("lib-test"), s"app-test should depend on lib-test, got: $depIds")
  }

  // ---- repositories ----

  test("custom repositories appear in project") {
    val build = singleModuleBuild().copy(repositories =
      Seq(RepositoryDef("https://repo.example.com/maven"))
    )
    val project = evaluateRender(build)
    assertEquals(project.repositories.asScala.length, 1)
    assertEquals(project.repositories.get(0).url, "https://repo.example.com/maven")
  }

  // ---- publish ----

  test("publish info sets pomSettings on module") {
    val mod = emptyModule(publish =
      Some(PublishInfo(
        organization = "com.example", artifactName = "myapp", version = "1.0.0",
        description = Some("A test app"), homepage = Some("https://example.com"),
        developers = Seq(DeveloperDef("dev1", "Dev One", "dev1@example.com")),
        licenses = Seq(LicenseDef("MIT", "https://opensource.org/licenses/MIT")),
        scmInfo = None
      ))
    )
    val build = singleModuleBuild(jvmMod = mod)
    val m = findModule("myapp", renderedModules(build))
    val pom = m.asInstanceOf[JavaModule].pomSettings
    assertEquals(pom.groupId, "com.example")
    assertEquals(pom.version, "1.0.0")
    assertEquals(pom.description, "A test app")
  }

  // ---- cross-version .map() rendering ----

  test("cross-version single module renders correct module IDs per version") {
    val versions = Seq("2.12.21", "2.13.18")
    val group = concreteCrossGroup("lib", versions,
      slices = versions.map(v => (v, "main", emptyModule(scalaVersion = v)))
    )
    val build = DederBuild(Seq(group), Seq.empty)
    val rendered = DederPklRenderer.render(build)
    assert(rendered.contains("testId = \"lib-jvm-test-\\(sv)\""))
    val ids = renderedModuleIds(build)
    assertEquals(
      ids.sorted,
      Seq("lib-jvm-2.12.21", "lib-jvm-2.13.18", "lib-jvm-test-2.12.21", "lib-jvm-test-2.13.18")
    )
  }

  test("cross-version multi-module resolves moduleDeps via find filter") {
    val versions = Seq("2.12.21", "2.13.18")
    val lib = concreteCrossGroup("lib", versions,
      slices = versions.map(v => (v, "main", emptyModule(scalaVersion = v)))
    )
    val app = concreteCrossGroup("app", versions,
      slices = versions.map(v =>
        (v, "main", emptyModule(scalaVersion = v,
          moduleDeps = Seq(ModuleDepRef("lib", "main", targetScalaVersion = Some(v), isTest = false))))
      )
    )
    val build = DederBuild(Seq(lib, app), Seq.empty)
    val rendered = DederPklRenderer.render(build)
    assert(rendered.contains("""libModules.find((m) -> m.id == "lib-jvm-\(sv)")"""))
    val mods = renderedModules(build)
    assertEquals(mods.length, 8) // 2 groups × 2 versions × 2 (main+test)
    // app main modules should depend on corresponding lib main module
    val app212 = scalaMod(findBy("app-jvm-2.12.21", mods))
    val app213 = scalaMod(findBy("app-jvm-2.13.18", mods))
    assert(app212.moduleDeps.asScala.toSeq.map(_.id).contains("lib-jvm-2.12.21"))
    assert(app213.moduleDeps.asScala.toSeq.map(_.id).contains("lib-jvm-2.13.18"))
  }

  test("cross-version cross-platform uses id without version placeholder in builder") {
    val versions = Seq("2.12.21")
    val coreMod = emptyModule(scalaVersion = "2.12.21")
    val jsMod = emptyModule(scalaVersion = "2.12.21", scalaJsVersion = Some("1.18.2"))
    val nativeMod = emptyModule(scalaVersion = "2.12.21", scalaNativeVersion = Some("0.5.10"))
    val group = concreteCrossGroup("core", versions,
      layout = DederProject.DirLayout.SBT_CROSS_FULL,
      slices = Seq(
        ("2.12.21", "jvm", coreMod),
        ("2.12.21", "js", jsMod),
        ("2.12.21", "native", nativeMod)
      )
    )
    val build = DederBuild(Seq(group), Seq.empty)
    val ids = renderedModuleIds(build)
    // version always last
    assertEquals(ids.sorted, Seq(
      "core-js-2.12.21", "core-js-test-2.12.21",
      "core-jvm-2.12.21", "core-jvm-test-2.12.21",
      "core-native-2.12.21", "core-native-test-2.12.21"
    ))
  }

  test("cross-version module dep on non-cross module uses direct accessor") {
    val versions = Seq("2.12.21", "2.13.18")
    val lib = ModuleGroup("lib", ".", DederProject.DirLayout.SBT, Seq.empty,
      emptyModule(scalaVersion = "2.12.21"), None, None, false, false, false, false)
    val app = concreteCrossGroup("app", versions,
      slices = versions.map(v =>
        (v, "main", emptyModule(scalaVersion = v,
          moduleDeps = Seq(ModuleDepRef("lib", "main", isTest = false))))
      )
    )
    val build = DederBuild(Seq(lib, app), Seq.empty)
    val mods = renderedModules(build)
    val app212 = scalaMod(findBy("app-jvm-2.12.21", mods))
    val app213 = scalaMod(findBy("app-jvm-2.13.18", mods))
    // both should depend on the plain non-cross lib module (direct accessor)
    assert(app212.moduleDeps.asScala.toSeq.map(_.id).contains("lib"))
    assert(app213.moduleDeps.asScala.toSeq.map(_.id).contains("lib"))
  }

  test("cross-version test module dep resolves to aligned test module id") {
    val versions = Seq("2.12.21", "2.13.18")
    val lib = concreteCrossGroup("lib", versions,
      slices = versions.map(v =>
        (v, "main", emptyModule(scalaVersion = v))
      )
    )
    val appWithTestDeps = concreteCrossGroup("app", versions,
      slices = versions.map(v =>
        (v, "main", emptyModule(scalaVersion = v,
          testModuleDeps = Seq(ModuleDepRef("lib", "main", targetScalaVersion = Some(v), isTest = true))))
      )
    )
    val build = DederBuild(Seq(lib, appWithTestDeps), Seq.empty)
    val mods = renderedModules(build)
    val app212Test = scalaMod(findBy("app-jvm-test-2.12.21", mods))
    val app213Test = scalaMod(findBy("app-jvm-test-2.13.18", mods))
    assert(app212Test.moduleDeps.asScala.toSeq.map(_.id).contains("lib-jvm-test-2.12.21"))
    assert(app213Test.moduleDeps.asScala.toSeq.map(_.id).contains("lib-jvm-test-2.13.18"))
  }

  // ---- cross-version when-clauses ----

  test("version-specific deps differ per version") {
    val versions = Seq("2.12.21", "2.13.18")
    val group = concreteCrossGroup("lib", versions,
      slices = Seq(
        ("2.12.21", "main", emptyModule(scalaVersion = "2.12.21")),
        ("2.13.18", "main", emptyModule(scalaVersion = "2.13.18",
          deps = Seq(dep("org.typelevel", "cats-core", "2.12.0", crossVersion = "binary"))
        ))
      )
    )
    val build = DederBuild(Seq(group), Seq.empty)
    val mods = renderedModules(build)
    val lib212 = scalaMod(findBy("lib-jvm-2.12.21", mods))
    val lib213 = scalaMod(findBy("lib-jvm-2.13.18", mods))
    assert(lib212.deps.isEmpty, s"lib-jvm-2.12.21 should have no deps, got: ${lib212.deps}")
    assert(lib213.deps.asScala.toSeq.contains("org.typelevel::cats-core:2.12.0"))
  }

  test("version-specific moduleDeps differ per version") {
    val versions = Seq("2.12.21", "2.13.18")
    val other = concreteCrossGroup("other", versions,
      slices = versions.map(v => (v, "main", emptyModule(scalaVersion = v)))
    )
    val lib = concreteCrossGroup("lib", versions,
      slices = Seq(
        ("2.12.21", "main", emptyModule(scalaVersion = "2.12.21")),
        ("2.13.18", "main", emptyModule(scalaVersion = "2.13.18",
          moduleDeps = Seq(ModuleDepRef("other", "main", targetScalaVersion = Some("2.13.18"), isTest = false))
        ))
      )
    )
    val build = DederBuild(Seq(other, lib), Seq.empty)
    val mods = renderedModules(build)
    val lib212 = scalaMod(findBy("lib-jvm-2.12.21", mods))
    val lib213 = scalaMod(findBy("lib-jvm-2.13.18", mods))
    assert(lib212.moduleDeps.isEmpty, s"lib-jvm-2.12.21 should have no moduleDeps, got: ${lib212.moduleDeps}")
    assert(lib213.moduleDeps.asScala.toSeq.map(_.id).contains("other-jvm-2.13.18"))
  }

  test("version-specific scalacOptions differ per version") {
    val versions = Seq("2.12.21", "2.13.18")
    val group = concreteCrossGroup("lib", versions,
      slices = Seq(
        ("2.12.21", "main", emptyModule(scalaVersion = "2.12.21",
          scalacOptions = Seq("-deprecation"))),
        ("2.13.18", "main", emptyModule(scalaVersion = "2.13.18",
          scalacOptions = Seq("-deprecation", "-Xsource:3")))
      )
    )
    val build = DederBuild(Seq(group), Seq.empty)
    val mods = renderedModules(build)
    val lib212 = scalaMod(findBy("lib-jvm-2.12.21", mods))
    val lib213 = scalaMod(findBy("lib-jvm-2.13.18", mods))
    assertEquals(lib212.scalacOptions.asScala.toSeq, Seq("-deprecation"))
    assertEquals(lib213.scalacOptions.asScala.toSeq, Seq("-deprecation", "-Xsource:3"))
  }

  // ---- cross-version sparse / partial platforms ----

  test("sparse cross-version slices still produce all platforms in output") {
    val versions = Seq("2.12.21", "2.13.18")
    val jvm213 = emptyModule(scalaVersion = "2.13.18")
    val js212 = emptyModule(scalaVersion = "2.12.21", scalaJsVersion = Some("1.18.2"))
    val js213 = emptyModule(scalaVersion = "2.13.18", scalaJsVersion = Some("1.18.2"))
    val native212 = emptyModule(scalaVersion = "2.12.21", scalaNativeVersion = Some("0.5.10"))
    val native213 = emptyModule(scalaVersion = "2.13.18", scalaNativeVersion = Some("0.5.10"))
    val group = concreteCrossGroup("core", versions,
      layout = DederProject.DirLayout.SBT_CROSS_FULL,
      slices = Seq(
        ("2.12.21", "js", js212),
        ("2.12.21", "native", native212),
        ("2.13.18", "jvm", jvm213),
        ("2.13.18", "js", js213),
        ("2.13.18", "native", native213)
      )
    )
    val build = DederBuild(Seq(group), Seq.empty)
    val ids = renderedModuleIds(build)
    // 2 versions × 3 platforms × 2 (main+test) = 12, but jvm missing for 2.12.21 → 10?
    // The renderer always emits all platforms in .map() — let's check
    assertEquals(ids.length, 12) // template always emits all platforms
  }

  // ---- tpolecat / typelevel template handling ----

  test("tpolecat detected: scalacOptions reflect template minus provided options") {
    val mod = emptyModule(scalacOptions = Seq("-deprecation"))
    val build = DederBuild(
      moduleGroups = Seq(
        ModuleGroup("lib", ".", DederProject.DirLayout.SBT, Seq("2.13.18"),
          mod, None, None, false, false, usesTpolecat = true, usesTypelevel = false)
      ),
      repositories = Seq.empty
    )
    val result = DederPklRenderer.render(build)
    // header check is a string assertion (format-sensitive)
    assert(result.contains(s"""import "https://sake92.github.io/deder/config/v0.10.0/DederTpolecat.pkl""""))
    assert(result.contains("DederTpolecat.forVersion(sv)"))
    val mods = renderedModules(build)
    val libMod = scalaMod(findBy("lib-jvm-2.13.18", mods))
    // -deprecation is not in tpolecat template → rendered as user addition
    assert(libMod.scalacOptions.asScala.toSeq.contains("-deprecation"))
  }

  test("typelevel detected: scalacOptions use typelevel template") {
    val mod = emptyModule()
    val build = DederBuild(
      moduleGroups = Seq(
        ModuleGroup("lib", ".", DederProject.DirLayout.SBT, Seq.empty,
          mod, None, None, false, false, usesTpolecat = false, usesTypelevel = true)
      ),
      repositories = Seq.empty
    )
    val result = DederPklRenderer.render(build)
    assert(result.contains(s"""import "https://sake92.github.io/deder/config/v0.10.0/DederTypelevel.pkl""""))
    assert(result.contains("(DederTypelevel.typelevelScala"))
    val mods = renderedModules(build)
    val libMod = scalaMod(findModule("lib", mods))
    // typelevel template provides scalacOptions
    assert(libMod.scalacOptions.asScala.toSeq.nonEmpty)
  }

  test("neither tpolecat nor typelevel: scalacOptions come directly from user") {
    val mod = emptyModule(scalacOptions = Seq("-deprecation"))
    val build = DederBuild(
      moduleGroups = Seq(
        ModuleGroup("lib", ".", DederProject.DirLayout.SBT, Seq.empty,
          mod, None, None, false, false, usesTpolecat = false, usesTypelevel = false)
      ),
      repositories = Seq.empty
    )
    val mods = renderedModules(build)
    val libMod = scalaMod(findModule("lib", mods))
    assertEquals(libMod.scalacOptions.asScala.toSeq, Seq("-deprecation"))
  }

  test("cross-version with tpolecat emits per-version template deltas") {
    val mods = Seq(
      ConcreteModule("proj1", "2.13.18", "main",
        emptyModule(scalaVersion = "2.13.18", scalacOptions = Seq("-deprecation"))),
      ConcreteModule("proj1", "3.7.4", "main",
        emptyModule(scalaVersion = "3.7.4", scalacOptions = Seq("-deprecation", "-Werror")))
    )
    val g = ModuleGroup("core", ".", DederProject.DirLayout.DEFAULT,
      Seq("2.13.18", "3.7.4"), concreteModules = mods, usesTpolecat = true)
    val build = DederBuild(Seq(g), Seq.empty)
    val rmods = renderedModules(build)
    val m213 = scalaMod(findBy("core-jvm-2.13.18", rmods))
    val m374 = scalaMod(findBy("core-jvm-3.7.4", rmods))
    assert(m213.scalacOptions.asScala.toSeq.contains("-deprecation"))
    assert(m374.scalacOptions.asScala.toSeq.contains("-Werror"))
  }

  // ---- basePomSettings ----

  test("shared basePomSettings when multiple modules share same publish info") {
    val publish = PublishInfo("com.example", "mod1", "1.0.0",
      Some("desc"), Some("https://example.com"),
      Seq(DeveloperDef("dev1", "Dev One", "dev1@example.com")),
      Seq(LicenseDef("MIT", "https://opensource.org/licenses/MIT")),
      Some(ScmDef("https://github.com/eg", "scm:git:https://...", None)))
    val publish2 = publish.copy(artifactName = "mod2")
    val mod1 = emptyModule(publish = Some(publish))
    val mod2 = emptyModule(publish = Some(publish2))
    val build = DederBuild(
      Seq(
        ModuleGroup("mod1", ".", DederProject.DirLayout.SBT, Seq.empty, mod1, None, None, false, false, false, false),
        ModuleGroup("mod2", ".", DederProject.DirLayout.SBT, Seq.empty, mod2, None, None, false, false, false, false)
      ),
      Seq.empty
    )
    val rmods = renderedModules(build)
    val m1 = findModule("mod1", rmods).asInstanceOf[JavaModule]
    val m2 = findModule("mod2", rmods).asInstanceOf[JavaModule]
    assertEquals(m1.pomSettings.groupId, "com.example")
    assertEquals(m2.pomSettings.groupId, "com.example")
    assertEquals(m1.pomSettings.artifactId, "mod1")
    assertEquals(m2.pomSettings.artifactId, "mod2")
    // both share the same scm info
    assertEquals(m1.pomSettings.scm.url, "https://github.com/eg")
    assertEquals(m2.pomSettings.scm.url, "https://github.com/eg")
  }

  test("differing publish infos produce distinct pomSettings") {
    val publish1 = PublishInfo("com.example", "mod1", "1.0.0", None, None, Seq.empty, Seq.empty, None)
    val publish2 = PublishInfo("com.other", "mod2", "2.0.0", None, None, Seq.empty, Seq.empty, None)
    val mod1 = emptyModule(publish = Some(publish1))
    val mod2 = emptyModule(publish = Some(publish2))
    val build = DederBuild(
      Seq(
        ModuleGroup("mod1", ".", DederProject.DirLayout.SBT, Seq.empty, mod1, None, None, false, false, false, false),
        ModuleGroup("mod2", ".", DederProject.DirLayout.SBT, Seq.empty, mod2, None, None, false, false, false, false)
      ),
      Seq.empty
    )
    val rmods = renderedModules(build)
    val m1 = findModule("mod1", rmods).asInstanceOf[JavaModule]
    val m2 = findModule("mod2", rmods).asInstanceOf[JavaModule]
    assertEquals(m1.pomSettings.groupId, "com.example")
    assertEquals(m2.pomSettings.groupId, "com.other")
  }

  // ---- header format (string-based — format-sensitive) ----

  test("generates correct Pkl header with amends directive") {
    val build = singleModuleBuild()
    val result = DederPklRenderer.render(build)
    assert(result.contains(s"""amends "https://sake92.github.io/deder/config/v0.10.0/DederProject.pkl""""))
  }

  // ---- TemplateOptionsReader tests (test the reader, not the renderer) ----

  test("TemplateOptionsReader tpolecat options have correct encoding") {
    val scala3 = TemplateOptionsReader.tpolecatScalacOptions("3.3.5")
    val scala213 = TemplateOptionsReader.tpolecatScalacOptions("2.13.18")
    val scala212 = TemplateOptionsReader.tpolecatScalacOptions("2.12.20")

    assert(scala3.nonEmpty)
    assert(scala213.nonEmpty)
    assert(scala212.nonEmpty)

    for (opts <- Seq(scala3, scala213, scala212)) {
      assert(opts.contains("-encoding"), s"should contain -encoding: $opts")
      assert(opts.contains("utf8"), s"should contain utf8: $opts")
      assert(!opts.contains("-encoding:utf8"), s"should NOT contain combined -encoding:utf8: $opts")
    }

    assert(scala3.contains("-deprecation"))
    assert(scala212.contains("-deprecation"))
    assert(scala213.contains("-Xlint:deprecation"))
    assert(scala213.contains("-feature"))
  }

  test("TemplateOptionsReader typelevel options have correct encoding") {
    val scala3 = TemplateOptionsReader.typelevelScalacOptions("3.3.5")
    assert(scala3.nonEmpty)
    assert(scala3.contains("-encoding"))
    assert(scala3.contains("utf8"))
    assert(scala3.contains("-deprecation"))
  }
}
