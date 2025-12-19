package ba.sake.deder

import java.io.File
import java.net.URLClassLoader
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import dependency.parser.DependencyParser
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import ba.sake.tupson.JsonRW
import ba.sake.deder.zinc.{DederZincLogger, ZincCompiler}
import ba.sake.deder.config.DederProject.{DederModule, JavaModule, ModuleType, ScalaModule, ScalaTestModule}
import ba.sake.deder.deps.Dependency
import ba.sake.deder.deps.DependencyResolver
import ba.sake.deder.deps.given
import ba.sake.deder.testing.*

class CoreTasks() {

  private def makeZincCompiler(scalaVersion: String) = {
    val dep =
      if scalaVersion.startsWith("3.") then s"org.scala-lang:scala3-sbt-bridge:${scalaVersion}"
      else "org.scala-sbt::compiler-bridge:1.11.0"
    val compilerBridgeJar = DependencyResolver.fetchFile(
      Dependency.make(dep, scalaVersion)
    )
    ZincCompiler(compilerBridgeJar)
  }

  private val zincCache: Cache[String, ZincCompiler] =
    Scaffeine()
      .expireAfterAccess(5.minute)
      .maximumSize(10)
      .build()

  private def getZincCompiler(scalaVersion: String): ZincCompiler =
    zincCache.get(scalaVersion, _ => makeZincCompiler(scalaVersion))

  /** source folders */
  val sourcesTask = SourceFilesTask(
    name = "sources",
    supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.SCALA_TEST, ModuleType.JAVA),
    execute = { ctx =>
      val sources = ctx.module match {
        case m: JavaModule      => m.sources.asScala.toSeq
        case m: ScalaModule     => m.sources.asScala.toSeq
        case m: ScalaTestModule => m.sources.asScala.toSeq
        case _                  => Seq.empty
      }
      // println(s"Module: ${ctx.module.id} sources: " + sources)
      sources.map(s => DederPath(os.SubPath(s"${ctx.module.root}/${s}")))
    }
  )

  /** resource folders */
  val resourcesTask = SourceFilesTask(
    name = "resources",
    supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.SCALA_TEST, ModuleType.JAVA),
    execute = { ctx =>
      val resources = ctx.module match {
        case m: JavaModule      => m.resources.asScala.toSeq
        case m: ScalaModule     => m.resources.asScala.toSeq
        case m: ScalaTestModule => m.resources.asScala.toSeq
        case _                  => Seq.empty
      }
      // println(s"Module: ${ctx.module.id} sources: " + sources)
      resources.map(s => DederPath(os.SubPath(s"${ctx.module.root}/${s}")))
    }
  )

  val javacOptionsTask = ConfigValueTask[Seq[String]](
    name = "javacOptions",
    supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.SCALA_TEST, ModuleType.JAVA),
    execute = { ctx =>
      ctx.module match {
        case m: JavaModule      => m.javacOptions.asScala.toSeq
        case m: ScalaModule     => m.javacOptions.asScala.toSeq
        case m: ScalaTestModule => m.javacOptions.asScala.toSeq
        case _                  => Seq.empty
      }
    }
  )

  val scalacOptionsTask = ConfigValueTask[Seq[String]](
    name = "scalacOptions",
    supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.SCALA_TEST),
    execute = { ctx =>
      ctx.module match {
        case m: ScalaModule     => m.scalacOptions.asScala.toSeq
        case m: ScalaTestModule => m.scalacOptions.asScala.toSeq
        case _                  => Seq.empty
      }
    }
  )

  val scalaVersionTask = ConfigValueTask[String](
    name = "scalaVersion",
    supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.SCALA_TEST, ModuleType.JAVA),
    execute = { ctx =>
      ctx.module match {
        case m: ScalaModule     => m.scalaVersion
        case m: ScalaTestModule => m.scalaVersion
        case _                  => "2.13.17" // dummy default scala version
      }
    }
  )

  val depsTask = ConfigValueTask[Seq[String]](
    name = "deps",
    supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.SCALA_TEST, ModuleType.JAVA),
    execute = { ctx =>
      ctx.module match {
        case m: JavaModule      => m.deps.asScala.toSeq
        case m: ScalaModule     => m.deps.asScala.toSeq
        case m: ScalaTestModule => m.deps.asScala.toSeq
        case _                  => Seq.empty
      }
    }
  )

  // applied deps, with scala version resolved
  val dependenciesTask = TaskBuilder
    .make[Seq[deps.Dependency]](
      name = "dependencies",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.SCALA_TEST, ModuleType.JAVA)
    )
    .dependsOn(depsTask)
    .dependsOn(scalaVersionTask)
    .build { ctx =>
      val (deps, scalaVersion) = ctx.depResults
      deps.map(depDecl => Dependency.make(depDecl, scalaVersion))
    }

  val allDependenciesTask = TaskBuilder
    .make[Seq[deps.Dependency]](
      name = "allDependencies",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.SCALA_TEST, ModuleType.JAVA),
      transitive = true
    )
    .dependsOn(dependenciesTask)
    .build { ctx =>
      val deps = ctx.depResults._1
      (deps ++ ctx.transitiveResults.flatten.flatten).distinct
    }

  val classesDirTask = TaskBuilder
    .make[os.Path](
      name = "classes",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.SCALA_TEST, ModuleType.JAVA)
    )
    .build { ctx => ctx.out }

  // this is localRunClasspath in mill ??
  val allClassesDirsTask = TaskBuilder
    .make[Seq[os.Path]](
      name = "allClassesDirs",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.SCALA_TEST, ModuleType.JAVA),
      transitive = true
    )
    .dependsOn(classesDirTask)
    .build { ctx =>
      Seq(ctx.depResults._1) ++ ctx.transitiveResults.flatten.flatten
    }

  val compileClasspathTask = TaskBuilder
    .make[Seq[os.Path]](
      name = "compileClasspath",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.SCALA_TEST, ModuleType.JAVA),
      transitive = true
    )
    .dependsOn(scalacOptionsTask)
    .dependsOn(scalaVersionTask)
    .dependsOn(allDependenciesTask)
    .dependsOn(classesDirTask)
    .dependsOn(allClassesDirsTask)
    .build { ctx =>
      val (scalacOptions, scalaVersion, dependencies, classesDir, allClassesDirs) = ctx.depResults
      // dirty hack to get class dirs, all except for this module.. :/
      val otherTransitiveClassesDirs = allClassesDirs.filterNot(_ == classesDir)
      val scalaLibDep =
        if scalaVersion.startsWith("3.") then s"org.scala-lang::scala3-library:${scalaVersion}"
        else s"org.scala-lang:scala-library:${scalaVersion}"
      val depsJars = DependencyResolver
        .fetchFiles(
          Seq(Dependency.make(scalaLibDep, scalaVersion)) ++ dependencies,
          Some(ctx.notifications)
        )
      // val additionalCompileClasspath = ctx.transitiveResults.flatten.flatten ++ depsJars
      (otherTransitiveClassesDirs ++ depsJars).reverse.distinct.reverse
    }

  // TODO make configurable
  val javacAnnotationProcessorsTask = TaskBuilder
    .make[Seq[os.Path]](
      name = "javacAnnotationProcessors",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.SCALA_TEST, ModuleType.JAVA)
    )
    .dependsOn(scalaVersionTask)
    .build { ctx =>
      val scalaVersion = ctx.depResults._1
      val processorJars = DependencyResolver.fetchFiles(
        Seq(Dependency.make("com.sourcegraph:semanticdb-javac:0.11.1", scalaVersion)),
        Some(ctx.notifications)
      )
      processorJars
    }

  // TODO make configurable
  val scalacPluginsTask = TaskBuilder
    .make[Seq[os.Path]](
      name = "scalacPlugins",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.SCALA_TEST, ModuleType.JAVA)
    )
    .dependsOn(scalaVersionTask)
    .build { ctx =>
      val scalaVersion = ctx.depResults._1
      // TODO make configurable on/off + version
      val semanticDbDeps =
        if scalaVersion.startsWith("3.") then Seq.empty
        else Seq("org.scalameta:::semanticdb-scalac:4.13.9")
      val pluginJars = DependencyResolver.fetchFiles(
        semanticDbDeps.map(d => Dependency.make(d, scalaVersion)),
        Some(ctx.notifications)
      )
      pluginJars
    }

  val compileTask = TaskBuilder
    .make[DederPath](
      name = "compile",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.SCALA_TEST, ModuleType.JAVA),
      transitive = true
    )
    .dependsOn(sourcesTask)
    .dependsOn(javacOptionsTask)
    .dependsOn(scalacOptionsTask)
    .dependsOn(scalaVersionTask)
    .dependsOn(compileClasspathTask)
    .dependsOn(classesDirTask)
    .dependsOn(scalacPluginsTask)
    .dependsOn(javacAnnotationProcessorsTask)
    .build { ctx =>
      val (
        sourceDirs,
        javacOptions,
        scalacOptions,
        scalaVersion,
        compileClasspath,
        classesDir,
        scalacPlugins,
        javacAnnotationProcessors
      ) = ctx.depResults

      val sourceFiles = sourceDirs
        .flatMap { sourceDir =>
          os.walk(
            sourceDir.absPath,
            skip = p => {
              if os.isDir(p) then false
              else if os.isFile(p) then !(p.ext == "scala" || p.ext == "java")
              else true
            }
          )
        }
        .filter(os.isFile)

      val compilerDeps =
        if scalaVersion.startsWith("3.") then Seq(s"org.scala-lang::scala3-compiler:${scalaVersion}")
        else
          Seq(
            s"org.scala-lang:scala-compiler:${scalaVersion}",
            s"org.scala-lang:scala-reflect:${scalaVersion}"
          )
      val compilerJars = DependencyResolver.fetchFiles(
        compilerDeps.map(d => Dependency.make(d, scalaVersion))
      )

      /*println(s"Compiling module: ${ctx.module.id} with ${(
          scalaVersion,
          compilerJars,
          compileClasspath,
          classesDir
        )}")*/

      val zincCacheFile = ctx.out / "inc_compile.zip"
      val zincLogger = new DederZincLogger(ctx.notifications, ctx.module.id)
      val finalJavacOptions = javacOptions ++
        Seq(
          "-processorpath",
          javacAnnotationProcessors.map(_.toString).mkString(File.pathSeparator),
          s"-Xplugin:semanticdb -sourceroot:${DederGlobals.projectRootDir} -targetroot:${classesDir}"
        )
      val semanticDbScalacOpts =
        if scalaVersion.startsWith("3.") then
          Seq("-Xsemanticdb", s"-sourceroot", s"${DederGlobals.projectRootDir}") ++
            scalacPlugins.map(p => s"-Xplugin:${p.toString}")
        else
          Seq("-Yrangepos", s"-P:semanticdb:sourceroot:${DederGlobals.projectRootDir}") ++
            scalacPlugins.map(p => s"-Xplugin:${p.toString}")

      val finalScalacOptions = scalacOptions ++ semanticDbScalacOpts
      getZincCompiler(scalaVersion).compile(
        scalaVersion,
        compilerJars,
        compileClasspath,
        zincCacheFile,
        sourceFiles,
        classesDir,
        finalScalacOptions,
        finalJavacOptions,
        zincLogger,
        moduleId = ctx.module.id,
        notifications = ctx.notifications
      )
      DederPath(classesDir)
    }

  val runClasspathTask = TaskBuilder
    .make[Seq[os.Path]](
      name = "runClasspath",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.SCALA_TEST, ModuleType.JAVA),
      transitive = true
    )
    .dependsOn(scalaVersionTask)
    .dependsOn(allDependenciesTask)
    .dependsOn(compileTask)
    .build { ctx =>
      val (scalaVersion, dependencies, classesDir) = ctx.depResults
      val mandatoryDeps = ctx.module match {
        case m: JavaModule => Seq.empty
        case _: (ScalaModule | ScalaTestModule) =>
          val scalaLibDep =
            if scalaVersion.startsWith("3.") then s"org.scala-lang::scala3-library:${scalaVersion}"
            else s"org.scala-lang:scala-library:${scalaVersion}"
          Seq(Dependency.make(scalaLibDep, scalaVersion))
        case _ => Seq.empty
      }
      val depsJars = DependencyResolver.fetchFiles(mandatoryDeps ++ dependencies, Some(ctx.notifications))

      // println(s"Resolved deps: " + depsJars)
      // classdirs that are last in each module are pushed last in final classpath
      val classesDirsAbs = Seq(classesDir).map(_.absPath)
      (classesDirsAbs ++ ctx.transitiveResults.flatten.flatten ++ depsJars).reverse.distinct.reverse
    }

  val mainClassesTask = TaskBuilder
    .make[Seq[String]](
      name = "mainClasses",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA)
    )
    .dependsOn(compileTask)
    .dependsOn(allClassesDirsTask)
    .build { ctx =>
      val (_, classesDir) = ctx.depResults
      MainClassesDiscovery.discover(classesDir)
    }

  val mainClassTask = ConfigValueTask[Option[String]](
    name = "mainClass",
    supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA),
    execute = { ctx =>
      ctx.module match {
        case m: JavaModule  => Option(m.mainClass)
        case m: ScalaModule => Option(m.mainClass)
        case _              => None
      }
    }
  )

  val finalMainClassTask = TaskBuilder
    .make[String](
      name = "finalMainClass",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA)
    )
    .dependsOn(mainClassTask)
    .dependsOn(mainClassesTask)
    .build { ctx =>
      val (mainClass, mainClasses) = ctx.depResults
      mainClass
        .orElse(mainClasses.headOption)
        .getOrElse(
          throw new Exception(s"No main class found for module: ${ctx.module.id}")
        )
    }

  val runTask = TaskBuilder
    .make[Seq[String]](
      name = "run",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA)
    )
    .dependsOn(runClasspathTask)
    .dependsOn(finalMainClassTask)
    .build { ctx =>
      val (runClasspath, mainClass) = ctx.depResults
      val cp = runClasspath.map(_.toString)
      val cmd = Seq("java", "-cp", cp.mkString(File.pathSeparator), mainClass) ++ ctx.args
      // println(s"Running command: " + cmd)
      ctx.notifications.add(ServerNotification.RunSubprocess(cmd))
      cmd
    }

  val testClassesTask = TaskBuilder
    .make[Seq[DiscoveredFrameworkTests]](
      name = "testClasses",
      supportedModuleTypes = Set(ModuleType.SCALA_TEST)
    )
    .dependsOn(compileTask)
    .dependsOn(runClasspathTask)
    .build { ctx =>
      val (classesDir, classpath) = ctx.depResults
      val testClasspath = Seq(classesDir.absPath) ++ classpath
      val urls = testClasspath.map(_.toURI.toURL).toArray
      val classLoader = new URLClassLoader(urls, getClass.getClassLoader)
      val testDiscovery = DederTestDiscovery(
        classLoader = classLoader,
        testClassesDir = classesDir.absPath.toIO,
        logger = DederTestLogger(ctx.notifications, ctx.module.id)
      )
      testDiscovery.discover().map { case (framework, tests) =>
        DiscoveredFrameworkTests(framework.name(), tests.map(_._1))
      }
    }

  val testTask = TaskBuilder
    .make[DederTestResults](
      name = "test",
      supportedModuleTypes = Set(ModuleType.SCALA_TEST)
    )
    .dependsOn(compileTask)
    .dependsOn(runClasspathTask)
    // TODO testClassesTask
    .build { ctx =>
      val (classesDir, runClasspath) = ctx.depResults
      val testClasspath = (Seq(classesDir.absPath) ++ runClasspath).reverse.distinct.reverse
      val urls = testClasspath.map(_.toURI.toURL).toArray
      val classLoader = new URLClassLoader(urls, getClass.getClassLoader)
      val testDiscovery = DederTestDiscovery(
        classLoader = classLoader,
        testClassesDir = classesDir.absPath.toIO,
        logger = DederTestLogger(ctx.notifications, ctx.module.id)
      )
      val frameworkTests = testDiscovery.discover()
      val testRunner = DederTestRunner(
        tests = frameworkTests,
        classLoader = classLoader,
        logger = DederTestLogger(ctx.notifications, ctx.module.id)
      )
      testRunner.run()
    }

  // order matters for dependency resolution!!
  val all: Seq[Task[?, ?]] = Seq(
    sourcesTask,
    scalaVersionTask,
    resourcesTask,
    javacOptionsTask,
    javacAnnotationProcessorsTask,
    scalacOptionsTask,
    scalacPluginsTask,
    depsTask,
    dependenciesTask,
    allDependenciesTask,
    classesDirTask,
    allClassesDirsTask,
    compileClasspathTask,
    compileTask,
    runClasspathTask,
    mainClassTask,
    mainClassesTask,
    finalMainClassTask,
    runTask,
    testClassesTask,
    testTask
  )

  private val allNames = all.map(_.name)
  private val distinctNames = allNames.distinct
  private val diff = allNames.diff(distinctNames)
  require(diff.isEmpty, s"Duplicate task names: ${diff.mkString(", ")}")
}
