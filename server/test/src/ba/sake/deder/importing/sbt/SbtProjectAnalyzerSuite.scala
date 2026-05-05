package ba.sake.deder.importing.sbt

import munit.FunSuite
import ba.sake.deder.ServerNotificationsLogger
import ba.sake.deder.importing._

class SbtProjectAnalyzerSuite extends FunSuite {

    private val noopLogger = ServerNotificationsLogger(_ => ())

    private def baseModule(
        id: String,
        base: String,
        name: String,
        scalaVersion: String = "3.3.5",
        externalDeps: Seq[DependencyExport] = Seq.empty,
        interProjectDeps: Seq[InterProjectDependencyExport] = Seq.empty,
        plugins: Seq[String] = Seq.empty,
        scalacOptions: Seq[String] = Seq.empty,
        javacOptions: Seq[String] = Seq.empty,
        crossScalaVersions: Seq[String] = Seq.empty,
        repositories: Seq[String] = Seq.empty,
    ): ProjectExport = ProjectExport(
        id = id,
        base = base,
        name = name,
        javacOptions = javacOptions,
        scalaVersion = scalaVersion,
        crossScalaVersions = crossScalaVersions,
        scalacOptions = scalacOptions,
        interProjectDependencies = interProjectDeps,
        externalDependencies = externalDeps,
        repositories = repositories,
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

    test("single module produces one ModuleGroup") {
        val mod = baseModule("myapp", os.pwd.toString, "myapp")
        val analyzer = new SbtProjectAnalyzer(noopLogger)
        val build = analyzer.analyze(IndexedSeq(mod))
        assertEquals(build.moduleGroups.size, 1)
        assertEquals(build.moduleGroups.head.jvmModule.scalaVersion, "3.3.5")
    }

    test("topological sort: independent modules appear before dependents") {
        val a = baseModule("a", (os.pwd / "a").toString, "a")
        val b = baseModule("b", (os.pwd / "b").toString, "b",
            interProjectDeps = Seq(InterProjectDependencyExport("a", "default"))
        )
        val c = baseModule("c", (os.pwd / "c").toString, "c",
            interProjectDeps = Seq(InterProjectDependencyExport("b", "default"))
        )
        val analyzer = new SbtProjectAnalyzer(noopLogger)
        val build = analyzer.analyze(IndexedSeq(c, b, a))
        val names = build.moduleGroups.map(_.builderVarName)
        assertEquals(names.indexOf("a"), 0)
        assertEquals(names.indexOf("b"), 1)
        assertEquals(names.indexOf("c"), 2)
    }

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
        val analyzer = new SbtProjectAnalyzer(noopLogger)
        val build = analyzer.analyze(IndexedSeq(mod))
        val depNames = build.moduleGroups.head.jvmModule.deps.map(_.name)
        assert(!depNames.contains("scala3-library"))
        assert(depNames.contains("jsoup"))
    }

    test("preserves scalacOptions from ProjectExport") {
        val mod = baseModule("app", os.pwd.toString, "app", scalacOptions = Seq("-deprecation", "-Xfatal-warnings"))
        val analyzer = new SbtProjectAnalyzer(noopLogger)
        val build = analyzer.analyze(IndexedSeq(mod))
        val opts = build.moduleGroups.head.jvmModule.scalacOptions
        assertEquals(opts, Seq("-deprecation", "-Xfatal-warnings"))
    }

    test("preserves javacOptions from ProjectExport") {
        val mod = baseModule("app", os.pwd.toString, "app", javacOptions = Seq("-Xlint:unchecked"))
        val analyzer = new SbtProjectAnalyzer(noopLogger)
        val build = analyzer.analyze(IndexedSeq(mod))
        val opts = build.moduleGroups.head.jvmModule.javacOptions
        assertEquals(opts, Seq("-Xlint:unchecked"))
    }

    test("generates CrossScalaVersionsWarning when crossScalaVersions is non-empty") {
        val mod = baseModule("app", os.pwd.toString, "app", crossScalaVersions = Seq("2.13.15"))
        val analyzer = new SbtProjectAnalyzer(noopLogger)
        val build = analyzer.analyze(IndexedSeq(mod))
        assert(build.warnings.nonEmpty)
        assert(build.warnings.exists {
            case ImportWarning.CrossScalaVersionsNotSupported(name, _, _) => name == "app"
            case _ => false
        })
    }

    test("maps custom repositories") {
        val mod = baseModule("app", os.pwd.toString, "app", repositories = Seq("https://repo.example.com/maven"))
        val analyzer = new SbtProjectAnalyzer(noopLogger)
        val build = analyzer.analyze(IndexedSeq(mod))
        assertEquals(build.repositories.size, 1)
        assertEquals(build.repositories.head.url, "https://repo.example.com/maven")
    }
}
