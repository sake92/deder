package ba.sake.deder.importing

import ba.sake.deder.config.DederProject

object DederPklRenderer {
  val DederVersion = "v0.11.1"

  private enum ScalaVersionCtx:
    case Placeholder
    case Literal(value: String)

  private case class VersionSlice(
      scalaVersion: String,
      modulesByPlatform: Map[String, ModuleDef]
  )

  private val platformOrder = Seq("main", "jvm", "js", "native")

  def render(build: DederBuild): String = {
    val header = s"""amends "https://sake92.github.io/deder/config/${DederVersion}/DederProject.pkl""""
    val repos = renderRepositories(build.repositories)
    val groupLookup = build.moduleGroups.map(g => g.builderVarName -> g).toMap

    val needsTpolecatImport = build.moduleGroups.exists(_.usesTpolecat)
    val needsTypelevelImport = build.moduleGroups.exists(_.usesTypelevel)

    val helperImport: Option[String] = {
      val imports = Seq(
        Option.when(needsTypelevelImport)(
          s"""import "https://sake92.github.io/deder/config/${DederVersion}/DederTypelevel.pkl""""
        ),
        Option.when(needsTpolecatImport)(
          s"""import "https://sake92.github.io/deder/config/${DederVersion}/DederTpolecat.pkl""""
        )
      ).flatten
      Option.when(imports.nonEmpty)(imports.mkString("\n"))
    }

    val crossGroups = build.moduleGroups.filter(g => g.crossScalaVersions.nonEmpty)
    val sharedVersionListName = Option.when(
      crossGroups.map(_.crossScalaVersions).distinct.size == 1 && crossGroups.size > 1
    )("projectScalaVersions")

    val sharedVersionsDecl = sharedVersionListName.flatMap { _ =>
      crossGroups.headOption.map { g =>
        val vs = versionsFor(g).map(v => s""""$v"""").mkString("List(", ", ", ")")
        s"local const projectScalaVersions = $vs"
      }
    }

    val allPublishes: Seq[PublishInfo] = build.moduleGroups.flatMap { g =>
      // Use all concrete modules, not just head — some modules may have publish in later concretes
      g.concreteModules.flatMap(_.module.publish).headOption
    }

    val hasSharedPomBase: Boolean = allPublishes.size >= 2 && {
      val base = allPublishes.head
      allPublishes.tail.forall { p =>
        p.organization == base.organization &&
        p.developers == base.developers &&
        p.licenses == base.licenses
      }
    }

    val sharedPomBaseStr = Option.when(hasSharedPomBase)(renderPublishInfoBase(allPublishes.head))

    val builders = build.moduleGroups
      .map { g =>
        val isCross = g.crossScalaVersions.nonEmpty
        renderGroup(
          g,
          groupLookup,
          if (isCross) sharedVersionListName else None,
          hasSharedPomBase
        )
      }
      .mkString("\n\n")
    val modulesBlock = renderModulesBlock(build.moduleGroups)

    List(
      Some(header),
      helperImport,
      sharedVersionsDecl,
      sharedPomBaseStr,
      if (repos.nonEmpty) Some(repos) else None,
      Some(builders),
      Some(modulesBlock)
    ).flatten.mkString("\n\n")
  }

  /** Maps a Scala version string to the template key used in convention file exports. "3.7.4" -> "3", "2.13.18" ->
    * "213", "2.12.20" -> "212"
    */
  private def templateVersionKey(scalaVersion: String): String = {
    val parts = scalaVersion.split("\\.")
    if (parts(0) == "3") "3"
    else parts.take(2).mkString("") // "2", "13" -> "213"
  }

  /** Returns the full namespace prefix for template amends. */
  private def templatePrefix(g: ModuleGroup): String =
    if (g.usesTypelevel) "DederTypelevel.typelevel"
    else "DederTpolecat.tpolecat"

  /** Returns the template amend expression for a single-version module. Example: "(DederTpolecat.tpolecatScala213)"
    */
  private def templateAmendExpr(g: ModuleGroup, scalaVersion: String): String =
    s"(${templatePrefix(g)}Scala${templateVersionKey(scalaVersion)})"

  /** Returns the Pkl module name for convention imports. */
  private def templateModuleName(g: ModuleGroup): String =
    if (g.usesTypelevel) "DederTypelevel" else "DederTpolecat"

  /** Returns a template amend expression for cross-version .map() using forVersion helper. Example:
    * "(DederTpolecat.forVersion(sv))"
    */
  private def crossVersionTemplateAmendExpr(g: ModuleGroup): String =
    s"(${templateModuleName(g)}.forVersion(sv))"

  /** Returns a platform template amend expression for a given slot. Example:
    * "(DederTypelevel.testForVersion(sv))" or "(DederTypelevel.typelevelScala3Test)"
    */
  private def platformAmendExpr(
      g: ModuleGroup,
      slot: String,
      scalaVersionCtx: ScalaVersionCtx,
      platformVersion: Option[String] = None
  ): String = {
    if (g.usesTypelevel || g.usesTpolecat) {
      scalaVersionCtx match {
        case ScalaVersionCtx.Placeholder =>
          val helperName = slot match {
            case "Test"        => "testForVersion"
            case "Js"          => "jsForVersion"
            case "JsTest"      => "jsTestForVersion"
            case "Native"      => "nativeForVersion"
            case "NativeTest"  => "nativeTestForVersion"
          }
          val args = platformVersion match {
            case Some(v) => s"""sv, "$v""""
            case None => "sv"
          }
          s"${templateModuleName(g)}.$helperName($args)"
        case ScalaVersionCtx.Literal(v) =>
          s"${templatePrefix(g)}Scala${templateVersionKey(v)}$slot"
      }
    } else {
      slot match {
        case "Test"        => "template.asTest()"
        case "Js"          => "template.asJs()"
        case "JsTest"      => "jsTemplate.asTest()"
        case "Native"      => "template.asNative()"
        case "NativeTest"  => "nativeTemplate.asTest()"
      }
    }
  }

  // ---- top-level blocks ----

  private def renderRepositories(repos: Seq[RepositoryDef]): String =
    if (repos.isEmpty) ""
    else {
      val entries = repos.map(r => s"""  new MavenRepository { url = "${r.url}" }""").mkString("\n")
      s"""repositories {\n$entries\n}"""
    }

  private def renderModulesBlock(groups: Seq[ModuleGroup]): String = {
    val refs = groups.flatMap(moduleRefs)
    s"""modules {\n${refs.map(r => s"  $r").mkString("\n")}\n}"""
  }

  private def moduleRefs(g: ModuleGroup): Seq[String] = {
    val name = g.builderVarName
    if (g.crossScalaVersions.nonEmpty) Seq(s"...${name}Modules")
    else if (g.hasJsModule || g.hasNativeModule) {
      if (g.hasJsModule && g.hasNativeModule) Seq(s"...$name.all")
      else
        Seq(
          Some(s"$name.jvm"),
          Some(s"$name.jvm_test"),
          if (g.hasJsModule) Some(s"$name.js") else None,
          if (g.hasJsModule) Some(s"$name.js_test") else None,
          if (g.hasNativeModule) Some(s"$name.native") else None,
          if (g.hasNativeModule) Some(s"$name.native_test") else None
        ).flatten
    } else Seq(s"...$name.all")
  }

  private def versionsFor(g: ModuleGroup): Seq[String] = {
    val declared =
      if (g.crossScalaVersions.nonEmpty) g.crossScalaVersions else g.concreteModules.map(_.scalaVersion).distinct
    val extra = g.concreteModules.map(_.scalaVersion).distinct.filterNot(declared.contains)
    declared ++ extra
  }

  private def versionSlices(g: ModuleGroup): Seq[VersionSlice] = {
    val byVersion = g.concreteModules.groupBy(_.scalaVersion)
    versionsFor(g).map { version =>
      val modulesByPlatform = byVersion
        .getOrElse(version, Seq.empty)
        .sortBy(cm => platformOrder.indexOf(cm.platform))
        .map(cm => cm.platform -> cm.module)
        .toMap
      VersionSlice(version, modulesByPlatform)
    }
  }

  /** Computes properties common to ALL versions in a cross-version group. Returns a ModuleDef where each Seq property
    * is the intersection across all version slices. moduleDeps are normalized: two refs differing only in
    * targetScalaVersion are treated as identical.
    */
  private def computeCommonProps(slices: Seq[VersionSlice]): ModuleDef = {
    val allModuleDefs = slices.flatMap { slice =>
      slice.modulesByPlatform.get("jvm").orElse(slice.modulesByPlatform.get("main"))
    }
    if (allModuleDefs.isEmpty)
      return ModuleDef(
        "",
        Seq.empty,
        Seq.empty,
        Seq.empty,
        Seq.empty,
        Seq.empty,
        Seq.empty,
        Seq.empty,
        None,
        None,
        None,
        Seq.empty,
        Seq.empty,
        Seq.empty,
        Seq.empty
      )

    def intersect[T](seqs: Seq[Seq[T]]): Seq[T] =
      if (seqs.isEmpty) Seq.empty
      else seqs.tail.foldLeft(seqs.head)((acc, s) => acc.filter(s.contains))

    def intersectDeps(depsSeq: Seq[Seq[DepDef]]): Seq[DepDef] =
      if (depsSeq.isEmpty) Seq.empty
      else {
        val formattedSets = depsSeq.map(_.map(_.formatted).toSet)
        val commonFormatted = formattedSets.tail.foldLeft(formattedSets.head)(_ & _)
        depsSeq.head.filter(d => commonFormatted.contains(d.formatted))
      }

    def intersectModuleDeps(depsSeq: Seq[Seq[ModuleDepRef]]): Seq[ModuleDepRef] =
      if (depsSeq.isEmpty) Seq.empty
      else {
        def normalized(ref: ModuleDepRef): (String, String, Boolean) =
          (ref.targetGroup, ref.targetPlatform, ref.isTest)
        val normalizedSets = depsSeq.map(_.map(normalized).toSet)
        val common = normalizedSets.tail.foldLeft(normalizedSets.head)(_ & _)
        depsSeq.head
          .filter(r => common.contains(normalized(r)))
          .map(r => r.copy(targetScalaVersion = None))
      }

    val base = allModuleDefs.head
    ModuleDef(
      scalaVersion = "",
      scalacOptions = intersect(allModuleDefs.map(_.scalacOptions)),
      javacOptions = intersect(allModuleDefs.map(_.javacOptions)),
      deps = intersectDeps(allModuleDefs.map(_.deps)),
      scalacPluginDeps = intersectDeps(allModuleDefs.map(_.scalacPluginDeps)),
      testDeps = Seq.empty,
      moduleDeps = intersectModuleDeps(allModuleDefs.map(_.moduleDeps)),
      testModuleDeps = Seq.empty,
      scalaJsVersion = None,
      scalaNativeVersion = None,
      publish = if (allModuleDefs.map(_.publish).distinct.size == 1) allModuleDefs.head.publish else None,
      sources = intersect(allModuleDefs.map(_.sources)),
      testSources = Seq.empty,
      resources = intersect(allModuleDefs.map(_.resources)),
      testResources = Seq.empty
    )
  }

  /** For each version, computes the additions over the common set. Returns Map[scalaVersion -> ModuleDef with only
    * added properties]. Properties identical to common are empty in the delta.
    */
  private def computeVersionDeltas(
      slices: Seq[VersionSlice],
      common: ModuleDef
  ): Map[String, ModuleDef] = {
    slices.flatMap { slice =>
      slice.modulesByPlatform.get("jvm").orElse(slice.modulesByPlatform.get("main")).map { m =>
        val v = slice.scalaVersion
        v -> ModuleDef(
          scalaVersion = v,
          scalacOptions = m.scalacOptions.filterNot(common.scalacOptions.contains),
          javacOptions = m.javacOptions.filterNot(common.javacOptions.contains),
          deps = m.deps.filterNot(d => common.deps.exists(_.formatted == d.formatted)),
          scalacPluginDeps =
            m.scalacPluginDeps.filterNot(d => common.scalacPluginDeps.exists(_.formatted == d.formatted)),
          testDeps = Seq.empty,
          moduleDeps = m.moduleDeps.filterNot { ref =>
            common.moduleDeps.exists(c =>
              c.targetGroup == ref.targetGroup &&
                c.targetPlatform == ref.targetPlatform &&
                c.isTest == ref.isTest
            )
          },
          testModuleDeps = Seq.empty,
          scalaJsVersion = common.scalaJsVersion match {
            case Some(cv) if m.scalaJsVersion.contains(cv) => None
            case _                                         => m.scalaJsVersion
          },
          scalaNativeVersion = common.scalaNativeVersion match {
            case Some(cv) if m.scalaNativeVersion.contains(cv) => None
            case _                                             => m.scalaNativeVersion
          },
          publish = if (common.publish == m.publish) None else m.publish,
          sources = m.sources.filterNot(common.sources.contains),
          testSources = Seq.empty,
          resources = m.resources.filterNot(common.resources.contains),
          testResources = Seq.empty
        )
      }
    }.toMap
  }

  private def renderScalacOptionsWithWhens(
      common: Seq[String],
      deltas: Map[String, Seq[String]],
      g: ModuleGroup,
      indent: Int
  ): String = {
    // If using tpolecat/typelevel template, diff against its default options
    val (effectiveCommon, effectiveDeltas) = if (g.usesTpolecat || g.usesTypelevel) {
      val refVersion = g.crossScalaVersions.headOption.getOrElse("2.13")
      val templateSet =
        if (g.usesTpolecat) TemplateOptionsReader.tpolecatScalacOptions(refVersion)
        else TemplateOptionsReader.typelevelScalacOptions(refVersion)
      val filteredCommon = common.filterNot(templateSet.contains)
      val filteredDeltas = deltas.view.mapValues(_.filterNot(templateSet.contains)).filter(_._2.nonEmpty).toMap
      (filteredCommon, filteredDeltas)
    } else (common, deltas)

    val hasCommon = effectiveCommon.nonEmpty
    val hasAnyDelta = effectiveDeltas.values.exists(_.nonEmpty)
    if (!hasCommon && !hasAnyDelta) return ""

    val spaces = " " * indent
    val i1 = " " * (indent + 2)
    val i2 = " " * (indent + 4)
    val commonEntries = effectiveCommon.map(o => s"""$i1"$o"""").mkString("\n")
    val whenEntries = effectiveDeltas.toSeq
      .sortBy(_._1)
      .flatMap { (v, items) =>
        if (items.nonEmpty) {
          val itemLines = items.map(o => s"""$i2"$o"""").mkString("\n")
          Some(s"""${i1}when (sv == "$v") {\n$itemLines\n$i1}""")
        } else None
      }
      .mkString("\n")
    val body = Seq(
      if (commonEntries.nonEmpty) Some(commonEntries) else None,
      if (whenEntries.nonEmpty) Some(whenEntries) else None
    ).flatten.mkString("\n")
    s"""${spaces}scalacOptions {\n$body\n$spaces}"""
  }

  private def renderDepsWithWhens(
      common: Seq[DepDef],
      deltas: Map[String, Seq[DepDef]],
      indent: Int
  ): String = {
    val hasCommon = common.nonEmpty
    val hasAnyDelta = deltas.values.exists(_.nonEmpty)
    if (!hasCommon && !hasAnyDelta) return ""
    val spaces = " " * indent
    val i1 = " " * (indent + 2)
    val i2 = " " * (indent + 4)
    val commonEntries = common.map(d => s"""$i1"${d.formatted}"""").mkString("\n")
    val whenEntries = deltas.toSeq
      .sortBy(_._1)
      .flatMap { (v, deps) =>
        if (deps.nonEmpty) {
          val depLines = deps.map(d => s"""$i2"${d.formatted}"""").mkString("\n")
          Some(s"""${i1}when (sv == "$v") {\n$depLines\n$i1}""")
        } else None
      }
      .mkString("\n")
    val body = Seq(
      if (commonEntries.nonEmpty) Some(commonEntries) else None,
      if (whenEntries.nonEmpty) Some(whenEntries) else None
    ).flatten.mkString("\n")
    s"""${spaces}deps {\n$body\n$spaces}"""
  }

  private def renderStringListWithWhens(
      label: String,
      common: Seq[String],
      deltas: Map[String, Seq[String]],
      indent: Int
  ): String = {
    val hasCommon = common.nonEmpty
    val hasAnyDelta = deltas.values.exists(_.nonEmpty)
    if (!hasCommon && !hasAnyDelta) return ""
    val spaces = " " * indent
    val i1 = " " * (indent + 2)
    val i2 = " " * (indent + 4)
    val commonEntries = common.map(s => s"""$i1"$s"""").mkString("\n")
    val whenEntries = deltas.toSeq
      .sortBy(_._1)
      .flatMap { (v, items) =>
        if (items.nonEmpty) {
          val itemLines = items.map(s => s"""$i2"$s"""").mkString("\n")
          Some(s"""${i1}when (sv == "$v") {\n$itemLines\n$i1}""")
        } else None
      }
      .mkString("\n")
    val body = Seq(
      if (commonEntries.nonEmpty) Some(commonEntries) else None,
      if (whenEntries.nonEmpty) Some(whenEntries) else None
    ).flatten.mkString("\n")
    s"""${spaces}$label {\n$body\n$spaces}"""
  }

  private def renderPluginDepsWithWhens(
      common: Seq[DepDef],
      deltas: Map[String, Seq[DepDef]],
      indent: Int
  ): String = {
    val hasCommon = common.nonEmpty
    val hasAnyDelta = deltas.values.exists(_.nonEmpty)
    if (!hasCommon && !hasAnyDelta) return ""
    val spaces = " " * indent
    val i1 = " " * (indent + 2)
    val i2 = " " * (indent + 4)
    val commonEntries = common.map(d => s"""$i1"${d.formatted}"""").mkString("\n")
    val whenEntries = deltas.toSeq
      .sortBy(_._1)
      .flatMap { (v, deps) =>
        if (deps.nonEmpty) {
          val depLines = deps.map(d => s"""$i2"${d.formatted}"""").mkString("\n")
          Some(s"""${i1}when (sv == "$v") {\n$depLines\n$i1}""")
        } else None
      }
      .mkString("\n")
    val body = Seq(
      if (commonEntries.nonEmpty) Some(commonEntries) else None,
      if (whenEntries.nonEmpty) Some(whenEntries) else None
    ).flatten.mkString("\n")
    s"""${spaces}scalacPluginDeps {\n$body\n$spaces}"""
  }

  private def renderModuleDepsWithWhens(
      common: Seq[ModuleDepRef],
      deltas: Map[String, Seq[ModuleDepRef]],
      groupLookup: Map[String, ModuleGroup],
      indent: Int
  ): String = {
    val hasCommon = common.nonEmpty
    val hasAnyDelta = deltas.values.exists(_.nonEmpty)
    if (!hasCommon && !hasAnyDelta) return ""
    val spaces = " " * indent
    val i1 = " " * (indent + 2)
    val i2 = " " * (indent + 4)
    val commonEntries =
      common.map(r => s"$i1${crossDepFilter(r, groupLookup, ScalaVersionCtx.Placeholder)}").mkString("\n")
    val whenEntries = deltas.toSeq
      .sortBy(_._1)
      .flatMap { (v, refs) =>
        if (refs.nonEmpty) {
          val refLines =
            refs.map(r => s"$i2${crossDepFilter(r, groupLookup, ScalaVersionCtx.Placeholder)}").mkString("\n")
          Some(s"""${i1}when (sv == "$v") {\n$refLines\n$i1}""")
        } else None
      }
      .mkString("\n")
    val body = Seq(
      if (commonEntries.nonEmpty) Some(commonEntries) else None,
      if (whenEntries.nonEmpty) Some(whenEntries) else None
    ).flatten.mkString("\n")
    s"""${spaces}moduleDeps {\n$body\n$spaces}"""
  }

  // ---- group rendering ----

  private def renderGroup(
      g: ModuleGroup,
      groupLookup: Map[String, ModuleGroup],
      sharedVersionListName: Option[String],
      hasSharedPomBase: Boolean = false
  ): String = {
    val slices = versionSlices(g)
    val builderType = builderTypeFor(g)

    if (g.crossScalaVersions.nonEmpty) {
      val common = computeCommonProps(slices)
      val rawDeltas = computeVersionDeltas(slices, common)

      val scalacOptDeltas: Map[String, Seq[String]] = rawDeltas.map { (v, d) => v -> d.scalacOptions }
      val javacOptDeltas: Map[String, Seq[String]] = rawDeltas.map { (v, d) => v -> d.javacOptions }
      val depsDeltas: Map[String, Seq[DepDef]] = rawDeltas.map { (v, d) => v -> d.deps }
      val pluginDiffedSlices = slices.map { slice =>
        val diffedModules = slice.modulesByPlatform.map { (platform, module) =>
          platform -> module.copy(
            scalacPluginDeps = diffTemplatePluginDeps(module.scalacPluginDeps, g, slice.scalaVersion)
          )
        }
        slice.copy(modulesByPlatform = diffedModules)
      }
      val pluginDiffedCommon = computeCommonProps(pluginDiffedSlices)
      val pluginDepsDeltas: Map[String, Seq[DepDef]] =
        computeVersionDeltas(pluginDiffedSlices, pluginDiffedCommon).map { (v, d) => v -> d.scalacPluginDeps }
      val srcDeltas: Map[String, Seq[String]] = rawDeltas.map { (v, d) => v -> d.sources }
      val resDeltas: Map[String, Seq[String]] = rawDeltas.map { (v, d) => v -> d.resources }
      val modDepDeltas: Map[String, Seq[ModuleDepRef]] = rawDeltas.map { (v, d) => v -> d.moduleDeps }

      // Fixup: keep flag+argument pairs together. If a paired flag (e.g.
      // -Ybackend-parallelism) ended up in a delta but its numeric argument
      // ended up in common, move the argument to the delta alongside the flag.
      val pairedFlags = Set("-Ybackend-parallelism", "-release", "-java-output-version")
      val standaloneNums = common.scalacOptions.filter(_.matches("\\d+")).toSet

      val fixedScalacOptDeltas = if (standaloneNums.nonEmpty) {
        scalacOptDeltas.map { (v, items) =>
          // Rebuild: for each paired flag, append its argument if found in common
          v -> items.flatMap { item =>
            if (pairedFlags.contains(item)) {
              // Look for the original argument in this version's slice
              val origItems = slices
                .find(sl => sl.scalaVersion == v)
                .flatMap(sl => sl.modulesByPlatform.get("jvm").orElse(sl.modulesByPlatform.get("main")))
                .map(m => m.scalacOptions)
                .getOrElse(Seq.empty)
              val arg = origItems.indexOf(item) + 1 match {
                case i if i < origItems.length && origItems(i).matches("\\d+") =>
                  Some(origItems(i))
                case _ => None
              }
              if (arg.isDefined) Seq(item, arg.get) else Seq(item)
            } else Seq(item)
          }
        }
      } else scalacOptDeltas

      // Remove arguments from common that were moved to deltas
      val fixedCommonScalacOpts = common.scalacOptions.filterNot { o =>
        o.matches("\\d+") && fixedScalacOptDeltas.values.exists(_.contains(o))
      }

      val templateProps = Seq(
        if (
          g.usesTpolecat || g.usesTypelevel || fixedCommonScalacOpts.nonEmpty || fixedScalacOptDeltas.values
            .exists(_.nonEmpty)
        )
          Some(renderScalacOptionsWithWhens(fixedCommonScalacOpts, fixedScalacOptDeltas, g, indent = 4))
        else None,
        if (
          !suppressJavacOptionsDeltas(
            common.javacOptions,
            javacOptDeltas,
            g
          ) && (common.javacOptions.nonEmpty || javacOptDeltas.values.exists(_.nonEmpty))
        )
          Some(renderStringListWithWhens("javacOptions", common.javacOptions, javacOptDeltas, indent = 4))
        else None,
        if (common.deps.nonEmpty || depsDeltas.values.exists(_.nonEmpty))
          Some(renderDepsWithWhens(common.deps, depsDeltas, indent = 4))
        else None,
        if (pluginDiffedCommon.scalacPluginDeps.nonEmpty || pluginDepsDeltas.values.exists(_.nonEmpty))
          Some(renderPluginDepsWithWhens(pluginDiffedCommon.scalacPluginDeps, pluginDepsDeltas, indent = 4))
        else None,
        if (common.sources.nonEmpty || srcDeltas.values.exists(_.nonEmpty))
          Some(renderStringListWithWhens("sources", common.sources, srcDeltas, indent = 4))
        else None,
        if (common.resources.nonEmpty || resDeltas.values.exists(_.nonEmpty))
          Some(renderStringListWithWhens("resources", common.resources, resDeltas, indent = 4))
        else None,
        if (common.moduleDeps.nonEmpty || modDepDeltas.values.exists(_.nonEmpty))
          Some(renderModuleDepsWithWhens(common.moduleDeps, modDepDeltas, groupLookup, indent = 4))
        else None,
        common.publish.map(p => renderPublishInfo(p, indent = 4, useBase = hasSharedPomBase))
      ).flatten.mkString("\n")

      val propsBlock = if (templateProps.nonEmpty) templateProps + "\n" else ""

      val templateHeader = if (g.usesTpolecat || g.usesTypelevel) {
        s"""  template = ${crossVersionTemplateAmendExpr(g)} {"""
      } else {
        """  template = new ScalaModule {"""
      }
      val templateBody = {
        val body = s"""    scalaVersion = sv
                   |$propsBlock""".stripMargin
        s"""$templateHeader
                   |$body  }""".stripMargin
      }

      val repMods = slices.headOption.getOrElse(VersionSlice("", Map.empty)).modulesByPlatform
      val jvmModule = repMods
        .get("jvm")
        .orElse(repMods.get("main"))
        .getOrElse(
          ModuleDef(
            "",
            Seq.empty,
            Seq.empty,
            Seq.empty,
            Seq.empty,
            Seq.empty,
            Seq.empty,
            Seq.empty,
            None,
            None,
            None,
            Seq.empty,
            Seq.empty,
            Seq.empty,
            Seq.empty
          )
        )
      val isCross = repMods.contains("js") || repMods.contains("native")

      val testTmpl = renderTestTemplate(g, jvmModule, Some(ScalaVersionCtx.Placeholder), groupLookup)

      val crossPlatTmpls = if (isCross) {
        // In cross-version mode, plugin deps are already in the template body
        // with when-clauses. Platform helpers (jsForVersion etc.) inherit them, so
        // DON'T emit them again here — that would apply to ALL versions.
        val jsTmpl = repMods
          .get("js")
          .map { m =>
            val jsVersion = m.scalaJsVersion.getOrElse("1.18.2")
            val amendExpr = platformAmendExpr(
              g,
              "Js",
              ScalaVersionCtx.Placeholder,
              Some(jsVersion)
            )
            val body = renderJsNativeOverride(
              "jsTemplate",
              amendExpr,
              m.copy(scalacPluginDeps = Seq.empty),
              scalaJsVersion = if (g.usesTpolecat || g.usesTypelevel) None else Some(jsVersion),
              scalaNativeVersion = None
            )
            s"  $body"
          }
          .getOrElse("")

        val nativeTmpl = repMods
          .get("native")
          .map { m =>
            val nativeVersion = m.scalaNativeVersion.getOrElse("0.5.10")
            val amendExpr = platformAmendExpr(
              g,
              "Native",
              ScalaVersionCtx.Placeholder,
              Some(nativeVersion)
            )
            val body = renderJsNativeOverride(
              "nativeTemplate",
              amendExpr,
              m.copy(scalacPluginDeps = Seq.empty),
              scalaJsVersion = None,
              scalaNativeVersion = if (g.usesTpolecat || g.usesTypelevel) None else Some(nativeVersion)
            )
            s"  $body"
          }
          .getOrElse("")

        val jsTestTmpl = repMods
          .get("js")
          .map { m =>
            if (m.testDeps.nonEmpty) {
              val jsVersion = m.scalaJsVersion.getOrElse("1.18.2")
              val amendExpr = platformAmendExpr(
                g,
                "JsTest",
                ScalaVersionCtx.Placeholder,
                Some(jsVersion)
              )
              val depsStr = renderDeps(m.testDeps, indent = 6)
              s"  jsTestTemplate = ($amendExpr) {\n$depsStr  }"
            } else ""
          }
          .getOrElse("")

        val nativeTestTmpl = repMods
          .get("native")
          .map { m =>
            if (m.testDeps.nonEmpty) {
              val nativeVersion = m.scalaNativeVersion.getOrElse("0.5.10")
              val amendExpr = platformAmendExpr(
                g,
                "NativeTest",
                ScalaVersionCtx.Placeholder,
                Some(nativeVersion)
              )
              val depsStr = renderDeps(m.testDeps, indent = 6)
              s"  nativeTestTemplate = ($amendExpr) {\n$depsStr  }"
            } else ""
          }
          .getOrElse("")

        Seq(jsTmpl, jsTestTmpl, nativeTmpl, nativeTestTmpl).filter(_.nonEmpty).mkString("\n")
      } else ""

      val body =
        Seq(Some(templateBody), Some(testTmpl), if (crossPlatTmpls.nonEmpty) Some(crossPlatTmpls) else None).flatten
          .mkString("\n")

      val versionsListName = sharedVersionListName.getOrElse(s"${g.builderVarName}ScalaVersions")
      val versionsDecl =
        if (sharedVersionListName.isEmpty)
          s"local const $versionsListName = ${versionsFor(g).map(v => s""""$v"""").mkString("List(", ", ", ")")}\n\n"
        else ""
      val builderIdProps = {
        val idLine = s"""      id = "${crossVersionIdWithPlaceholder(g, builderType)}""""
        val testIdLine = Option.when(builderType == "CreateScalaModules")(
          s"""      testId = "${crossVersionTestIdWithPlaceholder(g)}""""
        )
        Seq(Some(idLine), testIdLine).flatten.mkString("\n")
      }

      s"""${versionsDecl}local const ${g.builderVarName}Modules = $versionsListName
               |  .map((sv) ->
               |    new $builderType {
               |      root = "${g.root}"
               |$builderIdProps
               |      layout = "${g.layout.toString.toLowerCase.replace("_", "-")}"
               |$body
               |    }.get.all
               |  ).flatten()""".stripMargin
    } else {
      val representativeSlice = slices.find(_.modulesByPlatform.nonEmpty).getOrElse {
        val fallbackVersion = versionsFor(g).headOption.getOrElse("")
        VersionSlice(fallbackVersion, Map.empty)
      }
      val scalaVersionCtx = ScalaVersionCtx.Literal(representativeSlice.scalaVersion)
      val body = renderGroupBody(representativeSlice.modulesByPlatform, groupLookup, g, Some(scalaVersionCtx), hasSharedPomBase)
      s"""local const ${g.builderVarName} = new $builderType {
               |  root = "${g.root}"
               |  id = "${g.builderVarName}"
               |  layout = "${g.layout.toString.toLowerCase.replace("_", "-")}"
               |$body
               |}.get""".stripMargin
    }
  }

  private def builderTypeFor(g: ModuleGroup): String = {
    if (g.hasJsModule || g.hasNativeModule) "CreateCrossModules"
    else "CreateScalaModules"
  }

  private def crossVersionIdWithPlaceholder(g: ModuleGroup, builderType: String): String =
    builderType match {
      case "CreateScalaModules" => s"${g.builderVarName}-jvm-\\(sv)"
      case "CreateCrossModules" => g.builderVarName
      case other                => throw new IllegalArgumentException(s"Unexpected builder type: $other")
    }

  private def crossVersionTestIdWithPlaceholder(g: ModuleGroup): String =
    s"${g.builderVarName}-jvm-test-\\(sv)"

  private def renderGroupBody(
      modulesByPlatform: Map[String, ModuleDef],
      groupLookup: Map[String, ModuleGroup],
      g: ModuleGroup,
      scalaVersionCtx: Option[ScalaVersionCtx],
      hasSharedPomBase: Boolean = false
  ): String = {
    val isCross =
      modulesByPlatform.contains("jvm") || modulesByPlatform.contains("js") || modulesByPlatform.contains("native")
    val jvmModule = selectTemplateModule(modulesByPlatform)

    val jvmBody = renderTemplateBody(jvmModule, "ScalaModule", None, scalaVersionCtx, groupLookup, g, hasSharedPomBase)
    val testTmpl = renderTestTemplate(g, jvmModule, scalaVersionCtx, groupLookup)

    if (isCross) {
      val jsTmpl = modulesByPlatform
        .get("js")
        .map { m =>
          val jsVersion = m.scalaJsVersion.getOrElse("1.18.2")
          val resolvedCtx = scalaVersionCtx.getOrElse(ScalaVersionCtx.Literal(m.scalaVersion))
          val amendExpr = platformAmendExpr(
            g,
            "Js",
            resolvedCtx,
            Some(jsVersion)
          )
          val body = renderJsNativeOverride(
            "jsTemplate",
            amendExpr,
            m,
            scalaJsVersion = if (g.usesTpolecat || g.usesTypelevel) None else Some(jsVersion),
            scalaNativeVersion = None
          )
          s"  $body"
        }
        .getOrElse("")

      val nativeTmpl = modulesByPlatform
        .get("native")
        .map { m =>
          val nativeVersion = m.scalaNativeVersion.getOrElse("0.5.10")
          val resolvedCtx = scalaVersionCtx.getOrElse(ScalaVersionCtx.Literal(m.scalaVersion))
          val amendExpr = platformAmendExpr(
            g,
            "Native",
            resolvedCtx,
            Some(nativeVersion)
          )
          val body = renderJsNativeOverride(
            "nativeTemplate",
            amendExpr,
            m,
            scalaJsVersion = None,
            scalaNativeVersion = if (g.usesTpolecat || g.usesTypelevel) None else Some(nativeVersion)
          )
          s"  $body"
        }
        .getOrElse("")

      val jsTestTmpl = modulesByPlatform
        .get("js")
        .map { m =>
          if (m.testDeps.nonEmpty) {
            val jsVersion = m.scalaJsVersion.getOrElse("1.18.2")
            val resolvedCtx = scalaVersionCtx.getOrElse(ScalaVersionCtx.Literal(m.scalaVersion))
            val amendExpr = platformAmendExpr(
              g,
              "JsTest",
              resolvedCtx,
              Some(jsVersion)
            )
            val depsStr = renderDeps(m.testDeps, indent = 6)
            s"  jsTestTemplate = ($amendExpr) {\n$depsStr  }"
          } else ""
        }
        .getOrElse("")

      val nativeTestTmpl = modulesByPlatform
        .get("native")
        .map { m =>
          if (m.testDeps.nonEmpty) {
            val nativeVersion = m.scalaNativeVersion.getOrElse("0.5.10")
            val resolvedCtx = scalaVersionCtx.getOrElse(ScalaVersionCtx.Literal(m.scalaVersion))
            val amendExpr = platformAmendExpr(
              g,
              "NativeTest",
              resolvedCtx,
              Some(nativeVersion)
            )
            val depsStr = renderDeps(m.testDeps, indent = 6)
            s"  nativeTestTemplate = ($amendExpr) {\n$depsStr  }"
          } else ""
        }
        .getOrElse("")

      val tmpls = Seq(jsTmpl, jsTestTmpl, nativeTmpl, nativeTestTmpl).filter(_.nonEmpty).mkString("\n")
      val tmplsWithNewline = if (tmpls.nonEmpty) tmpls + "\n" else ""

      s"""$jvmBody
               |$testTmpl
               |$tmplsWithNewline""".stripMargin
    } else {
      s"""$jvmBody
               |$testTmpl""".stripMargin
    }
  }

  private def selectTemplateModule(modulesByPlatform: Map[String, ModuleDef]): ModuleDef = {
    modulesByPlatform
      .get("jvm")
      .orElse(modulesByPlatform.get("main"))
      .orElse(platformOrder.iterator.map(modulesByPlatform.get).collectFirst { case Some(module) => module })
      .orElse(modulesByPlatform.toSeq.sortBy(_._1).headOption.map(_._2))
      .getOrElse(throw IllegalArgumentException("Cannot render module group body: no modules found in version slice"))
  }

  private def renderTemplateBody(
      m: ModuleDef,
      moduleType: String,
      extraProps: Option[String],
      scalaVersionCtx: Option[ScalaVersionCtx] = None,
      groupLookup: Map[String, ModuleGroup] = Map.empty,
      g: ModuleGroup,
      hasSharedPomBase: Boolean = false
  ): String = {
    val extra = extraProps.map(e => s"    $e\n").getOrElse("")
    val versionLine = scalaVersionCtx match {
      case Some(ScalaVersionCtx.Placeholder)    => Some("    scalaVersion = sv")
      case Some(ScalaVersionCtx.Literal(value)) => Some(s"""    scalaVersion = "$value"""")
      case None if moduleType != "ScalaJsModule" && moduleType != "ScalaNativeModule" =>
        Some(s"""    scalaVersion = "${m.scalaVersion}"""")
      case _ => None
    }
    val props = Seq(
      versionLine,
      Some(extra.trim).filter(_.nonEmpty),
      Some(renderScalacOptionsSmart(m, g, indent = 4, scalaVersionCtx)).filter(_.nonEmpty),
      if (suppressJavacOptions(m.javacOptions, g)) None
      else Some(renderJavacOptions(m.javacOptions, indent = 4)).filter(_.nonEmpty),
      Some(renderSourceDirs(m.sources, indent = 4)).filter(_.nonEmpty),
      Some(renderResourceDirs(m.resources, indent = 4)).filter(_.nonEmpty),
      Some(renderDeps(m.deps, indent = 4)).filter(_.nonEmpty),
      Some(renderPluginDeps(diffTemplatePluginDeps(m.scalacPluginDeps, g, m.scalaVersion), indent = 4)).filter(_.nonEmpty),
      Some(renderModuleDepsPkl(m.moduleDeps, indent = 4, scalaVersionCtx, groupLookup)).filter(_.nonEmpty),
      m.publish.map(p => renderPublishInfo(p, indent = 4, useBase = hasSharedPomBase)).filter(_.nonEmpty)
    ).flatten.mkString("\n")
    val header = if (g.usesTpolecat || g.usesTypelevel) {
      val sv = scalaVersionCtx match {
        case Some(ScalaVersionCtx.Literal(v)) => v
        case _                                => m.scalaVersion
      }
      s"""  template = ${templateAmendExpr(g, sv)} {"""
    } else {
      s"""  template = new $moduleType {"""
    }
    s"""$header
           |$props
           |  }""".stripMargin
  }

  private def renderJsNativeOverride(
      label: String,
      amendExpr: String,
      m: ModuleDef,
      scalaJsVersion: Option[String],
      scalaNativeVersion: Option[String]
  ): String = {
    val versionProp = scalaJsVersion
      .map(v => s"""    scalaJsVersion = "$v"""")
      .orElse(
        scalaNativeVersion.map(v => s"""    scalaNativeVersion = "$v"""")
      )
      .getOrElse("")

    val hasExtra = m.deps.nonEmpty || m.scalacPluginDeps.nonEmpty
    if (hasExtra) {
      val depsStr = renderDeps(m.deps, indent = 4)
      val pluginsStr = renderPluginDeps(m.scalacPluginDeps, indent = 4)
      s"""$label = ($amendExpr) {
         |$versionProp
         |${if (depsStr.nonEmpty) s"$depsStr\n" else ""}${
          if (pluginsStr.nonEmpty) s"$pluginsStr\n" else ""
        }  }""".stripMargin
    } else {
      s"""$label = ($amendExpr) { $versionProp }"""
    }
  }

  private def renderTestTemplate(
      g: ModuleGroup,
      jvmModule: ModuleDef,
      scalaVersionCtx: Option[ScalaVersionCtx],
      groupLookup: Map[String, ModuleGroup]
  ): String = {
    val resolvedCtx = scalaVersionCtx.getOrElse(ScalaVersionCtx.Literal(jvmModule.scalaVersion))
    val amendExpr = platformAmendExpr(g, "Test", resolvedCtx)
    val scalaVersionLine = resolvedCtx match {
      case ScalaVersionCtx.Placeholder    => "    scalaVersion = sv"
      case ScalaVersionCtx.Literal(v) => s"""    scalaVersion = "$v""""
    }
    val testModuleDepsStr = renderModuleDepsPkl(jvmModule.testModuleDeps, indent = 6, scalaVersionCtx, groupLookup)
    val testDepsStr =
      if (jvmModule.testDeps.nonEmpty) renderDeps(jvmModule.testDeps, indent = 6)
      else """    deps { "org.scalameta::munit:1.2.1" }"""
    val testPluginDeps = diffTemplatePluginDeps(jvmModule.scalacPluginDeps, g, jvmModule.scalaVersion)
    val testPluginDepsStr = renderPluginDeps(testPluginDeps, indent = 6)
    s"""  testTemplate = ($amendExpr) {
       |$scalaVersionLine
       |$testModuleDepsStr
       |$testDepsStr
       |$testPluginDepsStr
       |  }""".stripMargin
  }

  // ---- property blocks ----

  private def renderScalacOptions(opts: Seq[String], indent: Int): String = {
    if (opts.isEmpty) ""
    else {
      val entries = opts.map(o => s""""$o"""").mkString("\n")
      val spaces = " " * indent
      val inner = " " * (indent + 2)
      s"""${spaces}scalacOptions {\n${entries.split("\n").map(l => inner + l).mkString("\n")}\n$spaces}"""
    }
  }

  private def renderScalacOptionsSmart(
      m: ModuleDef,
      g: ModuleGroup,
      indent: Int,
      scalaVersionCtx: Option[ScalaVersionCtx]
  ): String = {
    if (g.usesTpolecat || g.usesTypelevel) {
      val version = scalaVersionCtx match {
        case Some(ScalaVersionCtx.Literal(v)) => v
        case _                                => m.scalaVersion
      }
      val templateSet =
        if (g.usesTpolecat)
          TemplateOptionsReader.tpolecatScalacOptions(version)
        else
          TemplateOptionsReader.typelevelScalacOptions(version)
      val filtered = m.scalacOptions.filterNot(templateSet.contains)
      if (filtered.isEmpty) ""
      else renderScalacOptions(filtered, indent)
    } else renderScalacOptions(m.scalacOptions, indent)
  }

  /** Suppress javacOptions when the typelevel template already provides them. */
  private val javacDefaults = Set("-encoding:utf8", "-Xlint:all")
  private def suppressJavacOptions(opts: Seq[String], g: ModuleGroup): Boolean =
    g.usesTypelevel && opts.nonEmpty && opts.forall(javacDefaults.contains)
  private def suppressJavacOptionsDeltas(opts: Seq[String], deltas: Map[String, Seq[String]], g: ModuleGroup): Boolean =
    g.usesTypelevel && (opts.nonEmpty || deltas.values.exists(_.nonEmpty)) &&
      opts.forall(javacDefaults.contains) &&
      deltas.values.forall(_.forall(javacDefaults.contains))

  /** Typelevel Scala 2 templates already define these plugins. */
  private val pluginDefaults = Set(
    "com.olegpy" -> "better-monadic-for",
    "org.typelevel" -> "kind-projector"
  )
  private def templateProvidedPluginCoords(g: ModuleGroup, scalaVersion: String): Set[(String, String)] =
    if (g.usesTypelevel && scalaVersion.startsWith("2")) pluginDefaults else Set.empty

  private def diffTemplatePluginDeps(deps: Seq[DepDef], g: ModuleGroup, scalaVersion: String): Seq[DepDef] = {
    val templatePlugins = templateProvidedPluginCoords(g, scalaVersion)
    deps.filterNot(d => templatePlugins.contains(d.organization -> d.name))
  }

  private def renderJavacOptions(opts: Seq[String], indent: Int): String = {
    if (opts.isEmpty) ""
    else {
      val entries = opts.map(o => s""""$o"""").mkString("\n")
      val spaces = " " * indent
      val inner = " " * (indent + 2)
      s"""${spaces}javacOptions {\n${entries.split("\n").map(l => inner + l).mkString("\n")}\n$spaces}"""
    }
  }

  private def renderDeps(deps: Seq[DepDef], indent: Int): String = {
    if (deps.isEmpty) ""
    else {
      val entries = deps.map(d => s""""${d.formatted}"""").mkString("\n")
      val spaces = " " * indent
      val inner = " " * (indent + 2)
      s"""${spaces}deps {\n${entries.split("\n").map(l => inner + l).mkString("\n")}\n$spaces}"""
    }
  }

  private def renderPluginDeps(deps: Seq[DepDef], indent: Int): String = {
    if (deps.isEmpty) ""
    else {
      val entries = deps.map(d => s""""${d.formatted}"""").mkString("\n")
      val spaces = " " * indent
      val inner = " " * (indent + 2)
      s"""${spaces}scalacPluginDeps {\n${entries.split("\n").map(l => inner + l).mkString("\n")}\n$spaces}"""
    }
  }

  private def renderModuleDepsPkl(
      refs: Seq[ModuleDepRef],
      indent: Int,
      scalaVersionCtx: Option[ScalaVersionCtx] = None,
      groupLookup: Map[String, ModuleGroup] = Map.empty
  ): String = {
    if (refs.isEmpty) ""
    else {
      val entries = scalaVersionCtx match {
        case Some(ctx) => refs.map(r => crossDepFilter(r, groupLookup, ctx)).mkString("\n")
        case None      => refs.map(refString).mkString("\n")
      }
      val spaces = " " * indent
      val inner = " " * (indent + 2)
      s"""${spaces}moduleDeps {\n${entries.split("\n").map(l => inner + l).mkString("\n")}\n$spaces}"""
    }
  }

  private def renderSourceDirs(dirs: Seq[String], indent: Int): String = {
    if (dirs.isEmpty) ""
    else {
      val entries = dirs.map(d => s""""$d"""").mkString("\n")
      val spaces = " " * indent
      val inner = " " * (indent + 2)
      s"""${spaces}sources {\n${entries.split("\n").map(l => inner + l).mkString("\n")}\n$spaces}"""
    }
  }

  private def renderResourceDirs(dirs: Seq[String], indent: Int): String = {
    if (dirs.isEmpty) ""
    else {
      val entries = dirs.map(d => s""""$d"""").mkString("\n")
      val spaces = " " * indent
      val inner = " " * (indent + 2)
      s"""${spaces}resources {\n${entries.split("\n").map(l => inner + l).mkString("\n")}\n$spaces}"""
    }
  }

  private def renderPublishInfoBase(p: PublishInfo): String = {
    val inner = "  "
    val inner2 = "    "

    val lines = Seq.newBuilder[String]
    lines += "local const basePomSettings = new PomSettings {"
    lines += s"${inner}groupId = \"${p.organization}\""
    p.description.foreach(d => lines += s"""$inner description = "$d"""")
    p.homepage.foreach(h => lines += s"""$inner url = "$h"""")
    if (p.developers.nonEmpty) {
      val devs = p.developers
        .map(d => s"""$inner2 new PomDeveloper { id = "${d.id}"; name = "${d.name}"; email = "${d.email}" }""")
        .mkString("\n")
      lines += s"${inner}developers {"
      lines += devs
      lines += s"$inner }"
    }
    if (p.licenses.nonEmpty) {
      val lics =
        p.licenses.map(l => s"""$inner2 new PomLicense { name = "${l.name}"; url = "${l.url}" }""").mkString("\n")
      lines += s"${inner}licenses {"
      lines += lics
      lines += s"$inner }"
    }
    p.scmInfo.foreach { scm =>
      lines += s"${inner}scm {"
      lines += s"""$inner2 url = "${scm.browseUrl}""""
      lines += s"""$inner2 connection = "${scm.connection}""""
      scm.devConnection.foreach(dc => lines += s"""$inner2 developerConnection = "$dc"""")
      lines += s"$inner }"
    }
    lines += "}"
    lines.result().mkString("\n")
  }

  private def renderPublishInfo(p: PublishInfo, indent: Int, useBase: Boolean = false): String = {
    val spaces = " " * indent
    val inner = " " * (indent + 2)
    if (useBase) {
      val lines = Seq.newBuilder[String]
      lines += s"${spaces}pomSettings = (basePomSettings) {"
      lines += s"${inner}artifactId = \"${p.artifactName}\""
      if (p.version.nonEmpty) {
        lines += s"${inner}version = \"${p.version}\""
      }
      lines += s"$spaces}"
      lines.result().mkString("\n")
    } else {
      val inner2 = " " * (indent + 4)
      val lines = Seq.newBuilder[String]
      lines += s"${spaces}pomSettings {"
      lines += s"${inner}groupId = \"${p.organization}\""
      lines += s"${inner}artifactId = \"${p.artifactName}\""
      p.description.foreach(d => lines += s"""$inner description = "$d"""")
      p.homepage.foreach(h => lines += s"""$inner url = "$h"""")
      if (p.developers.nonEmpty) {
        val devs = p.developers
          .map(d => s"""$inner2 new PomDeveloper { id = "${d.id}"; name = "${d.name}"; email = "${d.email}" }""")
          .mkString("\n")
        lines += s"${inner}developers {"
        lines += devs
        lines += s"$inner }"
      }
      if (p.licenses.nonEmpty) {
        val lics =
          p.licenses.map(l => s"""$inner2 new PomLicense { name = "${l.name}"; url = "${l.url}" }""").mkString("\n")
        lines += s"${inner}licenses {"
        lines += lics
        lines += s"$inner }"
      }
      p.scmInfo.foreach { scm =>
        lines += s"${inner}scm {"
        lines += s"""$inner2 url = "${scm.browseUrl}""""
        lines += s"""$inner2 connection = "${scm.connection}""""
        scm.devConnection.foreach(dc => lines += s"""$inner2 developerConnection = "$dc"""")
        lines += s"$inner }"
      }
      if (p.version.nonEmpty) {
        lines += s"""$inner version = "${p.version}""""
      }
      lines += s"$spaces}"
      lines.result().mkString("\n")
    }
  }

  private def refString(r: ModuleDepRef): String = {
    val suffix = r.targetPlatform match {
      case "main" if r.isTest   => ".test"
      case "main"               => ".main"
      case "jvm" if r.isTest    => ".jvm_test"
      case "jvm"                => ".jvm"
      case "js" if r.isTest     => ".js_test"
      case "js"                 => ".js"
      case "native" if r.isTest => ".native_test"
      case "native"             => ".native"
      case _                    => s".${r.targetPlatform}"
    }
    s"${r.targetGroup}$suffix"
  }

  private def crossDepFilter(
      ref: ModuleDepRef,
      groupLookup: Map[String, ModuleGroup],
      scalaVersionCtx: ScalaVersionCtx
  ): String = {
    val name = ref.targetGroup
    val targetGroup = groupLookup.getOrElse(name, null)
    // Non-cross targets (and unknown/external targets) are declared as `local const name = ...get`
    // (no Modules suffix). Use the direct accessor rather than nameModules.find(...).
    if (targetGroup == null || targetGroup.crossScalaVersions.isEmpty) {
      return refString(ref)
    }
    val builderType = builderTypeFor(targetGroup)
    val plat = builderType match {
      case "CreateScalaModules" =>
        "-jvm"
      case "CreateCrossModules" =>
        ref.targetPlatform match {
          case "main" | "jvm" => "-jvm"
          case other          => s"-$other"
        }
      case other =>
        throw new IllegalArgumentException(s"Unexpected builder type: $other")
    }
    val testSuffix = if (ref.isTest) "-test" else ""
    scalaVersionCtx match {
      case ScalaVersionCtx.Placeholder =>
        val idPattern = s"$name$plat$testSuffix"
        s"""${name}Modules.find((m) -> m.id == "$idPattern-\\(sv)")"""
      case ScalaVersionCtx.Literal(ownerVersion) =>
        val targetVersion = ref.targetScalaVersion.getOrElse(ownerVersion)
        val idWithVersion = s"$name$plat$testSuffix-$targetVersion"
        s"""${name}Modules.find((m) -> m.id == "$idWithVersion")"""
    }
  }
}
