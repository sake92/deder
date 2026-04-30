package ba.sake.deder.importing.sbt

import scala.jdk.CollectionConverters.*
import ba.sake.tupson.parseJson
import ba.sake.deder.ServerNotification
import ba.sake.deder.ServerNotificationsLogger
import ba.sake.deder.config.DederProject
import ba.sake.deder.importing.ImportingUtils

class SbtImporter(
    serverNotificationsLogger: ServerNotificationsLogger
) {

  private val IgnoredDeps = Set(
    "org.scala-lang" -> "scala3-library",
    "org.scala-lang" -> "scala-library"
  )

  private var _builderDefs: Seq[String] = Seq.empty
  private var _builderNames: Seq[String] = Seq.empty

  def doImport() = {
    dumpSbtBuild()
    val dederBuild = parseAndGenerateBuild()
    os.write.over(os.pwd / "deder.pkl", dederBuild)
  }

  // writes in target/build-export/ , a json file for each "module"
  private def dumpSbtBuild() = {
    val sbtCmd = if (scala.util.Properties.isWin) "sbt.bat" else "sbt"
    val exportBuildStructurePluginVersion = "0.0.3"
    val exportBuildStructurePluginSource =
      s"""addSbtPlugin("ba.sake" % "sbt-build-extract" % "$exportBuildStructurePluginVersion")
         |libraryDependencies += "ba.sake" %% "sbt-build-extract-core" % "$exportBuildStructurePluginVersion"
         |""".stripMargin
    val exportBuildStructurePluginPath = os.pwd / "project/exportBuildStructure.sbt"
    os.write.over(exportBuildStructurePluginPath, exportBuildStructurePluginSource)
    val res = os.spawn((sbtCmd, "exportAllBuildStructures"), mergeErrIntoOut = true)
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

  private def parseAndGenerateBuild(): String = {
    val exportedSbtModuleFiles = os.list(os.pwd / "target/build-export").filter(_.ext == "json")
    val allModules = exportedSbtModuleFiles
      .map(mf => os.read(mf).parseJson[ProjectExport])
    // skip root aggregating project
    val exportedSbtModules = if (allModules.length > 1) allModules.filterNot(_.base == os.pwd.toString) else allModules

    // Group modules by their cross-project root.
    // For sbt-crossproject, jvm/js/native modules share the same parent directory.
    val grouped = exportedSbtModules.groupBy { pe =>
      val absPath = os.Path(pe.base)
      val last = absPath.last
      if (Set("jvm", "js", "native").contains(last)) absPath / os.up
      else absPath
    }

    serverNotificationsLogger.add(ServerNotification.logInfo(s"Discovered ${exportedSbtModules.length} modules in ${grouped.size} groups"))

    var counter = 0
    val builderDefs = grouped.flatMap { case (rootPath, modules) =>
      counter += 1
      generateModuleGroup(rootPath, modules.toSeq, counter)
    }.toSeq

    _builderDefs = builderDefs
    _builderNames = (1 to counter).map(i => s"mod$i").toSeq

    s"""amends "https://sake92.github.io/deder/config/early-access/DederProject.pkl"
       |
       |${_builderDefs.mkString("\n")}
       |
       |modules {
       |${_builderNames.map(n => s"  ...${n}.all").mkString("\n")}
       |}
       |""".stripMargin
  }

  private def generateModuleGroup(
      rootPath: os.Path,
      modules: Seq[ProjectExport],
      index: Int
  ): Option[String] = {
    val root = rootPath.relativeTo(os.pwd).toString match {
      case "" => "."
      case r => r
    }

    // Find the main module (not test, not JS, not Native)
    val mainModule = modules.find(pe =>
      !pe.id.endsWith("Test") && !pe.id.contains("JS") && !pe.id.contains("Native")
    ).getOrElse(modules.head)

    val plugins = mainModule.plugins
    val layout = SbtImporter.detectLayout(plugins, rootPath.toString)
    val layoutStr = layout.toString.toLowerCase.replace("_", "-")

    val isCross = layout == DederProject.DirLayout.SBT_CROSS_FULL ||
      layout == DederProject.DirLayout.SBT_CROSS_PURE ||
      layout == DederProject.DirLayout.SBT_CROSS_DUMMY

    val hasScalaJs = plugins.exists(p => p.contains("ScalaJSPlugin") || p.contains("scalajs"))
    val hasScalaNative = plugins.exists(p => p.contains("ScalaNativePlugin") || p.contains("scalanative"))

    val deps = mainModule.externalDependencies
      .filterNot(d => IgnoredDeps.contains(d.organization -> d.name))
      .filterNot(d => d.configurations.exists(_.contains("plugin")))
      .map(SbtImporter.formatDependency)
      .distinct

    val pluginDeps = mainModule.externalDependencies
      .filter(SbtImporter.isPluginDependency)
      .map(SbtImporter.formatDependency)
      .distinct

    val depsStr = if (deps.nonEmpty)
      s"""deps {\n${deps.map(d => s"""      "$d"""").mkString("\n")}\n    }"""
    else ""

    val pluginDepsStr = if (pluginDeps.nonEmpty)
      s"""scalacPluginDeps {\n${pluginDeps.map(d => s"""      "$d"""").mkString("\n")}\n    }"""
    else ""

    val idOverride = if (root == ".") "\n  id = \"project\"" else ""

    val body =
      s"""  template = new ScalaModule {
         |    scalaVersion = "${mainModule.scalaVersion}"
         |${if (depsStr.nonEmpty) s"    $depsStr\n" else ""}${if (pluginDepsStr.nonEmpty) s"    $pluginDepsStr\n" else ""}  }""".stripMargin

    val defStr = if (isCross) {
      s"""new CreateCrossModules {
         |  root = "$root"$idOverride
         |  layout = "$layoutStr"
         |$body
         |  jsTemplate = (template.asJs()) { scalaJsVersion = "1.18.2" }
         |  nativeTemplate = (template.asNative()) { scalaNativeVersion = "0.5.10" }
         |  testTemplate = (template.asTest()) {
         |    deps { "org.scalameta::munit:1.2.1" }
         |  }
         |}.get""".stripMargin
    } else if (hasScalaJs) {
      s"""new CreateScalaJsModules {
         |  root = "$root"$idOverride
         |  layout = "$layoutStr"
         |$body
         |  testTemplate = (template.asTest()) {
         |    deps { "org.scalameta::munit:1.2.1" }
         |  }
         |}.get""".stripMargin
    } else if (hasScalaNative) {
      s"""new CreateScalaNativeModules {
         |  root = "$root"$idOverride
         |  layout = "$layoutStr"
         |$body
         |  testTemplate = (template.asTest()) {
         |    deps { "org.scalameta::munit:1.2.1" }
         |  }
         |}.get""".stripMargin
    } else {
      s"""new CreateScalaModules {
         |  root = "$root"$idOverride
         |  layout = "$layoutStr"
         |$body
         |  testTemplate = (template.asTest()) {
         |    deps { "org.scalameta::munit:1.2.1" }
         |  }
         |}.get""".stripMargin
    }

    Some(s"local const mod$index = $defStr")
  }
}

object SbtImporter {

  /** Checks if a dependency is a compiler plugin based on its configurations field */
  def isPluginDependency(dep: DependencyExport): Boolean = {
    dep.configurations.exists(_.contains("plugin"))
  }

  /** Formats a DependencyExport into a Maven coordinate string */
  def formatDependency(dep: DependencyExport): String = {
    if (dep.crossVersion == "full") s"${dep.organization}:::${dep.name}:${dep.revision}"
    else if (dep.crossVersion == "binary") s"${dep.organization}::${dep.name}:${dep.revision}"
    else s"${dep.organization}:${dep.name}:${dep.revision}"
  }

  /** Detects which Deder DirLayout to use based on sbt plugins and directory structure. */
  def detectLayout(plugins: Seq[String], projectBaseDir: String): DederProject.DirLayout = {
    val hasCrossProject = plugins.exists(p => p.contains("CrossPlugin") || p.contains("crossproject"))
    
    if (hasCrossProject) {
      val basePath = os.Path(projectBaseDir)
      val hasSharedDir = os.exists(basePath / "shared")
      val hasDotDirs = os.exists(basePath / ".jvm") || os.exists(basePath / ".js") || os.exists(basePath / ".native")
      val hasTopLevelPlatformDirs = os.exists(basePath / "jvm") && os.exists(basePath / "js")
      
      if (hasSharedDir) DederProject.DirLayout.SBT_CROSS_FULL
      else if (hasDotDirs) DederProject.DirLayout.SBT_CROSS_PURE
      else if (hasTopLevelPlatformDirs) DederProject.DirLayout.SBT_CROSS_DUMMY
      else DederProject.DirLayout.SBT
    } else {
      DederProject.DirLayout.SBT
    }
  }
}
