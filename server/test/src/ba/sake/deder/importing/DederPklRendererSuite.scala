package ba.sake.deder.importing

import munit.FunSuite
import ba.sake.deder.config.DederProject

class DederPklRendererSuite extends FunSuite {

    // ---- helpers ----

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
        scalaNativeVersion = None,
        publish = publish,
        sources = Seq.empty,
        testSources = Seq.empty,
        resources = Seq.empty,
        testResources = Seq.empty,
    )

    private def singleModuleBuild(
        name: String = "myapp",
        jvmMod: ModuleDef = emptyModule(),
        jsMod: Option[ModuleDef] = None,
        nativeMod: Option[ModuleDef] = None,
        layout: DederProject.DirLayout = DederProject.DirLayout.SBT,
        crossScalaVersions: Seq[String] = Seq.empty,
        dederVersion: String = "v0.7.4",
    ): DederBuild = DederBuild(
        dederVersion = dederVersion,
        moduleGroups = Seq(ModuleGroup(
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
            usesTypelevel = false,
        )),
        repositories = Seq.empty,
        warnings = Seq.empty,
    )

    private def concreteCrossGroup(
        name: String,
        versions: Seq[String],
        slices: Seq[(String, String, ModuleDef)],
        layout: DederProject.DirLayout = DederProject.DirLayout.SBT,
        root: String = ".",
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
                    module = module,
                )
            },
        )

    private def dep(org: String, name: String, version: String, crossVersion: String = "none", platform: Option[String] = None): DepDef = {
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
            name = name,
        )
    }

    // ---- tests ----

    test("generates correct Pkl header with amends directive") {
        val build = singleModuleBuild()
        val result = DederPklRenderer.render(build)
        assert(result.contains(s"""amends "https://sake92.github.io/deder/config/v0.7.4/DederProject.pkl""""))
    }

    test("single Scala module generates CreateScalaModules builder and modules block") {
        val build = singleModuleBuild(name = "myapp")
        val result = DederPklRenderer.render(build)
        assert(result.contains("local const myapp = new CreateScalaModules"))
        assert(result.contains("modules {"))
        assert(result.contains("...myapp.all"))
    }

    test("emits scalacOptions when non-empty") {
        val mod = emptyModule(scalacOptions = Seq("-deprecation", "-Xfatal-warnings"))
        val build = singleModuleBuild(jvmMod = mod)
        val result = DederPklRenderer.render(build)
        assert(result.contains("scalacOptions {"))
        assert(result.contains("\"-deprecation\""))
        assert(result.contains("\"-Xfatal-warnings\""))
    }

    test("emits javacOptions when non-empty") {
        val mod = emptyModule(javacOptions = Seq("-Xlint:unchecked"))
        val build = singleModuleBuild(jvmMod = mod)
        val result = DederPklRenderer.render(build)
        assert(result.contains("javacOptions {"))
        assert(result.contains("\"-Xlint:unchecked\""))
    }

    test("does not emit scalacOptions when empty") {
        val mod = emptyModule(scalacOptions = Seq.empty)
        val build = singleModuleBuild(jvmMod = mod)
        val result = DederPklRenderer.render(build)
        assert(!result.contains("scalacOptions"))
    }

    test("does not emit javacOptions when empty") {
        val mod = emptyModule(javacOptions = Seq.empty)
        val build = singleModuleBuild(jvmMod = mod)
        val result = DederPklRenderer.render(build)
        assert(!result.contains("javacOptions"))
    }

    test("places regular compile deps in deps block") {
        val d = dep("org.jsoup", "jsoup", "1.21.1")
        val mod = emptyModule(deps = Seq(d))
        val build = singleModuleBuild(jvmMod = mod)
        val result = DederPklRenderer.render(build)
        assert(result.contains("deps {"))
        assert(result.contains("\"org.jsoup:jsoup:1.21.1\""))
    }

    test("places compiler plugin deps in scalacPluginDeps block") {
        val pluginD = dep("org.wartremover", "wartremover", "3.5.0", crossVersion = "full")
        val mod = emptyModule(scalacPluginDeps = Seq(pluginD))
        val build = singleModuleBuild(jvmMod = mod)
        val result = DederPklRenderer.render(build)
        assert(result.contains("scalacPluginDeps {"))
        assert(result.contains("\"org.wartremover:::wartremover:3.5.0\""))
        val templateIdx = result.indexOf("template = new ScalaModule")
        val testTemplateIdx = result.indexOf("testTemplate = (template.asTest())")
        val between = result.substring(templateIdx, testTemplateIdx)
        assert(between.contains("scalacPluginDeps"), "main template should have scalacPluginDeps")
        assert(!between.contains("deps {"), "main template should not have regular deps")
    }

    test("test deps appear in testTemplate only") {
        val testD = dep("org.scalameta", "munit", "1.2.1", crossVersion = "binary")
        val mod = emptyModule(testDeps = Seq(testD))
        val build = singleModuleBuild(jvmMod = mod)
        val result = DederPklRenderer.render(build)
        val mainTemplateIdx = result.indexOf("template = new ScalaModule")
        val testTemplateIdx = result.indexOf("testTemplate = (template.asTest())")
        assert(mainTemplateIdx >= 0)
        assert(testTemplateIdx >= 0)
        val between = result.substring(mainTemplateIdx, testTemplateIdx)
        assert(!between.contains("org.scalameta::munit"), "test dep should not leak into main template")
        val afterTest = result.substring(testTemplateIdx)
        assert(afterTest.contains("org.scalameta::munit:1.2.1"), "test dep should be in test template")
    }

    test("resolves compile-scoped moduleDeps") {
        val mod = emptyModule(moduleDeps = Seq(ModuleDepRef("lib", "main", isTest = false)))
        val build = singleModuleBuild(jvmMod = mod)
        val result = DederPklRenderer.render(build)
        assert(result.contains("moduleDeps {"))
        assert(result.contains("lib.main"))
    }

    test("resolves test-scoped moduleDeps with test suffix mapping") {
        val mod = emptyModule(testModuleDeps = Seq(ModuleDepRef("lib", "main", isTest = true)))
        val build = singleModuleBuild(jvmMod = mod)
        val result = DederPklRenderer.render(build)
        assert(result.contains("lib.test"))
    }

    test("emits custom repositories block") {
        val build = singleModuleBuild().copy(repositories = Seq(
            RepositoryDef("https://repo.example.com/maven")
        ))
        val result = DederPklRenderer.render(build)
        assert(result.contains("repositories {"))
        assert(result.contains("new MavenRepository { url = \"https://repo.example.com/maven\" }"))
    }

    test("emits publish info with pomSettings") {
        val mod = emptyModule(publish = Some(PublishInfo(
            organization = "com.example",
            artifactName = "myapp",
            version = "1.0.0",
            description = Some("A test app"),
            homepage = Some("https://example.com"),
            developers = Seq(DeveloperDef("dev1", "Dev One", "dev1@example.com")),
            licenses = Seq(LicenseDef("MIT", "https://opensource.org/licenses/MIT")),
            scmInfo = None,
        )))
        val build = singleModuleBuild(jvmMod = mod)
        val result = DederPklRenderer.render(build)
        assert(result.contains("pomSettings {"))
        assert(result.contains("groupId = \"com.example\""))
        assert(result.contains("version = \"1.0.0\""))
    }

    test("sanitizes module names with dots and hyphens into valid Pkl identifiers") {
        val build = singleModuleBuild(name = "my_lib_ext")
        val result = DederPklRenderer.render(build)
        assert(result.contains("local const my_lib_ext"))
        assert(result.contains("id = \"my_lib_ext\""))
        assert(result.contains("...my_lib_ext.all"))
    }

    test("cross-project with JS module generates CreateCrossModules") {
        val jvmMod = emptyModule(scalaVersion = "3.3.5")
        val jsMod = emptyModule(scalaVersion = "3.3.5", scalaJsVersion = Some("1.18.2"))
        val build = DederBuild(
            dederVersion = "v0.7.4",
            moduleGroups = Seq(ModuleGroup(
                builderVarName = "core",
                root = ".",
                layout = DederProject.DirLayout.SBT_CROSS_PURE,
                crossScalaVersions = Seq.empty,
                jvmModule = jvmMod,
                jsModule = Some(jsMod),
                nativeModule = None,
                hasJsModule = true,
                hasNativeModule = false,
                usesTpolecat = false,
                usesTypelevel = false,
            )),
            repositories = Seq.empty,
            warnings = Seq.empty,
        )
        val result = DederPklRenderer.render(build)
        assert(result.contains("new CreateCrossModules"))
        assert(result.contains("layout = \"sbt-cross-pure\""))
        assert(result.contains("jsTemplate = (template.asJs())"))
        assert(result.contains("scalaJsVersion = \"1.18.2\""))
        assert(result.contains("core.jvm"))
        assert(result.contains("core.js"))
    }

    test("cross-version single module renders map/flatten with Modules suffix") {
        val build = singleModuleBuild(name = "lib", crossScalaVersions = Seq("2.12.21", "2.13.18"))
        val result = DederPklRenderer.render(build)
        assert(result.contains("local const libScalaVersions = List(\"2.12.21\", \"2.13.18\")"))
        assert(result.contains("local const libModules = libScalaVersions"))
        assert(result.contains(".map((sv) ->"))
        assert(result.contains("scalaVersion = sv"))
        assert(result.contains("...libModules"))
    }

    test("cross-version multi-module generates find filter for deps") {
        val libMod = emptyModule()
        val rootMod = emptyModule(moduleDeps = Seq(ModuleDepRef("lib", "main", isTest = false)))
        val groups = Seq(
            ModuleGroup("lib", "lib", DederProject.DirLayout.SBT, Seq("2.12.21", "2.13.18"), libMod, None, None, false, false, false, false),
            ModuleGroup("root", "root", DederProject.DirLayout.SBT, Seq("2.12.21", "2.13.18"), rootMod, None, None, false, false, false, false),
        )
        val build = DederBuild("v0.7.4", groups, Seq.empty, Seq.empty)
        val result = DederPklRenderer.render(build)
        assert(result.contains("libModules.find((m) -> m.id == \"lib-\\(sv)\")"))
        assert(result.contains("local const projectScalaVersions"))
    }

    test("cross-version cross-platform uses id without version in CreateCrossModules") {
        val coreMod = emptyModule()
        val jsMod = emptyModule(scalaJsVersion = Some("1.18.2"))
        val appMod = emptyModule(moduleDeps = Seq(ModuleDepRef("core", "jvm", isTest = false)))
        val groups = Seq(
            ModuleGroup("core", "core", DederProject.DirLayout.SBT_CROSS_FULL, Seq("2.12.21"), coreMod, Some(jsMod), None, true, false, false, false),
            ModuleGroup("app", "app", DederProject.DirLayout.SBT_CROSS_FULL, Seq("2.12.21"), appMod, None, None, false, false, false, false),
        )
        val build = DederBuild("v0.7.4", groups, Seq.empty, Seq.empty)
        val result = DederPklRenderer.render(build)
        assert(result.contains("id = \"core\""))
        assert(result.contains("coreModules.find((m) -> m.id == \"core-jvm-\\(sv)\")"))
        assert(result.contains("new CreateCrossModules"))
    }

    test("keeps compact map rendering when per-version slices differ only by scalaVersion") {
        val versions = Seq("2.12.21", "2.13.18")
        val lib = concreteCrossGroup(
            name = "lib",
            versions = versions,
            slices = versions.map(v => (v, "main", emptyModule(
                scalaVersion = v,
                deps = Seq(dep("org.jsoup", "jsoup", "1.21.1")),
            ))),
        )
        val app = concreteCrossGroup(
            name = "app",
            versions = versions,
            slices = versions.map(v => (v, "main", emptyModule(
                scalaVersion = v,
                moduleDeps = Seq(ModuleDepRef("lib", "main", targetScalaVersion = Some(v), isTest = false)),
            ))),
        )
        val build = DederBuild("v0.7.4", Seq(lib, app), Seq.empty, Seq.empty)

        val result = DederPklRenderer.render(build)
        assert(result.contains("local const appModules = projectScalaVersions"))
        assert(result.contains(".map((sv) ->"))
        assert(result.contains("libModules.find((m) -> m.id == \"lib-\\(sv)\")"))
    }

    test("renders when clauses for version-specific deps in .map() output") {
        val versions = Seq("2.12.21", "2.13.18")
        val group = concreteCrossGroup(
            name = "lib",
            versions = versions,
            slices = Seq(
                ("2.12.21", "main", emptyModule(scalaVersion = "2.12.21")),
                ("2.13.18", "main", emptyModule(
                    scalaVersion = "2.13.18",
                    deps = Seq(dep("org.typelevel", "cats-core", "2.12.0", crossVersion = "binary")),
                )),
            ),
        )
        val build = DederBuild("v0.7.4", Seq(group), Seq.empty, Seq.empty)
        val result = DederPklRenderer.render(build)

        assert(result.contains("local const libModules = libScalaVersions"), clues(result))
        assert(result.contains(".map((sv) ->"), clues(result))
        assert(result.contains("when (sv == \"2.13.18\")"), clues(result))
        assert(result.contains("\"org.typelevel::cats-core:2.12.0\""), clues(result))
        assert(!result.contains("id = \"lib-2.12.21\""), clues(result))
        assert(!result.contains("id = \"lib-2.13.18\""), clues(result))
    }

    test("renders when clauses for version-specific module deps in .map() output") {
        val versions = Seq("2.12.21", "2.13.18")
        val lib = concreteCrossGroup(
            name = "lib", versions = versions,
            slices = versions.map(v => (v, "main", emptyModule(scalaVersion = v))),
        )
        val app = concreteCrossGroup(
            name = "app", versions = versions,
            slices = Seq(
                ("2.12.21", "main", emptyModule(scalaVersion = "2.12.21")),
                ("2.13.18", "main", emptyModule(
                    scalaVersion = "2.13.18",
                    moduleDeps = Seq(ModuleDepRef("lib", "main", targetScalaVersion = Some("2.13.18"), isTest = false)),
                )),
            ),
        )
        val build = DederBuild("v0.7.4", Seq(lib, app), Seq.empty, Seq.empty)
        val result = DederPklRenderer.render(build)

        assert(result.contains("local const appModules = projectScalaVersions"), clues(result))
        assert(result.contains(".map((sv) ->"), clues(result))
        assert(result.contains("when (sv == \"2.13.18\")"), clues(result))
    }

    test("always renders all platforms in .map() output even when platform missing per version") {
        val versions = Seq("2.12.21", "2.13.18")
        val group = concreteCrossGroup(
            name = "core", versions = versions,
            layout = DederProject.DirLayout.SBT_CROSS_FULL,
            slices = Seq(
                ("2.12.21", "jvm", emptyModule(scalaVersion = "2.12.21")),
                ("2.12.21", "js", emptyModule(scalaVersion = "2.12.21", scalaJsVersion = Some("1.18.2"))),
                ("2.13.18", "jvm", emptyModule(scalaVersion = "2.13.18")),
            ),
        )
        val build = DederBuild("v0.7.4", Seq(group), Seq.empty, Seq.empty)
        val result = DederPklRenderer.render(build)

        assert(result.contains("local const coreModules = coreScalaVersions"), clues(result))
        assert(result.contains(".map((sv) ->"), clues(result))
        assert(result.contains("jsTemplate = (template.asJs())"), clues(result))
    }

    test("renders sparse cross-version slices in .map() output") {
        val versions = Seq("2.12.21", "2.13.18")
        val group = concreteCrossGroup(
            name = "core", versions = versions,
            layout = DederProject.DirLayout.SBT_CROSS_FULL,
            slices = Seq(
                ("2.12.21", "jvm", emptyModule(scalaVersion = "2.12.21")),
                ("2.12.21", "js", emptyModule(scalaVersion = "2.12.21", scalaJsVersion = Some("1.18.2"))),
                ("2.13.18", "js", emptyModule(scalaVersion = "2.13.18", scalaJsVersion = Some("1.18.2"))),
            ),
        )
        val build = DederBuild("v0.7.4", Seq(group), Seq.empty, Seq.empty)
        val result = DederPklRenderer.render(build)

        assert(result.contains(".map((sv) ->"), clues(result))
        assert(result.contains("jsTemplate = (template.asJs())"), clues(result))
    }

    test("cross-version module dep on non-cross module uses direct accessor, not find filter") {
        // lib is single-version (no crossScalaVersions); app is cross-version and depends on lib.
        // The renderer must emit `lib.main` not `libModules.find(...)` for the non-cross target.
        val versions = Seq("2.12.21", "2.13.18")
        val lib = ModuleGroup("lib", "lib", DederProject.DirLayout.SBT, Seq.empty,
            emptyModule(scalaVersion = "2.12.21"), None, None, false, false, false, false)
        val app = concreteCrossGroup(
            name = "app",
            versions = versions,
            slices = versions.map(v => (v, "main", emptyModule(
                scalaVersion = v,
                moduleDeps = Seq(ModuleDepRef("lib", "main", isTest = false)),
            ))),
        )
        val build = DederBuild("v0.7.4", Seq(lib, app), Seq.empty, Seq.empty)
        val result = DederPklRenderer.render(build)
        assert(!result.contains("libModules.find"), clues(result))
        assert(result.contains("lib.main"), clues(result))
    }

    test("renders when clauses for version-specific scalacOptions in .map() output") {
        val versions = Seq("2.12.21", "2.13.18")
        val group = concreteCrossGroup(
            name = "lib", versions = versions,
            slices = Seq(
                ("2.12.21", "main", emptyModule(scalaVersion = "2.12.21", scalacOptions = Seq("-deprecation"))),
                ("2.13.18", "main", emptyModule(scalaVersion = "2.13.18", scalacOptions = Seq("-Xfatal-warnings"))),
            ),
        )
        val build = DederBuild("v0.7.4", Seq(group), Seq.empty, Seq.empty)
        val result = DederPklRenderer.render(build)

        assert(result.contains("local const libModules = libScalaVersions"), clues(result))
        assert(result.contains(".map((sv) ->"), clues(result))
        assert(result.contains("when (sv == \"2.12.21\")"), clues(result))
        assert(result.contains("\"-deprecation\""), clues(result))
        assert(result.contains("when (sv == \"2.13.18\")"), clues(result))
        assert(result.contains("\"-Xfatal-warnings\""), clues(result))
    }

    test("cross-version with differing scalacOptions uses .map() with when clauses") {
        val versions = Seq("2.12.21", "2.13.18")
        val group = concreteCrossGroup(
            name = "lib",
            versions = versions,
            slices = Seq(
                ("2.12.21", "main", emptyModule(
                    scalaVersion = "2.12.21",
                    scalacOptions = Seq("-deprecation"),
                )),
                ("2.13.18", "main", emptyModule(
                    scalaVersion = "2.13.18",
                    scalacOptions = Seq("-deprecation", "-Xsource:3"),
                )),
            ),
        )
        val build = DederBuild("v0.7.4", Seq(group), Seq.empty, Seq.empty)
        val result = DederPklRenderer.render(build)

        assert(result.contains("local const libScalaVersions = List(\"2.12.21\", \"2.13.18\")"), clues(result))
        assert(result.contains(".map((sv) ->"), clues(result))
        assert(result.contains("\"-deprecation\""), clues(result))
        assert(result.contains("when (sv == \"2.13.18\")"), clues(result))
        assert(result.contains("\"-Xsource:3\""), clues(result))
        assert(!result.contains("id = \"lib-2.12.21\""), clues(result))
        assert(!result.contains("id = \"lib-2.13.18\""), clues(result))
    }

    test("cross-version with differing deps uses .map() with when clauses") {
        val versions = Seq("2.12.21", "2.13.18")
        val group = concreteCrossGroup(
            name = "lib",
            versions = versions,
            slices = Seq(
                ("2.12.21", "main", emptyModule(
                    scalaVersion = "2.12.21",
                    deps = Seq(dep("org.jsoup", "jsoup", "1.21.1")),
                )),
                ("2.13.18", "main", emptyModule(
                    scalaVersion = "2.13.18",
                    deps = Seq(
                        dep("org.jsoup", "jsoup", "1.21.1"),
                        dep("org.typelevel", "cats-core", "2.12.0", crossVersion = "binary"),
                    ),
                )),
            ),
        )
        val build = DederBuild("v0.7.4", Seq(group), Seq.empty, Seq.empty)
        val result = DederPklRenderer.render(build)

        assert(result.contains(".map((sv) ->"), clues(result))
        assert(result.contains("\"org.jsoup:jsoup:1.21.1\""), clues(result))
        assert(result.contains("when (sv == \"2.13.18\")"), clues(result))
        assert(result.contains("\"org.typelevel::cats-core:2.12.0\""), clues(result))
    }

    test("cross-version with differing moduleDeps uses .map() with when clauses") {
        val versions = Seq("2.12.21", "2.13.18")
        val lib = concreteCrossGroup(
            name = "lib",
            versions = versions,
            slices = versions.map(v => (v, "main", emptyModule(scalaVersion = v))),
        )
        val app = concreteCrossGroup(
            name = "app",
            versions = versions,
            slices = Seq(
                ("2.12.21", "main", emptyModule(scalaVersion = "2.12.21")),
                ("2.13.18", "main", emptyModule(
                    scalaVersion = "2.13.18",
                    moduleDeps = Seq(ModuleDepRef("lib", "main", targetScalaVersion = Some("2.13.18"), isTest = false)),
                )),
            ),
        )
        val build = DederBuild("v0.7.4", Seq(lib, app), Seq.empty, Seq.empty)
        val result = DederPklRenderer.render(build)

        assert(result.contains(".map((sv) ->"), clues(result))
        assert(result.contains("when (sv == \"2.13.18\")"), clues(result))
        assert(result.contains("libModules.find((m) -> m.id == \"lib-\\(sv)\")"), clues(result))
        assert(!result.contains("id = \"app-2.12.21\""), clues(result))
        assert(!result.contains("id = \"app-2.13.18\""), clues(result))
    }

    test("emits DederTpolecat.pkl import and shared reference when tpolecat detected") {
        val mod = emptyModule(scalacOptions = Seq("-deprecation"))
        val build = DederBuild(
            dederVersion = "v0.7.4",
            moduleGroups = Seq(ModuleGroup(
                builderVarName = "lib", root = ".", layout = DederProject.DirLayout.SBT,
                crossScalaVersions = Seq("2.13.18"),
                jvmModule = mod, jsModule = None, nativeModule = None,
                hasJsModule = false, hasNativeModule = false,
                usesTpolecat = true, usesTypelevel = false,
            )),
            repositories = Seq.empty, warnings = Seq.empty,
        )
        val result = DederPklRenderer.render(build)
        assert(result.contains("""import "DederTpolecat.pkl""""), clues(result))
        assert(result.contains("scalacOptions = DederTpolecat.forVersion(sv)"), clues(result))
        assert(!result.contains("scalacOptions {"), clues(result)) // no verbatim options
    }

    test("emits DederTypelevel.pkl import and shared reference when typelevel detected") {
        val mod = emptyModule()
        val build = DederBuild(
            dederVersion = "v0.7.4",
            moduleGroups = Seq(ModuleGroup(
                builderVarName = "lib", root = ".", layout = DederProject.DirLayout.SBT,
                crossScalaVersions = Seq.empty,
                jvmModule = mod, jsModule = None, nativeModule = None,
                hasJsModule = false, hasNativeModule = false,
                usesTpolecat = false, usesTypelevel = true,
            )),
            repositories = Seq.empty, warnings = Seq.empty,
        )
        val result = DederPklRenderer.render(build)
        assert(result.contains("""import "DederTypelevel.pkl""""), clues(result))
        assert(result.contains("scalacOptions = DederTypelevel.forVersion("), clues(result))
    }

    test("emits raw scalacOptions when neither tpolecat nor typelevel detected") {
        val mod = emptyModule(scalacOptions = Seq("-deprecation"))
        val build = DederBuild(
            dederVersion = "v0.7.4",
            moduleGroups = Seq(ModuleGroup(
                builderVarName = "lib", root = ".", layout = DederProject.DirLayout.SBT,
                crossScalaVersions = Seq.empty,
                jvmModule = mod, jsModule = None, nativeModule = None,
                hasJsModule = false, hasNativeModule = false,
                usesTpolecat = false, usesTypelevel = false,
            )),
            repositories = Seq.empty, warnings = Seq.empty,
        )
        val result = DederPklRenderer.render(build)
        assert(!result.contains("DederTpolecat"), clues(result))
        assert(!result.contains("DederTypelevel"), clues(result))
        assert(result.contains("scalacOptions {"), clues(result))
        assert(result.contains("\"-deprecation\""), clues(result))
    }
}
