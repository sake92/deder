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
    ): ModuleDef = ModuleDef(
        scalaVersion = scalaVersion,
        scalacOptions = scalacOptions,
        javacOptions = javacOptions,
        crossScalaVersions = Seq.empty,
        deps = deps,
        scalacPluginDeps = scalacPluginDeps,
        testDeps = testDeps,
        moduleDeps = moduleDeps,
        testModuleDeps = testModuleDeps,
        scalaJsVersion = None,
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
        dederVersion: String = "v0.7.4",
    ): DederBuild = DederBuild(
        dederVersion = dederVersion,
        moduleGroups = Seq(ModuleGroup(
            builderVarName = name,
            root = ".",
            layout = layout,
            jvmModule = jvmMod,
            jsModule = jsMod,
            nativeModule = nativeMod,
            hasJsModule = jsMod.isDefined,
            hasNativeModule = nativeMod.isDefined,
        )),
        repositories = Seq.empty,
        warnings = Seq.empty,
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
}
