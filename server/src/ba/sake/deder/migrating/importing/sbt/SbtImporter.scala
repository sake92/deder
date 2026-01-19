package ba.sake.deder.migrating.importing.sbt

import scala.jdk.CollectionConverters.*
import ba.sake.tupson.parseJson
import ba.sake.deder.ServerNotification
import ba.sake.deder.ServerNotificationsLogger
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.{ModuleType, ScalaModule}
import org.pkl.core.{EvaluatorBuilder, ModuleSource, ValueConverter, ValueRenderer, ValueRenderers}

class SbtImporter(
    serverNotificationsLogger: ServerNotificationsLogger
) {

  def doImport() = {
    dumpSbtBuild()
    val dederProject = parseSbtBuild()
    val dederBuild = generateDederBuild(dederProject)
    os.write.over(os.pwd / "deder.pkl", dederBuild)
  }

  // writes in target/build-export/ , a json file for each "module"
  private def dumpSbtBuild() = {
    val sbtCmd = if (scala.util.Properties.isWin) "sbt.bat" else "sbt"
    val exportBuildStructurePluginVersion = "0.0.1"
    val exportBuildStructurePluginSource =
      s"""addSbtPlugin("ba.sake" % "sbt-build-extract" % "$exportBuildStructurePluginVersion")
         |libraryDependencies += "ba.sake" %% "sbt-build-extract-core" % "$exportBuildStructurePluginVersion"
         |""".stripMargin
    val exportBuildStructurePluginPath = os.pwd / "project/exportBuildStructure.sbt"
    os.write.over(exportBuildStructurePluginPath, exportBuildStructurePluginSource)
    val res = os.spawn((sbtCmd, "--client", "exportBuildStructure"), mergeErrIntoOut = true)
    var line = ""
    while {
      line = res.stdout.readLine()
      line != null
    } do {
      serverNotificationsLogger.add(ServerNotification.logInfo(line))
    }
    res.waitFor()
    os.remove(exportBuildStructurePluginPath)
  }

  private def parseSbtBuild(): DederProject = {
    val exportedSbtModuleFiles = os.list(os.pwd / "target/build-export").filter(_.ext == "json")
    val exportedSbtModules = exportedSbtModuleFiles.map { mf =>
      os.read(mf).parseJson[ProjectExport]
    }
    val ignoredDeps = Set(
      "org.scala-lang" -> "scala3-library",
      "org.scala-lang" -> "scala-library"
    )
    val modules = exportedSbtModules.map { sbtProjectExport =>
      val root = os.Path(sbtProjectExport.base).subRelativeTo(os.pwd)
      val sources = if sbtProjectExport.sourceDirs.isEmpty then Seq("src/main/scala") else sbtProjectExport.sourceDirs
      val tpe = ModuleType.SCALA
      val deps = sbtProjectExport.externalDependencies
        .filterNot { ed =>
          ignoredDeps.contains(ed.organization -> ed.name)
        }
        .map { ed =>
          if ed.crossVersion == "full" then s"${ed.organization}:::${ed.name}:${ed.revision}"
          else if ed.crossVersion == "binary" then s"${ed.organization}::${ed.name}:${ed.revision}"
          else s"${ed.organization}:${ed.name}:${ed.revision}"
        }
      val module = new ScalaModule(
        sbtProjectExport.id,
        root.toString,
        sources.asJava,
        List.empty.asJava, // moduleDeps, filled in next pass..
        tpe,
        sbtProjectExport.resourceDirs.asJava,
        null, // javaHome
        List.empty.asJava, // jvmOptions
        null, // javaVersion
        sbtProjectExport.javacOptions.asJava,
        null, // mainClass
        deps.asJava,
        List.empty.asJava, // javacAnnotationProcessorDeps
        true, // semanticdbEnabled
        "0.11.1", // javaSemanticdbVersion
        sbtProjectExport.scalaVersion,
        sbtProjectExport.scalacOptions.asJava,
        List.empty.asJava, // scalacPluginDeps
        "4.13.9" // scalaSemanticdbVersion
      )
      module
    }
    new DederProject(modules.asJava)
  }

  // there is no nice way to serialize DederProject back to Pkl..
  private def generateDederBuild(dederProject: DederProject): String = {
    val moduleIds = dederProject.modules.asScala.map(_.id)
    val moduleDefs = dederProject.modules.asScala
      .filterNot(_.root.isBlank) // skip root aggregating project.. TODO better heuristic
      .map { case m: ScalaModule =>
        val deps = m.deps.asScala.map(d => s""" "$d" """.trim)
        val depsOpt = Option
          .when(deps.nonEmpty) {
            s"""deps {
           |${deps.map(d => s"  ${d}").mkString("\n")}
           |}
           |""".stripMargin.indent(2)
          }
          .getOrElse("")
        val sources = m.sources.asScala.map(s => s""" "$s" """.trim)
        val sourcesOpt = Option
          .when(sources.nonEmpty) {
            s"""sources {
           |${sources.map(d => s"  ${d}").mkString("\n")}
           |}
           |""".stripMargin.indent(2)
          }
          .getOrElse("")
        s"""|local const ${m.id} = new ScalaModule {
          |  id = "${m.id}"
          |  root = "${m.root}"
          |${sourcesOpt}
          |  scalaVersion = "${m.scalaVersion}"
          |${depsOpt}
          |}
          |""".stripMargin

      }
    s"""amends "https://sake92.github.io/deder/config/DederProject.pkl"
       |
       |${moduleDefs.mkString("\n")}
       |modules {
       |${moduleIds.map(id => s"  ${id}").mkString("\n")}
       |}
       |""".stripMargin
  }
}
