package ba.sake.deder.importing.sbt

import scala.jdk.CollectionConverters.*
import ba.sake.tupson.parseJson
import ba.sake.deder.ServerNotification
import ba.sake.deder.ServerNotificationsLogger
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.{DederModule, JavaModule, ModuleType, ScalaModule, ScalaTestModule}
import ba.sake.deder.importing.ImportingUtils
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
    val res = os.spawn((sbtCmd, "exportBuildStructure"), mergeErrIntoOut = true)
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
    var exportedSbtModules = exportedSbtModuleFiles
      .map { mf =>
        os.read(mf).parseJson[ProjectExport]
      }
    // skip root aggregating project.. TODO better heuristic
    if exportedSbtModules.length > 1 then exportedSbtModules = exportedSbtModules.filterNot(_.base == os.pwd.toString)
    val modules = exportedSbtModules
      .flatMap { sbtProjectExport =>
        Seq(
          getModule(sbtProjectExport, "compile"),
          getModule(sbtProjectExport, "test")
        )
      }
      .filterNot(_._3.sources.isEmpty)
      .filterNot(_._1.externalDependencies.exists(_.organization == "org.scala-js")) // ScalaJS unsupported for now
    serverNotificationsLogger.add(ServerNotification.logInfo(s"Discovered ${modules.length} modules"))
    val finalModules = modules.map { case (spe, config, m, moduleDeps) =>
      val moduleDependencies = moduleDeps.flatMap { moduleDepId =>
        modules
          .find(_._3.id == moduleDepId)
          .map(_._3)
      }
      m.withModuleDeps(moduleDependencies.asJava)
    }
    new DederProject(finalModules.asJava, java.util.Collections.emptyList(), true)
  }

  // (projectExport, config, dederModule, dederModuleDeps)
  private def getModule(
      sbtProjectExport: ProjectExport,
      config: String
  ): (ProjectExport, String, JavaModule, Seq[String]) = {
    val isTest = config == "test"
    val originalId = if isTest then s"${sbtProjectExport.id}Test" else sbtProjectExport.id
    val id = ImportingUtils.sanitizeId(originalId)
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
    val resourceDirs = if isTest then sbtProjectExport.testResourceDirs else sbtProjectExport.resourceDirs
    val resources = resourceDirs
      .flatMap { d =>
        val resourceDirPath = os.Path(d)
        Option.when(os.exists(resourceDirPath))(resourceDirPath.relativeTo(rootAbsPath))
      }
      .map(_.toString)
    val tpe = if isTest then ModuleType.SCALA_TEST else ModuleType.SCALA // TODO
    val filteredDeps = sbtProjectExport.externalDependencies
      .filter { d =>
        if isTest then d.configurations.exists(_.contains(config)) else true
      }
      .filterNot { ed =>
        IgnoredDeps.contains(ed.organization -> ed.name)
      }
    val (pluginDeps, regularDeps) = filteredDeps.partition(SbtImporter.isPluginDependency)
    val deps = regularDeps.map(SbtImporter.formatDependency)
    val scalacPluginDeps = pluginDeps.map(SbtImporter.formatDependency)
    val dederModuleRoot = if root.toString.isEmpty then "." else root.toString
    val module =
      if isTest then
        new ScalaTestModule(
          id,
          dederModuleRoot,
          sources.asJava,
          List.empty.asJava, // moduleDeps, filled in next pass..
          tpe,
          resources.asJava,
          null, // javaHome
          List.empty.asJava, // jvmOptions
          null, // javaVersion
          DederProject.CompileOrder.JAVA_THEN_SCALA,
          sbtProjectExport.javacOptions.asJava,
          null, // forkEnv
          null, // mainClass
          deps.asJava,
          List.empty.asJava, // javacAnnotationProcessorDeps
          "0.11.1", // javaSemanticdbVersion
          true, // semanticdbEnabled
          new DederProject.ManifestSettings(java.util.Map.of(), java.util.Map.of()), // manifest
          false, // publish
          null, // pomSettings
          null, // publishTo
          null, // publishLocalTo
          null, // graalvm
          java.util.Map.of(), // mvnApps
          sbtProjectExport.scalaVersion,
          sbtProjectExport.scalacOptions.asJava,
          scalacPluginDeps.asJava, // scalacPluginDeps
          "4.13.9", // scalaSemanticdbVersion
          0L, // testParallelism (0 = all CPUs)
          0L, // maxTestForks (0 = all CPUs)
          null, // junitXmlReport
          List.empty.asJava // testFrameworks
        )
      else
        new ScalaModule(
          id,
          dederModuleRoot,
          sources.asJava,
          List.empty.asJava, // moduleDeps, filled in next pass..
          tpe,
          resources.asJava,
          null, // javaHome
          List.empty.asJava, // jvmOptions
          null, // javaVersion
          DederProject.CompileOrder.JAVA_THEN_SCALA,
          sbtProjectExport.javacOptions.asJava,
          null, // forkEnv
          null, // mainClass
          deps.asJava,
          List.empty.asJava, // javacAnnotationProcessorDeps
          "0.11.1", // javaSemanticdbVersion
          true, // semanticdbEnabled
          new DederProject.ManifestSettings(java.util.Map.of(), java.util.Map.of()), // manifest
          false, // publish
          null, // pomSettings
          null, // publishTo
          null, // publishLocalTo
          null, // graalvm
          java.util.Map.of(), // mvnApps
          sbtProjectExport.scalaVersion,
          sbtProjectExport.scalacOptions.asJava,
          scalacPluginDeps.asJava, // scalacPluginDeps
          "4.13.9" // scalaSemanticdbVersion
        )

    val originalModuleDeps = sbtProjectExport.interProjectDependencies.map { ipde =>
      if ipde.configuration == "test" then s"${ipde.project}Test" else ipde.project
    } ++ Option.when(isTest)(sbtProjectExport.id)
    val moduleDeps = originalModuleDeps.map(ImportingUtils.sanitizeId)
    (sbtProjectExport, config, module, moduleDeps)
  }

  // there is no nice way to serialize DederProject back to Pkl..
  private def generateDederBuild(dederProject: DederProject): String = {
    val allModules = dederProject.modules.asScala
    val mainModules = allModules.collect { case m: ScalaModule if !m.isInstanceOf[ScalaTestModule] => m }
    val testModules = allModules.collect { case m: ScalaTestModule => m }

    // Map: main module ID → test module
    val mainToTest: Map[String, ScalaTestModule] = testModules.flatMap { tm =>
      tm.moduleDeps.asScala
        .collectFirst { case dep if mainModules.exists(_.id == dep.id) => dep.id }
        .map(mainId => mainId -> tm)
    }.toMap

    // Build old-ID → Pkl variable reference mapping for moduleDeps substitution
    val idToRef: Map[String, String] =
      mainModules.map(m => m.id -> s"${m.id}.main").toMap ++
        testModules.flatMap { tm =>
          tm.moduleDeps.asScala
            .collectFirst { case dep if mainModules.exists(_.id == dep.id) => dep.id }
            .map(mainId => tm.id -> s"${mainId}.test")
        }.toMap

    def renderStringListing(name: String, items: Seq[String]): Option[String] =
      Option.when(items.nonEmpty) {
        val lines = items.map(d => s"""  "$d"""").mkString("\n")
        s"$name {\n$lines\n}"
      }

    def renderRefListing(name: String, refs: Seq[String]): Option[String] =
      Option.when(refs.nonEmpty) {
        val lines = refs.map(d => s"  $d").mkString("\n")
        s"$name {\n$lines\n}"
      }

    def renderModuleBody(
        m: ScalaModule,
        excludeDeps: Set[String] = Set.empty,
        excludeModuleDeps: Set[String] = Set.empty,
        excludeScalacOpts: Set[String] = Set.empty,
        excludePluginDeps: Set[String] = Set.empty
    ): Seq[String] = {
      val deps = m.deps.asScala.distinct.filterNot(excludeDeps.contains)
      val moduleDepsRefs = m.moduleDeps.asScala
        .map(_.id)
        .filterNot(excludeModuleDeps.contains)
        .map(id => idToRef.getOrElse(id, id))
      val javacOpts = m.javacOptions.asScala.toSeq
      val scalacOpts = m.scalacOptions.asScala.distinct.filterNot(excludeScalacOpts.contains)
      val pluginDeps = m.scalacPluginDeps.asScala.distinct.filterNot(excludePluginDeps.contains)
      List(
        renderStringListing("deps", deps.toSeq),
        renderRefListing("moduleDeps", moduleDepsRefs.toSeq),
        Option.when(javacOpts.nonEmpty)(s"javacOptions {\n${javacOpts.map(d => s"  $d").mkString("\n")}\n}"),
        renderStringListing("scalacOptions", scalacOpts.toSeq),
        renderStringListing("scalacPluginDeps", pluginDeps.toSeq)
      ).flatten
    }

    val moduleDefs = mainModules.map { mainMod =>
      val testModOpt = mainToTest.get(mainMod.id)

      val templateLines = s"""scalaVersion = "${mainMod.scalaVersion}"""" +:
        renderModuleBody(mainMod)
      val templateBody = templateLines.mkString("\n").indent(4).stripTrailing

      val testTemplatePart = testModOpt.map { testMod =>
        val mainAndDepIds = mainMod.moduleDeps.asScala.map(_.id).toSet + mainMod.id
        val testLines = renderModuleBody(
          testMod,
          excludeDeps = mainMod.deps.asScala.toSet,
          excludeModuleDeps = mainAndDepIds,
          excludeScalacOpts = mainMod.scalacOptions.asScala.toSet,
          excludePluginDeps = mainMod.scalacPluginDeps.asScala.toSet
        )
        if testLines.isEmpty then ""
        else {
          val body = testLines.mkString("\n").indent(4).stripTrailing
          s"""
             |  testTemplate = (template.asTest()) {
             |$body
             |  }""".stripMargin
        }
      }.getOrElse("")

      s"""local const ${mainMod.id} = new CreateScalaModules {
         |  root = "${mainMod.root}"
         |  layout = "maven"
         |  template = new {
         |$templateBody
         |  }$testTemplatePart
         |}.get
         |""".stripMargin
    }

    s"""amends "https://sake92.github.io/deder/config/v0.7.2/DederProject.pkl"
       |
       |${moduleDefs.mkString("\n")}
       |modules {
       |${mainModules.map(m => s"  ...${m.id}.all").mkString("\n")}
       |}
       |""".stripMargin
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
}
