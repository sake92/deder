package ba.sake.deder.importing.sbt

import scala.jdk.CollectionConverters.*
import ba.sake.tupson.parseJson
import ba.sake.deder.ServerNotification
import ba.sake.deder.ServerNotificationsLogger
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.{DederModule, JavaModule, ModuleType, ScalaModule, ScalaTestModule}
import org.pkl.core.{EvaluatorBuilder, ModuleSource, ValueConverter, ValueRenderer, ValueRenderers}

class SbtImporter(
    serverNotificationsLogger: ServerNotificationsLogger
) {

  private val IgnoredDeps = Set(
    "org.scala-lang" -> "scala3-library",
    "org.scala-lang" -> "scala-library"
  )

  def doImport() = {
    dumpSbtBuild()
    val dederProject = parseSbtBuild()
    val dederBuild = generateDederBuild(dederProject)
    os.write.over(os.pwd / "deder.pkl", dederBuild)
  }

  // writes in target/build-export/ , a json file for each "module"
  private def dumpSbtBuild() = {
    val sbtCmd = if (scala.util.Properties.isWin) "sbt.bat" else "sbt"
    val exportBuildStructurePluginVersion = "0.0.2"
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
    val exportedSbtModules = exportedSbtModuleFiles
      .map { mf =>
        os.read(mf).parseJson[ProjectExport]
      }
      .filterNot(_.base == os.pwd.toString) // skip root aggregating project.. TODO better heuristic
    val modules = exportedSbtModules
      .flatMap { sbtProjectExport =>
        Seq(
          getModule(sbtProjectExport, "compile"),
          getModule(sbtProjectExport, "test")
        )
      }
      .filterNot(_._3.sources.isEmpty)
      .filterNot(_._1.externalDependencies.exists (_.organization == "org.scala-js" )) // ScalaJS unsupported for now
    serverNotificationsLogger.add(ServerNotification.logInfo(s"Discovered ${modules.length} modules"))
    val finalModules = modules.map { case (spe, config, m, moduleDeps) =>
      val moduleDependencies = moduleDeps.flatMap { moduleDepId =>
        modules
          .find(_._3.id == moduleDepId)
          .map(_._3)
      }
      m.withModuleDeps(moduleDependencies.asJava)
    }
    new DederProject(finalModules.asJava)
  }

  // (projectExport, config, dederModule, dederModuleDeps)
  private def getModule(
      sbtProjectExport: ProjectExport,
      config: String
  ): (ProjectExport, String, JavaModule, Seq[String]) = {
    val isTest = config == "test"
    val id = if isTest then s"${sbtProjectExport.id}Test" else sbtProjectExport.id
    var rootAbsPath = os.Path(sbtProjectExport.base)
    val isCrossProject = Set("jvm", "js", "native").contains(rootAbsPath.last)
    if isCrossProject then rootAbsPath = rootAbsPath / os.up // dirty hack to have proper subpaths
    val root = rootAbsPath.relativeTo(os.pwd)
    val sourceDirs = if isTest then sbtProjectExport.testSourceDirs else sbtProjectExport.sourceDirs
    val sources = sourceDirs
      .flatMap { d =>
        val sourceDirPath = os.Path(d)
        Option.when(os.exists(sourceDirPath))(sourceDirPath.relativeTo(rootAbsPath))
      }
      .map(_.toString)
    val tpe = if isTest then ModuleType.SCALA_TEST else ModuleType.SCALA // TODO
    val deps = sbtProjectExport.externalDependencies
      .filter { d =>
        if isTest then d.configurations.contains(config) else true
      }
      .filterNot { ed =>
        IgnoredDeps.contains(ed.organization -> ed.name)
      }
      .map { ed =>
        if ed.crossVersion == "full" then s"${ed.organization}:::${ed.name}:${ed.revision}"
        else if ed.crossVersion == "binary" then s"${ed.organization}::${ed.name}:${ed.revision}"
        else s"${ed.organization}:${ed.name}:${ed.revision}"
      }
    val module =
      if isTest then
        new ScalaTestModule(
          id,
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
          "4.13.9", // scalaSemanticdbVersion
          List.empty.asJava // testFrameworks
        )
      else
        new ScalaModule(
          id,
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

    val moduleDeps = sbtProjectExport.interProjectDependencies.map { ipde =>
      if ipde.configuration == "test" then s"${ipde.project}Test" else ipde.project
    } ++ Option.when(isTest)(sbtProjectExport.id)
    (sbtProjectExport, config, module, moduleDeps)
  }

  // there is no nice way to serialize DederProject back to Pkl..
  private def generateDederBuild(dederProject: DederProject): String = {
    val moduleIds = dederProject.modules.asScala.map(_.id)
    val moduleDefs = dederProject.modules.asScala
      .map { case m: ScalaModule =>
        val deps = m.deps.asScala.map(d => s""" "$d" """.trim)
        val depsOpt = Option
          .when(deps.nonEmpty) {
            s"""deps {
               |${deps.map(d => s"  ${d}").mkString("\n")}
               |}""".stripMargin.indent(2).stripTrailing
          }
        val sources = m.sources.asScala.map(s => s""" "$s" """.trim)
        val sourcesOpt = Option
          .when(sources.nonEmpty) {
            s"""sources = new Listing {
               |${sources.map(d => s"  ${d}").mkString("\n")}
               |}""".stripMargin.indent(2).stripTrailing
          }
        val moduleDeps = m.moduleDeps.asScala.map(_.id)
        val moduleDepsOpt = Option
          .when(moduleDeps.nonEmpty) {
            s"""moduleDeps {
               |${moduleDeps.map(d => s"  ${d}").mkString("\n")}
               |}""".stripMargin.indent(2).stripTrailing
          }
        val optionals = List(sourcesOpt, moduleDepsOpt, depsOpt).flatten.mkString("\n")
        val moduleType = m match {
          case module: ScalaTestModule => "ScalaTestModule"
          case _                       => "ScalaModule"
        }
        s"""|local const ${m.id} = new ${moduleType} {
            |  id = "${m.id}"
            |  root = "${m.root}"
            |  scalaVersion = "${m.scalaVersion}"
            |${optionals}
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
