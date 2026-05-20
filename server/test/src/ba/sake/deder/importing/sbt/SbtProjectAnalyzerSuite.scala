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
        repositories: Seq[String] = Seq.empty,
    ): ExportedProjectExportFile = ExportedProjectExportFile(
        payload = ProjectExport(
            id = id,
            base = base,
            name = name,
            javacOptions = javacOptions,
            scalaVersion = scalaVersion,
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
        ),
        exportedProjectId = id,
        exportedScalaVersion = scalaVersion,
        platform = ExportedPlatform.fromBase(base),
    )

    private def concreteModule(
        group: ModuleGroup,
        scalaVersion: String,
        platform: String,
    ): ConcreteModule =
        group.concreteModules.find(cm =>
            cm.scalaVersion == scalaVersion && cm.platform == platform
        ).getOrElse(fail(s"Missing concrete module for scalaVersion=$scalaVersion platform=$platform"))

    private def withDirs(dirs: Seq[os.Path])(body: => Unit): Unit = {
        dirs.foreach(os.makeDir.all)
        try body
        finally dirs.reverse.foreach { dir =>
            if os.exists(dir) then os.remove.all(dir)
        }
    }

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

    test("surfaces crossScalaVersions on ModuleGroup") {
        val mod213 = baseModule("app", os.pwd.toString, "app", scalaVersion = "2.13.15")
        val mod3 = baseModule("app", os.pwd.toString, "app", scalaVersion = "3.3.5")
        val analyzer = new SbtProjectAnalyzer(noopLogger)
        val build = analyzer.analyze(IndexedSeq(mod213, mod3))
        val group = build.moduleGroups.head
        assertEquals(group.crossScalaVersions, Seq("2.13.15", "3.3.5"))
    }

    test("preserves distinct single-platform per-version exports as concrete modules") {
        val mod213 = baseModule("app", os.pwd.toString, "app", scalaVersion = "2.13.15")
        val mod3 = baseModule(
            "app",
            os.pwd.toString,
            "app",
            scalaVersion = "3.3.5",
            scalacOptions = Seq("-Ykind-projector:underscores"),
        )
        val analyzer = new SbtProjectAnalyzer(noopLogger)
        val build = analyzer.analyze(IndexedSeq(mod213, mod3))
        val group = build.moduleGroups.head

        assertEquals(
            group.concreteModules.map(cm => (cm.scalaVersion, cm.platform, cm.sbtProjectId)),
            Seq(
                ("2.13.15", "main", "app"),
                ("3.3.5", "main", "app"),
            )
        )
        assertEquals(concreteModule(group, "2.13.15", "main").module.scalacOptions, Seq.empty)
        assertEquals(
            concreteModule(group, "3.3.5", "main").module.scalacOptions,
            Seq("-Ykind-projector:underscores")
        )
    }

    test("preserves concrete platform and scala-version slices for cross-platform builds") {
        val root = os.pwd / "target" / "sbt-project-analyzer-suite" / "core"
        val rootStr = root.toString
        val plugins = Seq("ScalaJSCrossPlugin")
        val analyzer = new SbtProjectAnalyzer(noopLogger)
        withDirs(Seq(root / "jvm", root / "js")) {
            val build = analyzer.analyze(IndexedSeq(
                baseModule("core", s"$rootStr/jvm", "coreJVM", scalaVersion = "2.13.15", plugins = plugins),
                baseModule("core", s"$rootStr/js", "coreJS", scalaVersion = "2.13.15", plugins = plugins),
                baseModule("core", s"$rootStr/jvm", "coreJVM", scalaVersion = "3.3.5", plugins = plugins),
                baseModule("core", s"$rootStr/js", "coreJS", scalaVersion = "3.3.5", plugins = plugins),
            ))

            val group = build.moduleGroups.head
            assertEquals(group.crossScalaVersions, Seq("2.13.15", "3.3.5"))
            assertEquals(
                group.concreteModules.map(cm => (cm.scalaVersion, cm.platform)).sorted,
                Seq(
                    ("2.13.15", "js"),
                    ("2.13.15", "jvm"),
                    ("3.3.5", "js"),
                    ("3.3.5", "jvm"),
                )
            )
        }
    }

    test("resolves inter-project deps to the matching scala-version slice") {
        val lib213 = baseModule("lib", (os.pwd / "lib").toString, "lib", scalaVersion = "2.13.15")
        val lib3 = baseModule("lib", (os.pwd / "lib").toString, "lib", scalaVersion = "3.3.5")
        val app213 = baseModule(
            "app",
            (os.pwd / "app").toString,
            "app",
            scalaVersion = "2.13.15",
            interProjectDeps = Seq(InterProjectDependencyExport("lib", "default")),
        )
        val app3 = baseModule(
            "app",
            (os.pwd / "app").toString,
            "app",
            scalaVersion = "3.3.5",
            interProjectDeps = Seq(InterProjectDependencyExport("lib", "default")),
        )
        val analyzer = new SbtProjectAnalyzer(noopLogger)
        val build = analyzer.analyze(IndexedSeq(app3, lib3, app213, lib213))
        val appGroup = build.moduleGroups.find(_.builderVarName == "app").get

        assertEquals(
            concreteModule(appGroup, "2.13.15", "main").module.moduleDeps,
            Seq(ModuleDepRef("lib", "main", targetScalaVersion = Some("2.13.15"), isTest = false))
        )
        assertEquals(
            concreteModule(appGroup, "3.3.5", "main").module.moduleDeps,
            Seq(ModuleDepRef("lib", "main", targetScalaVersion = Some("3.3.5"), isTest = false))
        )
    }

    test("falls back to the only exported slice when inter-project deps cross scala patch versions") {
        val root = os.pwd / "target" / "sbt-project-analyzer-suite" / "snowplow-repro"
        val compatibility = baseModule(
            id = "compatibility",
            base = (root / "compatibility").toString,
            name = "msc_compatibility",
            scalaVersion = "2.13.18",
            interProjectDeps = Seq(
                InterProjectDependencyExport("schemaDdlSubschema", "test->test;compile->compile"),
            ),
        )
        val schemaDdlSubschema = baseModule(
            id = "schemaDdlSubschema",
            base = (root / "schema-ddl-subschema").toString,
            name = "schema_ddl_subschema",
            scalaVersion = "2.13.17",
        )
        val analyzer = new SbtProjectAnalyzer(noopLogger)
        val build = analyzer.analyze(IndexedSeq(compatibility, schemaDdlSubschema))
        val compatibilityGroup = build.moduleGroups.find(_.builderVarName == "msc_compatibility").get

        assertEquals(
            concreteModule(compatibilityGroup, "2.13.18", "main").module.moduleDeps,
            Seq(ModuleDepRef("schema_ddl_subschema", "main", targetScalaVersion = Some("2.13.17"), isTest = false))
        )
        assertEquals(
            concreteModule(compatibilityGroup, "2.13.18", "main").module.testModuleDeps,
            Seq(ModuleDepRef("schema_ddl_subschema", "main", targetScalaVersion = Some("2.13.17"), isTest = true))
        )
        assert(
            build.moduleGroups.map(_.builderVarName).indexOf("schema_ddl_subschema") <
                build.moduleGroups.map(_.builderVarName).indexOf("msc_compatibility")
        )

        val rendered = DederPklRenderer.render(build)
        assert(rendered.contains("schema_ddl_subschema.main"))
    }

    test("does not fall back when multiple exported slices exist and none matches") {
        val root = os.pwd / "target" / "sbt-project-analyzer-suite" / "no-fallback-guard"
        val depender = baseModule(
            id = "depender",
            base = (root / "depender").toString,
            name = "depender",
            scalaVersion = "2.12.19",
            interProjectDeps = Seq(
                InterProjectDependencyExport("library", "test->test;compile->compile"),
            ),
        )
        val library213 = baseModule(
            id = "library",
            base = (root / "library").toString,
            name = "library",
            scalaVersion = "2.13.15",
        )
        val library3 = baseModule(
            id = "library",
            base = (root / "library").toString,
            name = "library",
            scalaVersion = "3.3.5",
        )
        val analyzer = new SbtProjectAnalyzer(noopLogger)
        val build = analyzer.analyze(IndexedSeq(depender, library213, library3))
        val dependerGroup = build.moduleGroups.find(_.builderVarName == "depender").get

        assertEquals(concreteModule(dependerGroup, "2.12.19", "main").module.moduleDeps, Seq.empty)
        assertEquals(concreteModule(dependerGroup, "2.12.19", "main").module.testModuleDeps, Seq.empty)
    }

    test("does not fall back when multiple refs exist for the same project and scala version") {
        val root = os.pwd / "target" / "sbt-project-analyzer-suite" / "no-fallback-same-version"
        val depender = baseModule(
            id = "depender",
            base = (root / "depender").toString,
            name = "depender",
            scalaVersion = "2.12.19",
            interProjectDeps = Seq(
                InterProjectDependencyExport("library", "test->test;compile->compile"),
            ),
        )
        val libraryJvm = baseModule(
            id = "library",
            base = (root / "library" / "jvm").toString,
            name = "libraryJVM",
            scalaVersion = "3.3.5",
            plugins = Seq("ScalaJSCrossPlugin"),
        )
        val libraryJs = baseModule(
            id = "library",
            base = (root / "library" / "js").toString,
            name = "libraryJS",
            scalaVersion = "3.3.5",
            plugins = Seq("ScalaJSCrossPlugin"),
        )
        val analyzer = new SbtProjectAnalyzer(noopLogger)
        withDirs(Seq(root / "library" / "jvm", root / "library" / "js")) {
            val build = analyzer.analyze(IndexedSeq(depender, libraryJvm, libraryJs))
            val dependerGroup = build.moduleGroups.find(_.builderVarName == "depender").get

            assertEquals(concreteModule(dependerGroup, "2.12.19", "main").module.moduleDeps, Seq.empty)
            assertEquals(concreteModule(dependerGroup, "2.12.19", "main").module.testModuleDeps, Seq.empty)
        }
    }

    test("does not exact-match when multiple refs share the same project and scala version") {
        val root = os.pwd / "target" / "sbt-project-analyzer-suite" / "no-exact-match-same-version"
        val depender = baseModule(
            id = "depender",
            base = (root / "depender").toString,
            name = "depender",
            scalaVersion = "3.3.5",
            interProjectDeps = Seq(
                InterProjectDependencyExport("library", "test->test;compile->compile"),
            ),
        )
        val libraryJvm = baseModule(
            id = "library",
            base = (root / "library" / "jvm").toString,
            name = "libraryJVM",
            scalaVersion = "3.3.5",
            plugins = Seq("ScalaJSCrossPlugin"),
        )
        val libraryJs = baseModule(
            id = "library",
            base = (root / "library" / "js").toString,
            name = "libraryJS",
            scalaVersion = "3.3.5",
            plugins = Seq("ScalaJSCrossPlugin"),
        )
        val analyzer = new SbtProjectAnalyzer(noopLogger)
        withDirs(Seq(root / "library" / "jvm", root / "library" / "js")) {
            val build = analyzer.analyze(IndexedSeq(depender, libraryJvm, libraryJs))
            val dependerGroup = build.moduleGroups.find(_.builderVarName == "depender").get

            assertEquals(concreteModule(dependerGroup, "3.3.5", "main").module.moduleDeps, Seq.empty)
            assertEquals(concreteModule(dependerGroup, "3.3.5", "main").module.testModuleDeps, Seq.empty)
        }
    }

    test("counts actual concrete slices when version-platform matrix is sparse") {
        val dep213Jvm = DependencyExport(
            organization = "org.example", name = "core-jvm-213", revision = "1.0.0",
            extraAttributes = Map.empty, configurations = None, excludes = Seq.empty, crossVersion = "none"
        )
        val dep213Js = DependencyExport(
            organization = "org.example", name = "core-js-213", revision = "1.0.0",
            extraAttributes = Map.empty, configurations = None, excludes = Seq.empty, crossVersion = "none"
        )
        val dep3Jvm = DependencyExport(
            organization = "org.example", name = "core-jvm-3", revision = "1.0.0",
            extraAttributes = Map.empty, configurations = None, excludes = Seq.empty, crossVersion = "none"
        )
        val root = os.pwd / "target" / "sbt-project-analyzer-suite" / "core-matrix"
        val rootStr = root.toString
        val plugins = Seq("ScalaJSCrossPlugin")
        val analyzer = new SbtProjectAnalyzer(noopLogger)
        withDirs(Seq(root / "jvm", root / "js")) {
            analyzer.analyze(IndexedSeq(
                baseModule("core", s"$rootStr/jvm", "coreJVM", scalaVersion = "2.13.15", plugins = plugins, externalDeps = Seq(dep213Jvm)),
                baseModule("core", s"$rootStr/js", "coreJS", scalaVersion = "2.13.15", plugins = plugins, externalDeps = Seq(dep213Js)),
                baseModule("core", s"$rootStr/jvm", "coreJVM", scalaVersion = "3.3.5", plugins = plugins, externalDeps = Seq(dep3Jvm)),
            ))

            val summary = analyzer.summary()
            assertEquals(summary.modulesImported, 6)
            assertEquals(summary.dependenciesMapped, 3)
        }
    }

    test("maps custom repositories") {
        val mod = baseModule("app", os.pwd.toString, "app", repositories = Seq("https://repo.example.com/maven"))
        val analyzer = new SbtProjectAnalyzer(noopLogger)
        val build = analyzer.analyze(IndexedSeq(mod))
        assertEquals(build.repositories.size, 1)
        assertEquals(build.repositories.head.url, "https://repo.example.com/maven")
    }

    test("filterManagedDirs removes src_managed, resource_managed, and target/ paths") {
        val input = Seq(
            "src/main/scala",
            "src/main/resources",
            "target/scala-2.13/src_managed/main",
            "target/resource_managed/main",
            "some/path/with/target/in/middle/src",
        )
        val result = SbtProjectAnalyzer.filterManagedDirs(input)
        assertEquals(result, Seq("src/main/scala", "src/main/resources"))
    }

    test("relativizeTo makes paths relative to base") {
        val base = os.Path("/home/user/project/moduleA")
        val input = Seq(
            "/home/user/project/moduleA/src/main/scala",
            "/home/user/project/moduleA/src/main/resources",
            "/home/user/project/moduleB/src/main/scala",
        )
        val result = SbtProjectAnalyzer.relativizeTo(base, input)
        assertEquals(result, Seq("src/main/scala", "src/main/resources"))
    }

    test("filterManagedDirs handles empty input") {
        val result = SbtProjectAnalyzer.filterManagedDirs(Seq.empty)
        assertEquals(result, Seq.empty)
    }

    test("relativizeTo handles empty input") {
        val result = SbtProjectAnalyzer.relativizeTo(os.pwd, Seq.empty)
        assertEquals(result, Seq.empty)
    }

    test("filterStandardSbtDirs removes standard sbt directories for sbt layout") {
        val input = Seq(
            "src/main/scala",
            "src/main/java",
            "src/main/resources",
            "src/main/scala-2.13",
            "src/main/scala-3",
            "src/main/scala-2.13+",
            "src/test/scala",
            "src/test/scala-2.13",
            "custom/path/to/src",
            "generated/src/main/scala",
        )
        val result = SbtProjectAnalyzer.filterStandardSbtDirs(input,
            ba.sake.deder.config.DederProject.DirLayout.SBT)
        assertEquals(result, Seq("custom/path/to/src", "generated/src/main/scala"))
    }

    test("filterStandardSbtDirs removes cross-platform dirs for sbt-cross-full layout") {
        val input = Seq(
            "shared/src/main/scala",
            "jvm/src/main/scala",
            "js/src/main/scala",
            ".jvm/src/main/scala",
            ".js/src/main/scala",
            "shared/src/main/scala-2.13",
            "jvm/src/main/scala-3",
            "custom/shared/src/main/scala",
        )
        val result = SbtProjectAnalyzer.filterStandardSbtDirs(input,
            ba.sake.deder.config.DederProject.DirLayout.SBT_CROSS_FULL)
        assertEquals(result, Seq("custom/shared/src/main/scala"))
    }

    test("filterStandardSbtDirs does not filter anything for non-sbt layout") {
        val input = Seq("src/main/scala", "src/main/java", "src/main/scala-2.13")
        val result = SbtProjectAnalyzer.filterStandardSbtDirs(input,
            ba.sake.deder.config.DederProject.DirLayout.DEFAULT)
        assertEquals(result, input)
    }
}
