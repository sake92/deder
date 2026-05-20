package ba.sake.deder.importing.sbt

import ba.sake.deder.ServerNotification
import ba.sake.deder.ServerNotificationsLogger
import ba.sake.deder.config.DederProject
import ba.sake.deder.importing._

class SbtProjectAnalyzer(
    serverNotificationsLogger: ServerNotificationsLogger
) {

  import SbtProjectAnalyzer._

  def analyze(
      exportedSbtModules: IndexedSeq[ExportedProjectExportFile]
  ): DederBuild = {
    var filteredDepCount = 0

    serverNotificationsLogger.add(ServerNotification.logInfo(s"Discovered ${exportedSbtModules.length} modules"))

    // ---- PASS 1: group modules by cross-project root ----

    val grouped = exportedSbtModules
      .groupBy { pe =>
        val absPath = os.Path(pe.base)
        val last = absPath.last
        if (Set("jvm", "js", "native", ".jvm", ".js", ".native").contains(last)) absPath / os.up
        else absPath
      }
      .map { case (rootPath, modules) => RawGroup(rootPath, modules) }
      .toSeq

    // Build group infos and global id mapping
    val groupInfos = grouped.map(buildGroupInfo)
    val globalIdMap: Map[(String, String), ResolvedModuleRef] = groupInfos.flatMap(_.sbtIdToRef).toMap
    val refsByProject: Map[String, Seq[(String, ResolvedModuleRef)]] =
      groupInfos.flatMap(gi => gi.concreteExports.map(ce =>
        ce.sbtProjectId -> (ce.scalaVersion -> ResolvedModuleRef(
          targetGroup = gi.builderVarName,
          targetPlatform = ce.platform,
          targetScalaVersion = ce.scalaVersion
        ))
      )).groupBy(_._1).view.mapValues(_.map(_._2)).toMap

    serverNotificationsLogger.add(
      ServerNotification.logInfo(
        s"Resolved ${groupInfos.size} module groups, ${globalIdMap.size} id mappings"
      )
    )

    // ---- Topological sort ----
    val sortedGroups = topoSort(groupInfos, globalIdMap, refsByProject)
    serverNotificationsLogger.add(
      ServerNotification.logInfo(
        s"Sorted order: ${sortedGroups.map(_.builderVarName).mkString(", ")}"
      )
    )

    val moduleGroups = sortedGroups.map { gi =>
      val concreteModules = gi.concreteExports.map { concreteExport =>
        val (moduleDef, filtered) = buildModuleDef(
          concreteExport.exportedModule,
          concreteExport.scalaVersion,
          globalIdMap,
          refsByProject,
          layout = gi.layout,
          isJs = concreteExport.platform == "js",
          isNative = concreteExport.platform == "native"
        )
        filteredDepCount += filtered
        ConcreteModule(
          sbtProjectId = concreteExport.sbtProjectId,
          scalaVersion = concreteExport.scalaVersion,
          platform = concreteExport.platform,
          module = moduleDef
        )
      }

      ModuleGroup(
        builderVarName = gi.builderVarName,
        root = gi.root,
        layout = gi.layout,
        crossScalaVersions = gi.crossScalaVersions,
        concreteModules = concreteModules,
        usesTpolecat = gi.usesTpolecat,
        usesTypelevel = gi.usesTypelevel
      )
    }

    _cachedSummary = buildSummary(moduleGroups, filteredDepCount)

    DederBuild(
      moduleGroups = moduleGroups,
      repositories = exportedSbtModules.flatMap(_.repositories).distinct.map(RepositoryDef.apply)
    )
  }

  def summary(): ImportSummary = _cachedSummary

  // ---- private state ----

  private var _cachedSummary: ImportSummary = ImportSummary(0, 0, 0, 0, 0, Seq.empty)

  // ---- internal types ----

  private case class RawGroup(rootPath: os.Path, modules: Seq[ExportedProjectExportFile])

  private case class ConcreteExport(
      sbtProjectId: String,
      scalaVersion: String,
      platform: String,
      exportedModule: ExportedProjectExportFile
  )

  private case class ResolvedModuleRef(
      targetGroup: String,
      targetPlatform: String,
      targetScalaVersion: String
  )

  private case class GroupInfo(
      builderVarName: String,
      root: String,
      layout: DederProject.DirLayout,
      crossScalaVersions: Seq[String],
      sbtIdToRef: Map[(String, String), ResolvedModuleRef],
      concreteExports: Seq[ConcreteExport],
      usesTpolecat: Boolean = false,
      usesTypelevel: Boolean = false
  )

  private def buildGroupInfo(rg: RawGroup): GroupInfo = {
    val root = rg.rootPath.relativeTo(os.pwd).toString match {
      case "" => "."
      case r  => r
    }

    val mainModule = rg.modules
      .find(pe => !pe.id.endsWith("Test") && !pe.id.contains("JS") && !pe.id.contains("Native"))
      .getOrElse(rg.modules.head)

    val allPlugins = rg.modules.flatMap(_.plugins).distinct
    val usesTpolecat = allPlugins.exists(p => p.contains("org.typelevel.sbt.tpolecat"))
    val usesTypelevel = allPlugins.exists(p => p.contains("org.typelevel.sbt.Typelevel"))
    val layout = SbtProjectAnalyzer.detectLayout(allPlugins, rg.rootPath.toString)
    val isCross = layout == DederProject.DirLayout.SBT_CROSS_FULL ||
      layout == DederProject.DirLayout.SBT_CROSS_PURE ||
      layout == DederProject.DirLayout.SBT_CROSS_DUMMY

    val concreteExports = rg.modules.map { pe =>
      val platform = if isCross then platformRefName(pe) else "main"
      ConcreteExport(
        sbtProjectId = pe.id,
        scalaVersion = pe.exportedScalaVersion,
        platform = platform,
        exportedModule = pe
      )
    }

    val allScalaVersions = concreteExports.map(_.scalaVersion).distinct
    val crossScalaVersions = if allScalaVersions.size > 1 then allScalaVersions else Seq.empty

    val rawName = ImportingUtils.sanitizeId(mainModule.name)
    val baseName = if (isCross) rawName.replaceFirst("(?i)jvm$", "") else rawName
    val builderVarName = baseName.replaceAll("[.-]", "_").replaceAll("[^a-zA-Z0-9_]", "")

    val sbtIdToRef = concreteExports
      .groupBy(concreteExport => (concreteExport.sbtProjectId, concreteExport.scalaVersion))
      .collect { case (key, Seq(concreteExport)) =>
        key -> ResolvedModuleRef(
          targetGroup = builderVarName,
          targetPlatform = concreteExport.platform,
          targetScalaVersion = concreteExport.scalaVersion
        )
      }

    GroupInfo(
      builderVarName,
      root,
      layout,
      crossScalaVersions,
      sbtIdToRef,
      concreteExports,
      usesTpolecat,
      usesTypelevel
    )
  }

  // ---- ModuleDef construction ----

  private def buildModuleDef(
      pe: ExportedProjectExportFile,
      dependerScalaVersion: String,
      idMap: Map[(String, String), ResolvedModuleRef],
      refsByProject: Map[String, Seq[(String, ResolvedModuleRef)]],
      layout: DederProject.DirLayout,
      isJs: Boolean = false,
      isNative: Boolean = false
  ): (ModuleDef, Int) = {
    val (compileDeps, pluginDeps, testDeps, filteredCount) = partitionDeps(pe)
    val moduleDeps = pe.interProjectDependencies
      .filter(ipde => ipde.configuration == "default" || ipde.configuration.contains("compile"))
      .flatMap(ipde => resolveInterProjectRef(ipde.project, dependerScalaVersion, idMap, refsByProject))
      .map(ref => refToModuleDepRef(ref, isTest = false))
      .distinct
    val testModuleDeps = pe.interProjectDependencies
      .filter(ipde => ipde.configuration.contains("test"))
      .flatMap(ipde => resolveInterProjectRef(ipde.project, dependerScalaVersion, idMap, refsByProject))
      .map(ref => refToModuleDepRef(ref, isTest = true))
      .distinct

    // Publish info
    val publish = if (pe.organization.nonEmpty || pe.version.nonEmpty) {
      Some(
        PublishInfo(
          organization = pe.organization,
          artifactName = pe.artifactName,
          version = pe.version,
          description = if (pe.description.nonEmpty) Some(pe.description) else None,
          homepage = pe.homepage,
          developers = pe.developers.map(d => DeveloperDef(d.id, d.name, d.email)),
          licenses = pe.licenses.map(l => LicenseDef(l.name, l.url)),
          scmInfo = pe.scmInfo.map(s => ScmDef(s.browseUrl, s.connection, s.devConnection))
        )
      )
    } else None

    val moduleBasePath = os.Path(pe.base)

    val filteredSources = SbtProjectAnalyzer.filterManagedDirs(pe.sourceDirs)
    val filteredTestSources = SbtProjectAnalyzer.filterManagedDirs(pe.testSourceDirs)
    val filteredResources = SbtProjectAnalyzer.filterManagedDirs(pe.resourceDirs)
    val filteredTestResources = SbtProjectAnalyzer.filterManagedDirs(pe.testResourceDirs)

    val relSourceDirs = SbtProjectAnalyzer.relativizeTo(moduleBasePath, filteredSources)
    val relTestSourceDirs = SbtProjectAnalyzer.relativizeTo(moduleBasePath, filteredTestSources)
    val relResourceDirs = SbtProjectAnalyzer.relativizeTo(moduleBasePath, filteredResources)
    val relTestResourceDirs = SbtProjectAnalyzer.relativizeTo(moduleBasePath, filteredTestResources)

    val finalSourceDirs = SbtProjectAnalyzer.filterStandardSbtDirs(relSourceDirs, layout)
    val finalTestSourceDirs = SbtProjectAnalyzer.filterStandardSbtDirs(relTestSourceDirs, layout)
    val finalResourceDirs = SbtProjectAnalyzer.filterStandardSbtDirs(relResourceDirs, layout)
    val finalTestResourceDirs = SbtProjectAnalyzer.filterStandardSbtDirs(relTestResourceDirs, layout)

    val moduleDef = ModuleDef(
      scalaVersion = pe.scalaVersion,
      scalacOptions = pe.scalacOptions,
      javacOptions = pe.javacOptions,
      deps = compileDeps,
      scalacPluginDeps = pluginDeps,
      testDeps = testDeps,
      moduleDeps = moduleDeps,
      testModuleDeps = testModuleDeps,
      scalaJsVersion = if (isJs) Some(SbtProjectAnalyzer.DefaultScalaJsVersion) else None,
      scalaNativeVersion = if (isNative) Some(SbtProjectAnalyzer.DefaultScalaNativeVersion) else None,
      publish = publish,
      sources = finalSourceDirs,
      testSources = finalTestSourceDirs,
      resources = finalResourceDirs,
      testResources = finalTestResourceDirs
    )
    (moduleDef, filteredCount)
  }

  private def partitionDeps(pe: ExportedProjectExportFile): (Seq[DepDef], Seq[DepDef], Seq[DepDef], Int) = {
    val ignoredCount = pe.externalDependencies.count(d => isIgnoredDep(d.organization, d.name))
    val compileDeps = pe.externalDependencies
      .filterNot(d => isIgnoredDep(d.organization, d.name))
      .filterNot(d => d.configurations.exists(c => c.contains("plugin") || c.contains("test") || c == "provided"))
      .map(toDepDef)
      .distinct
    val pluginDeps = pe.externalDependencies
      .filter(SbtProjectAnalyzer.isPluginDependency)
      .filterNot(d => isIgnoredDep(d.organization, d.name))
      .map(toDepDef)
      .distinct
    val testDeps = pe.externalDependencies
      .filterNot(d => isIgnoredDep(d.organization, d.name))
      .filter(d => d.configurations.exists(_.contains("test")))
      .filterNot(d => d.configurations.exists(_.contains("plugin")))
      .map(toDepDef)
      .distinct
    (compileDeps, pluginDeps, testDeps, ignoredCount)
  }

  private def toDepDef(d: DependencyExport): DepDef = DepDef(
    formatted = SbtProjectAnalyzer.formatDependency(d),
    organization = d.organization,
    name = d.name
  )

  private def refToModuleDepRef(ref: ResolvedModuleRef, isTest: Boolean): ModuleDepRef =
    ModuleDepRef(
      targetGroup = ref.targetGroup,
      targetPlatform = ref.targetPlatform,
      targetScalaVersion = Some(ref.targetScalaVersion),
      isTest = isTest
    )

  // ---- Summary ----

  private def buildSummary(
      groups: Seq[ModuleGroup],
      filteredDepCount: Int
  ): ImportSummary = {
    val concreteModules = groups.flatMap(_.concreteModules)
    val allDeps = concreteModules.flatMap(cm => cm.module.deps ++ cm.module.scalacPluginDeps ++ cm.module.testDeps)
    ImportSummary(
      modulesImported = concreteModules.size * 2,
      moduleGroups = groups.size,
      dependenciesMapped = allDeps.size,
      depsFiltered = filteredDepCount,
      depsSkipped = 0,
      errors = Seq.empty
    )
  }

  // ---- Topological sort ----

  private def topoSort(
      groups: Seq[GroupInfo],
      idMap: Map[(String, String), ResolvedModuleRef],
      refsByProject: Map[String, Seq[(String, ResolvedModuleRef)]]
  ): Seq[GroupInfo] = {
    val builderNames = groups.map(_.builderVarName).toSet
    val nameToGroup = groups.map(g => g.builderVarName -> g).toMap

    val depsOf: Map[String, Set[String]] = groups.map { gi =>
      val deps = gi.concreteExports
        .flatMap { concreteExport =>
          concreteExport.exportedModule.interProjectDependencies
            .filter(ipde => ipde.configuration == "default" || ipde.configuration.contains("compile"))
            .flatMap(ipde =>
              resolveInterProjectRef(ipde.project, concreteExport.scalaVersion, idMap, refsByProject)
            )
            .map(_.targetGroup)
        }
        .filter(builderNames.contains)
      gi.builderVarName -> deps.toSet
    }.toMap

    val dependents: Map[String, Set[String]] = {
      val m = scala.collection.mutable.Map.empty[String, Set[String]].withDefaultValue(Set.empty)
      depsOf.foreach { case (dependent, deps) =>
        deps.foreach(d => m(d) = m(d) + dependent)
      }
      m.toMap
    }

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

  private def platformRefName(pe: ExportedProjectExportFile): String = {
    val rawSegment = os.Path(pe.base).last
    if rawSegment.startsWith(".") then rawSegment.tail else rawSegment
  }

  private def resolveInterProjectRef(
      projectId: String,
      dependerScalaVersion: String,
      idMap: Map[(String, String), ResolvedModuleRef],
      refsByProject: Map[String, Seq[(String, ResolvedModuleRef)]]
  ): Option[ResolvedModuleRef] =
    idMap.get((projectId, dependerScalaVersion)).orElse {
      refsByProject.get(projectId).flatMap { refs =>
        Option.when(refs.size == 1)(refs.head._2)
      }
    }
}

object SbtProjectAnalyzer {

  val DefaultScalaJsVersion = "1.18.2"
  val DefaultScalaNativeVersion = "0.5.10"

  private val IgnoredDeps = Set(
    ("org.scala-lang", "scala3-library"),
    ("org.scala-lang", "scala-library"),
    ("org.scala-js", "scalajs-library"),
    ("org.scala-js", "scalajs-scalalib"),
    ("org.scala-js", "scalajs-test-bridge"),
    ("org.scala-js", "scalajs-test-interface"),
    ("org.scala-js", "scalajs-compiler"),
    ("org.scala-native", "scala3lib"),
    ("org.scala-native", "scalalib"),
    ("org.scala-native", "javalib"),
    ("org.scala-native", "nativelib"),
    ("org.scala-native", "auxlib"),
    ("org.scala-native", "clib"),
    ("org.scala-native", "posixlib"),
    ("org.scala-native", "test-interface"),
    ("org.scala-native", "windowslib"),
    ("org.scala-native", "nscplugin")
  )

  def isIgnoredDep(org: String, name: String): Boolean =
    IgnoredDeps.exists { case (ignoredOrg, ignoredName) =>
      org == ignoredOrg && name.startsWith(ignoredName)
    }

  def lastIsPlatform(s: String, platform: String): Boolean =
    s == platform || s == s".$platform"

  def isPluginDependency(dep: DependencyExport): Boolean = {
    dep.configurations.exists(_.contains("plugin"))
  }

  def formatDependency(dep: DependencyExport): String = {
    val scalaColon = dep.crossVersion match {
      case "full"   => ":::"
      case "binary" => "::"
      case _        => ":"
    }
    val platformColon = dep.platformOpt match {
      case Some(_) => "::"
      case None    => ":"
    }
    s"${dep.organization}$scalaColon${dep.name}$platformColon${dep.revision}"
  }

  def filterManagedDirs(dirs: Seq[String]): Seq[String] =
    dirs.filterNot { d =>
      val segments = d.split("/").toSet
      segments.contains("src_managed") ||
      segments.contains("resource_managed") ||
      d.contains("/target/")
    }

  def relativizeTo(base: os.Path, paths: Seq[String]): Seq[String] =
    paths.flatMap { p =>
      try {
        val rel = os.Path(p).relativeTo(base).toString
        if rel.startsWith("..") then None else Some(rel)
      } catch case _: IllegalArgumentException => None
    }

  private val SbtStandardDirPattern =
    """^(shared/)?((jvm|js|native|\.jvm|\.js|\.native)/)?src/(main|test)/(scala|java|resources)(-[0-9].*)?$""".r

  def filterStandardSbtDirs(dirs: Seq[String], layout: DederProject.DirLayout): Seq[String] =
    if (!layout.toString.toLowerCase.startsWith("sbt")) dirs
    else dirs.filterNot(d => SbtStandardDirPattern.findFirstIn(d).isDefined)

  def detectLayout(plugins: Seq[String], projectBaseDir: String): DederProject.DirLayout = {
    val hasCrossProject = plugins.exists(p =>
      p.contains("sbt-crossproject") || p.contains("ScalaJSCrossPlugin") || p.contains("ScalaNativeCrossPlugin")
    )
    val basePath = os.Path(projectBaseDir)
    val hasSharedDir = os.exists(basePath / "shared")
    val hasDotDirs = os.exists(basePath / ".jvm") || os.exists(basePath / ".js") || os.exists(basePath / ".native")
    val hasTopLevelPlatformDirs = os.exists(basePath / "jvm") && os.exists(basePath / "js")

    if (hasCrossProject) {
      if (hasSharedDir) DederProject.DirLayout.SBT_CROSS_FULL
      else if (hasDotDirs) DederProject.DirLayout.SBT_CROSS_PURE
      else if (hasTopLevelPlatformDirs) DederProject.DirLayout.SBT_CROSS_DUMMY
      else DederProject.DirLayout.SBT
    } else if (hasDotDirs) {
      DederProject.DirLayout.SBT_CROSS_PURE
    } else {
      DederProject.DirLayout.SBT
    }
  }
}
