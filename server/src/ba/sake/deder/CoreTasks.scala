package ba.sake.deder

import java.io.File
import scala.jdk.CollectionConverters.*
import dependency.parser.DependencyParser
import dependency.api.ops.*
import dependency.ScalaParameters
import ba.sake.tupson.JsonRW
import ba.sake.deder.zinc.{DederZincLogger, ZincCompiler}
import ba.sake.deder.config.DederProject.{DederModule, JavaModule, ModuleType, ScalaModule}
import ba.sake.deder.deps.Dependency
import ba.sake.deder.deps.DependencyResolver
import ba.sake.deder.deps.given

class CoreTasks() {

  // TODO figure out how to cache these
  private def zincCompiler(scalaVersion: String) = {
    val dep =
      if scalaVersion.startsWith("3.") then s"org.scala-lang:scala3-sbt-bridge:${scalaVersion}"
      else "org.scala-sbt::compiler-bridge:1.11.0"
    val compilerBridgeJar = DependencyResolver.fetchFile(
      Dependency.make(dep, scalaVersion)
    )
    ZincCompiler(compilerBridgeJar)
  }

  // source dirs
  val sourcesTask = CachedTaskBuilder
    .make[Seq[DederPath]](
      name = "sources",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA)
    )
    .build { ctx =>
      val sources = ctx.module match {
        case m: JavaModule  => m.sources.asScala.toSeq.map(s => DederPath(os.SubPath(s"${m.root}/${s}")))
        case m: ScalaModule => m.sources.asScala.toSeq.map(s => DederPath(os.SubPath(s"${m.root}/${s}")))
        case _              => Seq.empty
      }
      // println(s"Module: ${ctx.module.id} sources: " + sources)
      sources
    }

  val resourcesTask = CachedTaskBuilder
    .make[Seq[DederPath]](
      name = "resources",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA)
    )
    .build { ctx =>
      val resources = ctx.module match {
        case m: JavaModule  => m.resources.asScala.toSeq.map(s => DederPath(os.SubPath(s"${m.root}/${s}")))
        case m: ScalaModule => m.resources.asScala.toSeq.map(s => DederPath(os.SubPath(s"${m.root}/${s}")))
        case _              => Seq.empty
      }
      // println(s"Module: ${ctx.module.id} sources: " + sources)
      resources
    }

  val javacOptionsTask = CachedTaskBuilder
    .make[Seq[String]](
      name = "javacOptions",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA)
    )
    .build { ctx =>
      ctx.module match {
        case m: JavaModule  => m.javacOptions.asScala.toSeq
        case m: ScalaModule => m.javacOptions.asScala.toSeq
        case _              => ???
      }
    }

  val scalacOptionsTask = CachedTaskBuilder
    .make[Seq[String]](
      name = "scalacOptions",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA)
    )
    .build { ctx =>
      ctx.module match {
        case m: ScalaModule =>
          val additional = ScalaParameters(m.scalaVersion).scalaVersion match {
            case s"3.${_}" => Seq.empty // ("-Xtasty-reader")
            case _         => Seq.empty
          }
          additional ++ m.scalacOptions.asScala.toSeq
      }
    }

  val scalaVersionTask = TaskBuilder
    .make[String](
      name = "scalaVersion",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA)
    )
    .build { ctx =>
      ctx.module match {
        case m: JavaModule  => "2.13.17" // dummy default scala version
        case m: ScalaModule => m.scalaVersion
        case _              => ???
      }
    }

  val dependenciesTask = TaskBuilder
    .make[Seq[deps.Dependency]](
      name = "dependencies",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA)
    )
    .dependsOn(scalaVersionTask)
    .build { ctx =>
      val scalaVersion = ctx.depResults._1
      val depDeclarations = ctx.module match {
        case m: JavaModule  => m.deps.asScala.toSeq
        case m: ScalaModule => m.deps.asScala.toSeq
        case _              => Seq.empty
      }
      // println(s"Module: ${ctx.module.id} resolved deps: " + res)
      depDeclarations.map(depDecl => Dependency.make(depDecl, scalaVersion))
    }

  val allDependenciesTask = TaskBuilder
    .make[Seq[os.Path]](
      name = "allDependencies",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA),
      transitive = true
    )
    .dependsOn(scalaVersionTask)
    .build { ctx =>
      val scalaVersion = ctx.depResults._1
      val depDeclarations = ctx.module match {
        case m: JavaModule  => m.deps.asScala.toSeq
        case m: ScalaModule => m.deps.asScala.toSeq
        case _              => Seq.empty
      }
      val deps = depDeclarations.map(depDecl => Dependency.make(depDecl, scalaVersion))
      val depsRes = DependencyResolver.fetchFiles(deps, Some(ctx.notifications))
      (depsRes ++ ctx.transitiveResults.flatten.flatten).reverse.distinct.reverse
    }

  val classesDirTask = TaskBuilder
    .make[os.Path](
      name = "classes",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA)
    )
    .build { ctx =>
      ctx.out
    }

  // this is localRunClasspath in mill ??
  val transitiveClassesDirTask = TaskBuilder
    .make[Seq[os.Path]](
      name = "transitiveClassesDir",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA),
      transitive = true
    )
    .dependsOn(classesDirTask)
    .build { ctx =>
      Seq(ctx.depResults._1) ++ ctx.transitiveResults.flatten.flatten
    }

  val compileClasspathTask = TaskBuilder
    .make[Seq[os.Path]](
      name = "compileClasspath",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA),
      transitive = true
    )
    .dependsOn(scalacOptionsTask)
    .dependsOn(scalaVersionTask)
    .dependsOn(allDependenciesTask)
    .dependsOn(classesDirTask)
    .dependsOn(transitiveClassesDirTask)
    .build { ctx =>
      val scalacOptions = ctx.depResults._1
      val scalaVersion = ctx.depResults._2
      val dependencies = ctx.depResults._3: Seq[os.Path]
      // dirty hack to get class dirs, all except for this module.. :/
      val transitiveClassesDirs = ctx.depResults._5.filterNot(_ == ctx.depResults._4)
      val scalaLibDep = ScalaParameters(scalaVersion).scalaVersion match {
        case s"3.${_}" => s"org.scala-lang::scala3-library:${scalaVersion}"
        case _         => s"org.scala-lang:scala-library:${scalaVersion}"
      }
      println(s"Module: ${ctx.module.id} scalaLibDep: ${scalaLibDep}")
      val scalaLibraryJars = DependencyResolver.fetchFiles(Seq(Dependency.make(scalaLibDep, scalaVersion)))
      val additionalCompileClasspath = ctx.transitiveResults.flatten.flatten ++ dependencies
      val res = (transitiveClassesDirs ++ scalaLibraryJars ++ additionalCompileClasspath).reverse.distinct.reverse
      // there can only be one of each scala library version in classpath
      var foundScala2Lib = false
      var foundScala3Lib = false
      val filteredRes = res.filter { p =>
        if p.last.startsWith("scala-library-") then
          val filter = !foundScala2Lib
          foundScala2Lib = true
          filter
        else if p.last.startsWith("scala3-library_3") then
          val filter = !foundScala3Lib
          foundScala3Lib = true
          filter
        else true
      }
      filteredRes
    }

  val javacAnnotationProcessorsTask = TaskBuilder
    .make[Seq[os.Path]](
      name = "javacAnnotationProcessors",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA)
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

  val scalacPluginsTask = TaskBuilder
    .make[Seq[os.Path]](
      name = "scalacPlugins",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA)
    )
    .dependsOn(scalaVersionTask)
    .build { ctx =>
      val scalaVersion = ctx.depResults._1
      // TODO make configurable on/off
      val semanticDbDeps = ScalaParameters(scalaVersion).scalaVersion match {
        case s"3.${_}" => Seq.empty
        case _         => Seq("org.scalameta:::semanticdb-scalac:4.14.2")
      }
      println(s"Module: ${ctx.module.id} semanticDbDeps: ${semanticDbDeps.mkString(", ")}")
      val pluginJars = DependencyResolver.fetchFiles(
        semanticDbDeps.map(d => Dependency.make(d, scalaVersion)),
        Some(ctx.notifications)
      )
      pluginJars
    }

  val compileTask = TaskBuilder
    .make[DederPath](
      name = "compile",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA),
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
      val sourceDirs = ctx.depResults._1
      val javacOptions = ctx.depResults._2
      val scalacOptions = ctx.depResults._3
      val scalaVersion = ctx.depResults._4
      val compileClasspath = ctx.depResults._5
      val classesDir = ctx.depResults._6
      val scalacPlugins = ctx.depResults._7
      val javacAnnotationProcessors = ctx.depResults._8

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

      val compilerDeps = ScalaParameters(scalaVersion).scalaVersion match {
        case s"3.${_}" =>
          Seq(s"org.scala-lang::scala3-compiler:${scalaVersion}")
        case _ =>
          Seq(
            s"org.scala-lang:scala-compiler:${scalaVersion}",
            s"org.scala-lang:scala-reflect:${scalaVersion}"
          )
      }
      println(s"Module: ${ctx.module.id} compilerJars: ${compilerDeps.mkString(", ")}")
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
      val semanticDbScalacOpts = ScalaParameters(scalaVersion).scalaVersion match {
        case s"3.${_}" =>
          scalacPlugins.map(p => s"-Xplugin:${p.toString}") ++
            Seq("-Xsemanticdb", s"-sourceroot", s"${DederGlobals.projectRootDir}")
        case _ =>
          Seq("-Yrangepos", s"-P:semanticdb:sourceroot:${DederGlobals.projectRootDir}") ++
            scalacPlugins.map(p => s"-Xplugin:${p.toString}")
      }
      val finalScalacOptions = scalacOptions ++ semanticDbScalacOpts
      zincCompiler(scalaVersion).compile(
        scalaVersion,
        compilerJars,
        compileClasspath,
        zincCacheFile,
        sourceFiles,
        classesDir,
        finalScalacOptions,
        finalJavacOptions,
        zincLogger
      )
      DederPath(classesDir)
    }

  val runClasspathTask = TaskBuilder
    .make[Seq[os.Path]](
      name = "runClasspath",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA),
      transitive = true
    )
    .dependsOn(allDependenciesTask)
    .dependsOn(compileTask)
    .build { ctx =>
      val dependencies: Seq[os.Path] = ctx.depResults._1
      val classesDir: DederPath = ctx.depResults._2

      val mandatoryDeps = ctx.module match {
        case m: JavaModule => Seq.empty
        case m: ScalaModule =>
          val scalaVersion = m.scalaVersion
          val scalaLibDep = ScalaParameters(scalaVersion).scalaVersion match {
            case s"3.${_}" => s"org.scala-lang::scala3-library:${scalaVersion}"
            case _         => s"org.scala-lang:scala-library:${scalaVersion}"
          }
          DependencyResolver.fetchFiles(
            Seq(Dependency.make(scalaLibDep, scalaVersion))
          )
        case _ => Seq.empty
      }

      println(s"Module: ${ctx.module.id} mandatoryDeps: ${mandatoryDeps.mkString(", ")}")

      // println(s"Resolved deps: " + allDeps)
      // classdirs that are last in each module are pushed last in final classpath
      val classesDirsAbs = Seq(classesDir).map(_.absPath)
      (classesDirsAbs ++ ctx.transitiveResults.flatten.flatten ++ mandatoryDeps ++ dependencies).reverse.distinct.reverse
    }

  val mainClassesTask = TaskBuilder
    .make[Seq[String]](
      name = "mainClasses",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA)
    )
    .dependsOn(transitiveClassesDirTask)
    .build { ctx =>
      MainClassesDiscovery.discover(ctx.depResults._1)
    }

  val mainClassTask = TaskBuilder
    .make[Option[String]](
      name = "mainClass",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA)
    )
    .build { ctx =>
      ctx.module match {
        case m: JavaModule  => Option(m.mainClass)
        case m: ScalaModule => Option(m.mainClass)
        case _              => None
      }
    }

  val finalMainClassTask = TaskBuilder
    .make[String](
      name = "finalMainClass",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA)
    )
    .dependsOn(mainClassTask)
    .dependsOn(mainClassesTask)
    .build { ctx =>
      val mainClass = ctx.depResults._1.orElse(ctx.depResults._2.headOption)
      mainClass.getOrElse(
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
      val runClasspath = ctx.depResults._1
      val cp = runClasspath.map(_.toString)
      val mainClass = ctx.depResults._2
      val cmd = Seq("java", "-cp", cp.mkString(File.pathSeparator), mainClass)
      // println(s"Running command: " + cmd)
      ctx.notifications.add(ServerNotification.RunSubprocess(cmd))
      cmd
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
    dependenciesTask,
    allDependenciesTask,
    classesDirTask,
    transitiveClassesDirTask,
    compileClasspathTask,
    compileTask,
    runClasspathTask,
    mainClassTask,
    mainClassesTask,
    finalMainClassTask,
    runTask
  )

  private val allNames = all.map(_.name)
  private val distinctNames = allNames.distinct
  private val diff = allNames.diff(distinctNames)
  require(diff.isEmpty, s"Duplicate task names: ${diff.mkString(", ")}")
}
