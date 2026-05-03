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
    // Base Scala
    "org.scala-lang"     -> "scala3-library",
    "org.scala-lang"     -> "scala-library",
    // ScalaJS (auto-added by Deder)
    "org.scala-js"       -> "scalajs-library",
    "org.scala-js"       -> "scalajs-test-bridge",
    // ScalaNative (auto-added by Deder)
    "org.scala-native"   -> "scala3lib",
    "org.scala-native"   -> "scalalib",
    "org.scala-native"   -> "javalib",
    "org.scala-native"   -> "nativelib",
    "org.scala-native"   -> "auxlib",
    "org.scala-native"   -> "clib",
    "org.scala-native"   -> "posixlib",
  )

  private def isIgnoredDep(org: String, name: String): Boolean =
    IgnoredDeps.exists { case (ignoredOrg, ignoredName) =>
      org == ignoredOrg && name.startsWith(ignoredName)
    }

  def doImport() = {
    dumpSbtBuild()
    val dederBuild = parseAndGenerateBuild()
    os.write.over(os.pwd / "deder.pkl", dederBuild)
  }

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

  /** Info about one module group after first-pass analysis. */
  private case class GroupInfo(
      builderVarName: String,
      root: String,
      layout: DederProject.DirLayout,
      isCross: Boolean,
      hasScalaJs: Boolean,
      hasScalaNative: Boolean,
      hasJsModule: Boolean,
      hasNativeModule: Boolean,
      scalaVersion: String,
      // sbt project id -> Pkl module reference (e.g., "builderVar.jvm")
      sbtIdToRef: Map[String, String],
      // for generating deps/plugins in the template
      mainModule: ProjectExport,
      // all modules in this group (for per-platform dep extraction)
      allModules: Seq[ProjectExport]
  )

  private def parseAndGenerateBuild(): String = {
    val exportedSbtModuleFiles = os.list(os.pwd / "target/build-export").filter(_.ext == "json")
    val allModules = exportedSbtModuleFiles
      .map(mf => os.read(mf).parseJson[ProjectExport])
    // skip root aggregating project
    val exportedSbtModules = if (allModules.length > 1) allModules.filterNot(_.base == os.pwd.toString) else allModules

    serverNotificationsLogger.add(ServerNotification.logInfo(s"Discovered ${exportedSbtModules.length} modules"))

    // ---- PASS 1: group modules and build id mappings ----
    case class RawGroup(
        rootPath: os.Path,
        modules: Seq[ProjectExport],
    )

    // Group modules by their cross-project root.
    // For sbt-crossproject, jvm/js/native modules share the same parent.
    val grouped = exportedSbtModules.groupBy { pe =>
      val absPath = os.Path(pe.base)
      val last = absPath.last
      if (Set("jvm", "js", "native").contains(last)) absPath / os.up
      else absPath
    }.map { case (rootPath, modules) => RawGroup(rootPath, modules) }.toSeq

    // Build group infos and an sbt-id -> deder-id mapping
    val groupInfos = grouped.map { rg =>
      val root = rg.rootPath.relativeTo(os.pwd).toString match {
        case "" => "."
        case r => r
      }

      // Find main (non-test, non-JS-specific) module for metadata
      val mainModule = rg.modules.find(pe =>
        !pe.id.endsWith("Test") && !pe.id.contains("JS") && !pe.id.contains("Native")
      ).getOrElse(rg.modules.head)

      val plugins = mainModule.plugins
      val layout = SbtImporter.detectLayout(plugins, rg.rootPath.toString)
      val isCross = layout == DederProject.DirLayout.SBT_CROSS_FULL ||
        layout == DederProject.DirLayout.SBT_CROSS_PURE ||
        layout == DederProject.DirLayout.SBT_CROSS_DUMMY

      val hasScalaJs = plugins.exists(p => p.contains("ScalaJSPlugin") || p.contains("scalajs"))
      val hasScalaNative = plugins.exists(p => p.contains("ScalaNativePlugin") || p.contains("scalanative"))
      val scalaVersion = mainModule.scalaVersion

      // Derive builder variable name from project name (must be valid Pkl identifier)
      val rawName = ImportingUtils.sanitizeId(mainModule.name)
      val builderVarName = rawName.replaceAll("[.-]", "_").replaceAll("[^a-zA-Z0-9_]", "")

      // Build sbt-id -> Pkl reference mapping (e.g., "hepekComponentsJVM" -> "hepek_components.jvm")
      val sbtIdToRef = rg.modules.map { pe =>
        val sbtId = pe.id
        val lastSegment = os.Path(pe.base).last
        val ref = if (isCross) {
          s"$builderVarName.$lastSegment"
        } else {
          s"$builderVarName.main"
        }
        sbtId -> ref
      }.toMap

      // Also add default (non-js/native) sbt id mapping
      val hasJsModule = rg.modules.exists(pe => os.Path(pe.base).last == "js")
      val hasNativeModule = rg.modules.exists(pe => os.Path(pe.base).last == "native")

      GroupInfo(
        builderVarName,
        root,
        layout,
        isCross,
        hasScalaJs,
        hasScalaNative,
        hasJsModule,
        hasNativeModule,
        scalaVersion,
        sbtIdToRef,
        mainModule,
        rg.modules
      )
    }

    // Build global sbt-id -> Pkl ref map for moduleDep resolution
    val globalIdMap: Map[String, String] = groupInfos.flatMap(_.sbtIdToRef).toMap

    serverNotificationsLogger.add(ServerNotification.logInfo(s"Resolved ${groupInfos.size} module groups, ${globalIdMap.size} id mappings"))

    // ---- Topological sort: modules with no deps come first ----
    val sortedGroupInfos = topoSort(groupInfos, globalIdMap)
    serverNotificationsLogger.add(ServerNotification.logInfo(s"Sorted order: ${sortedGroupInfos.map(_.builderVarName).mkString(", ")}"))

    // ---- PASS 2: generate Pkl builder definitions ----
    val builderDefs = sortedGroupInfos.map { gi =>
      generateModuleGroup(gi, globalIdMap)
    }

    s"""amends "https://sake92.github.io/deder/config/early-access/DederProject.pkl"
       |
       |${builderDefs.mkString("\n")}
       |
       |modules {
       |${sortedGroupInfos.flatMap(gi => moduleRefs(gi)).map(r => s"  $r").mkString("\n")}
       |}
       |""".stripMargin
  }

  /** Returns the Pkl module references for a group (for the modules block). */
  private def moduleRefs(gi: GroupInfo): Seq[String] = {
    val name = gi.builderVarName
    if (gi.isCross) {
      Seq(
        Some(s"$name.jvm"),
        Some(s"$name.jvm_test"),
        if (gi.hasJsModule) Some(s"$name.js") else None,
        if (gi.hasJsModule) Some(s"$name.js_test") else None,
        if (gi.hasNativeModule) Some(s"$name.native") else None,
        if (gi.hasNativeModule) Some(s"$name.native_test") else None,
      ).flatten
    } else {
      Seq(s"$name.main", s"$name.test")
    }
  }

  private def generateModuleGroup(
      gi: GroupInfo,
      idMap: Map[String, String]
  ): String = {
    val layoutStr = gi.layout.toString.toLowerCase.replace("_", "-")

    // Helper: extract deps from a ProjectExport
    def depsFor(pe: ProjectExport, isCrossPlatform: Boolean = false): (Seq[String], Seq[String]) = {
      val deps = pe.externalDependencies
        .filterNot(d => isIgnoredDep(d.organization, d.name))
        .filterNot(d => d.configurations.exists(_.contains("plugin")))
        .map(d => SbtImporter.formatDependency(d, isCrossPlatform))
        .distinct
      val plugins = pe.externalDependencies
        .filter(SbtImporter.isPluginDependency)
        .filterNot(d => isIgnoredDep(d.organization, d.name))
        .map(d => SbtImporter.formatDependency(d, isCrossPlatform))
        .distinct
      (deps, plugins)
    }

    def formatDeps(deps: Seq[String]): String =
      if (deps.nonEmpty) s"""deps {\n${deps.map(d => s"""      "$d"""").mkString("\n")}\n    }""" else ""

    def formatPluginDeps(plugins: Seq[String]): String =
      if (plugins.nonEmpty) s"""scalacPluginDeps {\n${plugins.map(d => s"""      "$d"""").mkString("\n")}\n    }""" else ""

    // JVM deps (for template, or for non-cross modules)
    val jvmModule = if (gi.isCross)
      gi.allModules.find(pe => os.Path(pe.base).last == "jvm").getOrElse(gi.mainModule)
    else gi.mainModule
    val (jvmDeps, jvmPlugins) = depsFor(jvmModule)
    val jvmDepsStr = formatDeps(jvmDeps)
    val jvmPluginDepsStr = formatPluginDeps(jvmPlugins)

    // JS deps (for cross-project jsTemplate)
    val jsModule = gi.allModules.find(pe => os.Path(pe.base).last == "js")
    val (jsDeps, jsPlugins) = jsModule.map(pe => depsFor(pe, isCrossPlatform = true)).getOrElse((Seq.empty, Seq.empty))
    val jsDepsStr = formatDeps(jsDeps)
    val jsPluginDepsStr = formatPluginDeps(jsPlugins)

    // Native deps (for cross-project nativeTemplate)
    val nativeModule = gi.allModules.find(pe => os.Path(pe.base).last == "native")
    val (nativeDeps, nativePlugins) = nativeModule.map(pe => depsFor(pe, isCrossPlatform = true)).getOrElse((Seq.empty, Seq.empty))
    val nativeDepsStr = formatDeps(nativeDeps)
    val nativePluginDepsStr = formatPluginDeps(nativePlugins)

    // Resolve inter-project module dependencies
    val interModuleDeps = gi.mainModule.interProjectDependencies
      .filter(_.configuration == "default") // only default config for now
      .flatMap(ipde => idMap.get(ipde.project)) // sbt project id -> deder module id
    val moduleDepsStr = if (interModuleDeps.nonEmpty)
      s"""moduleDeps {\n${interModuleDeps.map(d => s"""      $d""").mkString("\n")}\n    }"""
    else ""

    val idOverride = s"""id = "${gi.builderVarName}""""

    val body =
      s"""  template = new ScalaModule {
         |    scalaVersion = "${gi.mainModule.scalaVersion}"
         |${if (jvmDepsStr.nonEmpty) s"    $jvmDepsStr\n" else ""}${if (jvmPluginDepsStr.nonEmpty) s"    $jvmPluginDepsStr\n" else ""}${if (moduleDepsStr.nonEmpty) s"    $moduleDepsStr\n" else ""}  }""".stripMargin

    val defStr = if (gi.isCross) {
      val hasJs = jsModule.isDefined
      val hasNative = nativeModule.isDefined

      val jsOverride = if (jsDeps.nonEmpty || jsPlugins.nonEmpty)
        s""" {
           |    scalaJsVersion = "1.18.2"
           |${if (jsDepsStr.nonEmpty) s"    $jsDepsStr\n" else ""}${if (jsPluginDepsStr.nonEmpty) s"    $jsPluginDepsStr\n" else ""}  }""".stripMargin
      else """ { scalaJsVersion = "1.18.2" }"""

      val nativeOverride = if (nativeDeps.nonEmpty || nativePlugins.nonEmpty)
        s""" {
           |    scalaNativeVersion = "0.5.10"
           |${if (nativeDepsStr.nonEmpty) s"    $nativeDepsStr\n" else ""}${if (nativePluginDepsStr.nonEmpty) s"    $nativePluginDepsStr\n" else ""}  }""".stripMargin
      else """ { scalaNativeVersion = "0.5.10" }"""

      val jsTmpl = if (hasJs) s"  jsTemplate = (template.asJs())$jsOverride" else ""
      val nativeTmpl = if (hasNative) s"  nativeTemplate = (template.asNative())$nativeOverride" else ""
      val tmpls = Seq(jsTmpl, nativeTmpl).filter(_.nonEmpty).mkString("\n")

      s"""new CreateCrossModules {
         |  root = "${gi.root}"
         |  $idOverride
         |  layout = "$layoutStr"
         |$body
         |${if (tmpls.nonEmpty) tmpls + "\n" else ""}  testTemplate = (template.asTest()) {
         |    deps { "org.scalameta::munit:1.2.1" }
         |  }
         |}
         |.get""".stripMargin
    } else if (gi.hasScalaJs) {
      s"""new CreateScalaJsModules {
         |  root = "${gi.root}"
         |  $idOverride
         |  layout = "$layoutStr"
         |$body
         |  testTemplate = (template.asTest()) {
         |    deps { "org.scalameta::munit:1.2.1" }
         |  }
         |}
         |.get""".stripMargin
    } else if (gi.hasScalaNative) {
      s"""new CreateScalaNativeModules {
         |  root = "${gi.root}"
         |  $idOverride
         |  layout = "$layoutStr"
         |$body
         |  testTemplate = (template.asTest()) {
         |    deps { "org.scalameta::munit:1.2.1" }
         |  }
         |}
         |.get""".stripMargin
    } else {
      s"""new CreateScalaModules {
         |  root = "${gi.root}"
         |  $idOverride
         |  layout = "$layoutStr"
         |$body
         |  testTemplate = (template.asTest()) {
         |    deps { "org.scalameta::munit:1.2.1" }
         |  }
         |}
         |.get""".stripMargin
    }

    s"local const ${gi.builderVarName} = $defStr"
  }

  /** Topological sort: modules with no deps first, then dependents. */
  private def topoSort(groups: Seq[GroupInfo], idMap: Map[String, String]): Seq[GroupInfo] = {
    val builderNames = groups.map(_.builderVarName).toSet
    val nameToGroup = groups.map(g => g.builderVarName -> g).toMap

    // Build dependency graph: builderName -> set of builder names it depends on
    val depsOf: Map[String, Set[String]] = groups.map { gi =>
      val deps = gi.mainModule.interProjectDependencies
        .filter(_.configuration == "default")
        .flatMap(ipde => idMap.get(ipde.project))
        .map(ref => ref.takeWhile(_ != '.')) // "builder.jvm" -> "builder"
        .filter(builderNames.contains)
      gi.builderVarName -> deps.toSet
    }.toMap

    // Build reverse: builderName -> set of builders that depend on it
    val dependents: Map[String, Set[String]] = {
      val m = scala.collection.mutable.Map.empty[String, Set[String]].withDefaultValue(Set.empty)
      depsOf.foreach { case (dependent, deps) =>
        deps.foreach(d => m(d) = m(d) + dependent)
      }
      m.toMap
    }

    // Kahn's algorithm: in-degree = how many deps this node has
    val inDegree = scala.collection.mutable.Map.from(
      groups.map(g => g.builderVarName -> depsOf(g.builderVarName).size)
    )

    val queue = scala.collection.mutable.Queue.from(
      groups.filter(g => inDegree(g.builderVarName) == 0)
    )
    val sorted = scala.collection.mutable.ListBuffer.empty[GroupInfo]

    while (queue.nonEmpty) {
      val g = queue.dequeue()
      sorted += g
      for (dependent <- dependents.getOrElse(g.builderVarName, Set.empty)) {
        inDegree(dependent) -= 1
        if (inDegree(dependent) == 0) {
          queue.enqueue(nameToGroup(dependent))
        }
      }
    }

    sorted.toSeq
  }
}

object SbtImporter {

  /** Checks if a dependency is a compiler plugin based on its configurations field */
  def isPluginDependency(dep: DependencyExport): Boolean = {
    dep.configurations.exists(_.contains("plugin"))
  }

  /** Formats a DependencyExport into a Maven coordinate string.
   *  isCrossPlatform should be true for ScalaJS/Native modules (uses :: between name and version for platform cross-version). */
  def formatDependency(dep: DependencyExport, isCrossPlatform: Boolean = false): String = {
    val scalaColon = dep.crossVersion match {
      case "full" => ":::"
      case "binary" => "::"
      case _ => ":"
    }
    val platformColon = if (isCrossPlatform) "::" else ":"
    s"${dep.organization}${scalaColon}${dep.name}${platformColon}${dep.revision}"
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
