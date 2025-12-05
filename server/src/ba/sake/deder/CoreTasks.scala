package ba.sake.deder

import java.io.File
import scala.jdk.CollectionConverters.*
import dependency.parser.DependencyParser
import dependency.api.ops.*
import dependency.ScalaParameters
import ba.sake.tupson.JsonRW
import ba.sake.deder.zinc.{DederZincLogger, ZincCompiler}
import ba.sake.deder.config.DederProject.{DederModule, JavaModule, ModuleType, ScalaModule}
import ba.sake.deder.deps.DependencyResolver
import ba.sake.deder.deps.given

class CoreTasks(zincCompiler: ZincCompiler) {

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
        case _              => ???
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
        case _              => ???
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
        case m: ScalaModule => m.scalacOptions.asScala.toSeq
        case _              => Seq.empty
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
    .make[coursierapi.FetchResult](
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

      val res = DependencyResolver.fetch(
        depDeclarations
          .map(depDecl => DependencyParser.parse(depDecl).toOption.get.applyParams(ScalaParameters(scalaVersion)))
          .map(_.toCs),
        Some(ctx.notifications)
      )

      println(s"Module: ${ctx.module.id} resolved deps: " + res)
      res
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
      val coursierDeps = depDeclarations
        .map(depDecl => DependencyParser.parse(depDecl).toOption.get.applyParams(ScalaParameters(scalaVersion)))
        .map(_.toCs)
      val depsRes = DependencyResolver
        .fetch(coursierDeps, Some(ctx.notifications))
        .getFiles
        .asScala
        .map(f => os.Path(f.toPath()))
        .toSeq
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
      val scalaLibraryJar = DependencyResolver.fetchOne(
        DependencyParser
          .parse(s"org.scala-lang:scala-library:${scalaVersion}")
          .toOption
          .get
          .applyParams(ScalaParameters(scalaVersion))
          .toCs
      ) // TODO scala3-library
      val additionalCompileClasspath = ctx.transitiveResults.flatten.flatten ++ dependencies
      (transitiveClassesDirs ++ Seq(scalaLibraryJar) ++ additionalCompileClasspath).reverse.distinct.reverse
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
    .build { ctx =>
      val sourceDirs = ctx.depResults._1
      val javacOptions = ctx.depResults._2
      val scalacOptions = ctx.depResults._3
      val scalaVersion = ctx.depResults._4
      val compileClasspath = ctx.depResults._5
      val classesDir = ctx.depResults._6
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

      val compilerJars = DependencyResolver
        .fetch(
          Seq(
            s"org.scala-lang:scala-compiler:${scalaVersion}",
            s"org.scala-lang:scala-reflect:${scalaVersion}" // TODO only for scala 2
          ).map(d =>
            DependencyParser
              .parse(d)
              .toOption
              .get
              .applyParams(ScalaParameters(scalaVersion))
              .toCs
          )
        )
        .getFiles
        .asScala
        .map(f => os.Path(f.toPath()))
        .toSeq

      /*println(s"Compiling module: ${ctx.module.id} with ${(
        scalaVersion,
        compilerJars,
        compileClasspath,
        classesDir
      )}")*/

      val zincCacheFile = ctx.out / "inc_compile.zip"
      val zincLogger = new DederZincLogger(ctx.notifications, ctx.module.id)
      zincCompiler.compile(
        scalaVersion,
        compilerJars,
        compileClasspath,
        zincCacheFile,
        sourceFiles,
        classesDir,
        scalacOptions,
        javacOptions,
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
          DependencyResolver
            .fetch(
              Seq(
                DependencyParser
                  .parse(s"org.scala-lang:scala-library:${m.scalaVersion}")
                  .toOption
                  .get
                  .applyParams(ScalaParameters(m.scalaVersion))
                  .toCs
              )
            )
            .getFiles
            .asScala
            .map(f => os.Path(f.toPath()))
            .toSeq
        case _ => Seq.empty
      }

      // println(s"Resolved deps: " + allDeps)
      // classdirs that are last in each module are pushed last in final classpath
      (Seq(classesDir).map(
        _.absPath
      ) ++ ctx.transitiveResults.flatten.flatten ++ mandatoryDeps ++ dependencies).reverse.distinct.reverse
    }

  val mainClassTask = TaskBuilder
    .make[String](
      name = "mainClass",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA)
    )
    .build { ctx =>
      ctx.module match {
        case m: JavaModule  => m.mainClass
        case m: ScalaModule => m.mainClass
        case _              => ???
      }
    }

  val runTask = TaskBuilder
    .make[String](
      name = "run",
      supportedModuleTypes = Set(ModuleType.SCALA, ModuleType.JAVA)
    )
    .dependsOn(runClasspathTask)
    .build { ctx =>
      val runClasspath = ctx.depResults._1
      val cp = runClasspath.map(_.toString)
      val mainClass = ctx.module match {
        case m: JavaModule  => m.mainClass
        case m: ScalaModule => m.mainClass
        case _              => ???
      }
      val cmd = Seq("java", "-cp", cp.mkString(File.pathSeparator), mainClass)
      // println(s"Running command: " + cmd)
      ctx.notifications.add(ServerNotification.RunSubprocess(cmd))
      ""
    }

  val all: Seq[Task[?, ?]] = Seq(
    sourcesTask,
    resourcesTask,
    javacOptionsTask,
    scalacOptionsTask,
    scalaVersionTask,
    dependenciesTask,
    allDependenciesTask,
    classesDirTask,
    transitiveClassesDirTask,
    compileClasspathTask,
    compileTask,
    runClasspathTask,
    mainClassTask,
    runTask
  )

  private val allNames = all.map(_.name)
  private val distinctNames = allNames.distinct
  private val diff = allNames.diff(distinctNames)
  require(diff.isEmpty, s"Duplicate task names: ${diff.mkString(", ")}")
}
