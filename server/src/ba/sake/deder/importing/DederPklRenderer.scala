package ba.sake.deder.importing

import ba.sake.deder.config.DederProject

object DederPklRenderer {
    private enum ScalaVersionCtx:
        case Placeholder
        case Literal(value: String)

    private case class VersionSlice(
        scalaVersion: String,
        modulesByPlatform: Map[String, ModuleDef],
    )

    private val platformOrder = Seq("main", "jvm", "js", "native")

    def render(build: DederBuild): String = {
        val header = s"""amends "https://sake92.github.io/deder/config/${build.dederVersion}/DederProject.pkl""""
        val repos = renderRepositories(build.repositories)
        val groupLookup = build.moduleGroups.map(g => g.builderVarName -> g).toMap

        val needsTpolecatImport  = build.moduleGroups.exists(_.usesTpolecat)
        val needsTypelevelImport = build.moduleGroups.exists(_.usesTypelevel)

        val helperImport = (needsTypelevelImport, needsTpolecatImport) match {
            case (true, _)  => Some("""import "DederTypelevel.pkl"""")
            case (_, true)  => Some("""import "DederTpolecat.pkl"""")
            case _          => None
        }

        val crossGroups = build.moduleGroups.filter(g => g.crossScalaVersions.nonEmpty)
        val sharedVersionListName: Option[String] =
            if (crossGroups.map(_.crossScalaVersions).distinct.size == 1 && crossGroups.size > 1) Some("projectScalaVersions")
            else None

        val sharedVersionsDecl = sharedVersionListName.flatMap { _ =>
            crossGroups.headOption.map { g =>
                val vs = versionsFor(g).map(v => s""""$v"""").mkString("List(", ", ", ")")
                s"local const projectScalaVersions = $vs"
            }
        }

        val builders = build.moduleGroups.map { g =>
            val isCross = g.crossScalaVersions.nonEmpty
            renderGroup(
                g,
                groupLookup,
                if (isCross) sharedVersionListName else None,
            )
        }.mkString("\n\n")
        val modulesBlock = renderModulesBlock(build.moduleGroups)

        List(Some(header), helperImport, sharedVersionsDecl, if (repos.nonEmpty) Some(repos) else None, Some(builders), Some(modulesBlock))
            .flatten.mkString("\n\n")
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
            else Seq(
                Some(s"$name.jvm"), Some(s"$name.jvm_test"),
                if (g.hasJsModule) Some(s"$name.js") else None,
                if (g.hasJsModule) Some(s"$name.js_test") else None,
                if (g.hasNativeModule) Some(s"$name.native") else None,
                if (g.hasNativeModule) Some(s"$name.native_test") else None,
            ).flatten
        } else Seq(s"...$name.all")
    }

    private def versionsFor(g: ModuleGroup): Seq[String] = {
        val declared = if (g.crossScalaVersions.nonEmpty) g.crossScalaVersions else g.concreteModules.map(_.scalaVersion).distinct
        val extra = g.concreteModules.map(_.scalaVersion).distinct.filterNot(declared.contains)
        declared ++ extra
    }

    private def versionSlices(g: ModuleGroup): Seq[VersionSlice] = {
        val byVersion = g.concreteModules.groupBy(_.scalaVersion)
        versionsFor(g).map { version =>
            val modulesByPlatform = byVersion.getOrElse(version, Seq.empty)
                .sortBy(cm => platformOrder.indexOf(cm.platform))
                .map(cm => cm.platform -> cm.module)
                .toMap
            VersionSlice(version, modulesByPlatform)
        }
    }

    /** Computes properties common to ALL versions in a cross-version group.
      * Returns a ModuleDef where each Seq property is the intersection across all
      * version slices. moduleDeps are normalized: two refs differing only in
      * targetScalaVersion are treated as identical. */
    private def computeCommonProps(slices: Seq[VersionSlice]): ModuleDef = {
        val allModuleDefs = slices.flatMap { slice =>
            slice.modulesByPlatform.get("jvm").orElse(slice.modulesByPlatform.get("main"))
        }
        if (allModuleDefs.isEmpty) return ModuleDef("", Seq.empty, Seq.empty, Seq.empty, Seq.empty, Seq.empty, Seq.empty, Seq.empty, None, None, None, Seq.empty, Seq.empty, Seq.empty, Seq.empty)

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
                depsSeq.head.filter(r => common.contains(normalized(r)))
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
            testResources = Seq.empty,
        )
    }

    /** For each version, computes the additions over the common set.
      * Returns Map[scalaVersion -> ModuleDef with only added properties].
      * Properties identical to common are empty in the delta. */
    private def computeVersionDeltas(
        slices: Seq[VersionSlice],
        common: ModuleDef,
    ): Map[String, ModuleDef] = {
        slices.flatMap { slice =>
            slice.modulesByPlatform.get("jvm").orElse(slice.modulesByPlatform.get("main")).map { m =>
                val v = slice.scalaVersion
                v -> ModuleDef(
                    scalaVersion = v,
                    scalacOptions = m.scalacOptions.filterNot(common.scalacOptions.contains),
                    javacOptions = m.javacOptions.filterNot(common.javacOptions.contains),
                    deps = m.deps.filterNot(d => common.deps.exists(_.formatted == d.formatted)),
                    scalacPluginDeps = m.scalacPluginDeps.filterNot(d => common.scalacPluginDeps.exists(_.formatted == d.formatted)),
                    testDeps = Seq.empty,
                    moduleDeps = m.moduleDeps.filterNot { ref =>
                        common.moduleDeps.exists(c =>
                            c.targetGroup == ref.targetGroup &&
                            c.targetPlatform == ref.targetPlatform &&
                            c.isTest == ref.isTest)
                    },
                    testModuleDeps = Seq.empty,
                    scalaJsVersion = common.scalaJsVersion match {
                        case Some(cv) if m.scalaJsVersion.contains(cv) => None
                        case _ => m.scalaJsVersion
                    },
                    scalaNativeVersion = common.scalaNativeVersion match {
                        case Some(cv) if m.scalaNativeVersion.contains(cv) => None
                        case _ => m.scalaNativeVersion
                    },
                    publish = if (common.publish == m.publish) None else m.publish,
                    sources = m.sources.filterNot(common.sources.contains),
                    testSources = Seq.empty,
                    resources = m.resources.filterNot(common.resources.contains),
                    testResources = Seq.empty,
                )
            }
        }.toMap
    }

    /** Renders when(sv == "version") { ... } blocks for version-specific property additions.
      * Only emits blocks for versions that have non-empty deltas. */
    private def renderDeltaWhenBlocks(
        deltas: Map[String, ModuleDef],
        groupLookup: Map[String, ModuleGroup],
        g: ModuleGroup,
        indent: Int = 8,
    ): String = {
        val spaces = " " * indent
        deltas.toSeq.sortBy(_._1).flatMap { case (version, delta) =>
            val props = Seq(
                if (g.usesTpolecat || g.usesTypelevel || delta.scalacOptions.nonEmpty)
                    Some(renderScalacOptionsSmart(delta, g, indent + 2, Some(ScalaVersionCtx.Placeholder)))
                else None,
                if (delta.javacOptions.nonEmpty) Some(renderJavacOptions(delta.javacOptions, indent + 2)) else None,
                if (delta.deps.nonEmpty) Some(renderDeps(delta.deps, indent + 2)) else None,
                if (delta.scalacPluginDeps.nonEmpty) Some(renderPluginDeps(delta.scalacPluginDeps, indent + 2)) else None,
                if (delta.sources.nonEmpty) Some(renderSourceDirs(delta.sources, indent + 2)) else None,
                if (delta.resources.nonEmpty) Some(renderResourceDirs(delta.resources, indent + 2)) else None,
                if (delta.moduleDeps.nonEmpty) Some(renderModuleDepsPkl(delta.moduleDeps, indent + 2, Some(ScalaVersionCtx.Placeholder), groupLookup)) else None,
            ).flatten
            if (props.isEmpty) None
            else Some(s"${spaces}when (sv == \"$version\") {\n${props.mkString("\n")}\n$spaces}")
        }.mkString("\n")
    }

    // ---- group rendering ----

    private def renderGroup(
        g: ModuleGroup,
        groupLookup: Map[String, ModuleGroup],
        sharedVersionListName: Option[String],
    ): String = {
        val slices = versionSlices(g)
        val builderType = builderTypeFor(g)

        if (g.crossScalaVersions.nonEmpty) {
            val common = computeCommonProps(slices)
            val rawDeltas = computeVersionDeltas(slices, common)
            val allVersions = versionsFor(g)

            val hasScalacOptionsDelta = rawDeltas.exists(_._2.scalacOptions.nonEmpty)
            val hasJavacOptionsDelta = rawDeltas.exists(_._2.javacOptions.nonEmpty)
            val hasDepsDelta = rawDeltas.exists(_._2.deps.nonEmpty)
            val hasScalacPluginDepsDelta = rawDeltas.exists(_._2.scalacPluginDeps.nonEmpty)
            val hasSourcesDelta = rawDeltas.exists(_._2.sources.nonEmpty)
            val hasResourcesDelta = rawDeltas.exists(_._2.resources.nonEmpty)
            val hasModuleDepsDelta = rawDeltas.exists(_._2.moduleDeps.nonEmpty)

            val safeCommon = common.copy(
                scalacOptions = if (hasScalacOptionsDelta) Seq.empty else common.scalacOptions,
                javacOptions = if (hasJavacOptionsDelta) Seq.empty else common.javacOptions,
                deps = if (hasDepsDelta) Seq.empty else common.deps,
                scalacPluginDeps = if (hasScalacPluginDepsDelta) Seq.empty else common.scalacPluginDeps,
                sources = if (hasSourcesDelta) Seq.empty else common.sources,
                resources = if (hasResourcesDelta) Seq.empty else common.resources,
                moduleDeps = if (hasModuleDepsDelta) Seq.empty else common.moduleDeps,
            )

            val versionDeltas = rawDeltas.view.mapValues { delta =>
                delta.copy(
                    scalacOptions = (if (hasScalacOptionsDelta) common.scalacOptions else Seq.empty) ++ delta.scalacOptions,
                    javacOptions = (if (hasJavacOptionsDelta) common.javacOptions else Seq.empty) ++ delta.javacOptions,
                    deps = (if (hasDepsDelta) common.deps else Seq.empty) ++ delta.deps,
                    scalacPluginDeps = (if (hasScalacPluginDepsDelta) common.scalacPluginDeps else Seq.empty) ++ delta.scalacPluginDeps,
                    sources = (if (hasSourcesDelta) common.sources else Seq.empty) ++ delta.sources,
                    resources = (if (hasResourcesDelta) common.resources else Seq.empty) ++ delta.resources,
                    moduleDeps = (if (hasModuleDepsDelta) common.moduleDeps else Seq.empty) ++ delta.moduleDeps,
                )
            }.toMap

            val deltas = allVersions.map { v =>
                v -> versionDeltas.getOrElse(v, ModuleDef(v,
                    scalacOptions = if (hasScalacOptionsDelta) common.scalacOptions else Seq.empty,
                    javacOptions = if (hasJavacOptionsDelta) common.javacOptions else Seq.empty,
                    deps = if (hasDepsDelta) common.deps else Seq.empty,
                    scalacPluginDeps = if (hasScalacPluginDepsDelta) common.scalacPluginDeps else Seq.empty,
                    sources = if (hasSourcesDelta) common.sources else Seq.empty,
                    resources = if (hasResourcesDelta) common.resources else Seq.empty,
                    moduleDeps = if (hasModuleDepsDelta) common.moduleDeps else Seq.empty,
                    testDeps = Seq.empty,
                    testModuleDeps = Seq.empty,
                    scalaJsVersion = None,
                    scalaNativeVersion = None,
                    publish = None,
                    testSources = Seq.empty,
                    testResources = Seq.empty,
                ))
            }.toMap

            val commonPropsStr = {
                val props = Seq(
                    if (g.usesTpolecat || g.usesTypelevel || common.scalacOptions.nonEmpty)
                        Some(renderScalacOptionsSmart(common, g, indent = 4, Some(ScalaVersionCtx.Placeholder)))
                    else None,
                    if (safeCommon.javacOptions.nonEmpty) Some(renderJavacOptions(safeCommon.javacOptions, indent = 4)) else None,
                    if (safeCommon.deps.nonEmpty) Some(renderDeps(safeCommon.deps, indent = 4)) else None,
                    if (safeCommon.scalacPluginDeps.nonEmpty) Some(renderPluginDeps(safeCommon.scalacPluginDeps, indent = 4)) else None,
                    if (safeCommon.sources.nonEmpty) Some(renderSourceDirs(safeCommon.sources, indent = 4)) else None,
                    if (safeCommon.resources.nonEmpty) Some(renderResourceDirs(safeCommon.resources, indent = 4)) else None,
                    if (safeCommon.moduleDeps.nonEmpty) Some(renderModuleDepsPkl(safeCommon.moduleDeps, indent = 4, Some(ScalaVersionCtx.Placeholder), groupLookup)) else None,
                    safeCommon.publish.map(p => renderPublishInfo(p, indent = 4)),
                ).flatten.mkString("\n")
                if (props.nonEmpty) props + "\n" else ""
            }

            val whenBlocksStr = renderDeltaWhenBlocks(deltas, groupLookup, g, indent = 4)

            val templateBody = {
                val body =
                    s"""    scalaVersion = sv
                       |${if (commonPropsStr.nonEmpty) commonPropsStr else ""}${if (whenBlocksStr.nonEmpty) "\n" + whenBlocksStr else ""}""".stripMargin
                s"""  template = new ScalaModule {
                   |$body
                   |  }""".stripMargin
            }

            val repMods = slices.headOption.getOrElse(VersionSlice("", Map.empty)).modulesByPlatform
            val jvmModule = repMods.get("jvm").orElse(repMods.get("main")).getOrElse(ModuleDef("", Seq.empty, Seq.empty, Seq.empty, Seq.empty, Seq.empty, Seq.empty, Seq.empty, None, None, None, Seq.empty, Seq.empty, Seq.empty, Seq.empty))
            val isCross = repMods.contains("js") || repMods.contains("native")

            val testTmpl = renderTestTemplate(jvmModule, Some(ScalaVersionCtx.Placeholder), groupLookup)

            val crossPlatTmpls = if (isCross) {
                val jsTmpl = repMods.get("js").map { m =>
                    val body = renderJsNativeOverride(
                        "jsTemplate", "template.asJs()", m,
                        scalaJsVersion = m.scalaJsVersion.orElse(Some("1.18.2")),
                        scalaNativeVersion = None
                    )
                    s"  $body"
                }.getOrElse("")

                val nativeTmpl = repMods.get("native").map { m =>
                    val body = renderJsNativeOverride(
                        "nativeTemplate", "template.asNative()", m,
                        scalaJsVersion = None,
                        scalaNativeVersion = m.scalaNativeVersion.orElse(Some("0.5.10"))
                    )
                    s"  $body"
                }.getOrElse("")

                val jsTestTmpl = repMods.get("js").map { m =>
                    if (m.testDeps.nonEmpty) {
                        val depsStr = renderDeps(m.testDeps, indent = 6)
                        s"  jsTestTemplate = (jsTemplate.asTest()) {\n$depsStr  }"
                    } else ""
                }.getOrElse("")

                val nativeTestTmpl = repMods.get("native").map { m =>
                    if (m.testDeps.nonEmpty) {
                        val depsStr = renderDeps(m.testDeps, indent = 6)
                        s"  nativeTestTemplate = (nativeTemplate.asTest()) {\n$depsStr  }"
                    } else ""
                }.getOrElse("")

                Seq(jsTmpl, nativeTmpl, jsTestTmpl, nativeTestTmpl).filter(_.nonEmpty).mkString("\n")
            } else ""

            val body = Seq(Some(templateBody), Some(testTmpl), if (crossPlatTmpls.nonEmpty) Some(crossPlatTmpls) else None)
                .flatten.mkString("\n")

            val versionsListName = sharedVersionListName.getOrElse(s"${g.builderVarName}ScalaVersions")
            val versionsDecl = if (sharedVersionListName.isEmpty)
                s"local const $versionsListName = ${versionsFor(g).map(v => s""""$v"""").mkString("List(", ", ", ")")}\n\n"
            else ""

            s"""${versionsDecl}local const ${g.builderVarName}Modules = $versionsListName
               |  .map((sv) ->
               |    new $builderType {
               |      root = "${g.root}"
               |      id = "${crossVersionIdWithPlaceholder(g, builderType)}"
               |      layout = "${g.layout.toString.toLowerCase.replace("_","-")}"
               |$body
               |    }.get.all
               |  ).flatten()""".stripMargin
        } else {
            val representativeSlice = slices.find(_.modulesByPlatform.nonEmpty).getOrElse {
                val fallbackVersion = versionsFor(g).headOption.getOrElse("")
                VersionSlice(fallbackVersion, Map.empty)
            }
            val body = renderGroupBody(representativeSlice.modulesByPlatform, groupLookup, g, None)
            s"""local const ${g.builderVarName} = new $builderType {
               |  root = "${g.root}"
               |  id = "${g.builderVarName}"
               |  layout = "${g.layout.toString.toLowerCase.replace("_","-")}"
               |$body
               |}.get""".stripMargin
        }
    }

    private def builderTypeFor(g: ModuleGroup): String = {
        if (g.hasJsModule || g.hasNativeModule) "CreateCrossModules"
        else "CreateScalaModules"
    }

    private def crossVersionIdWithPlaceholder(g: ModuleGroup, builderType: String): String =
        if (builderType == "CreateCrossModules") s"${g.builderVarName}"
        else s"${g.builderVarName}-\\(sv)"

    private def crossVersionIdWithLiteral(g: ModuleGroup, builderType: String, scalaVersion: String): String =
        if (builderType == "CreateCrossModules") s"${g.builderVarName}"
        else s"${g.builderVarName}-$scalaVersion"

    private def renderGroupBody(
        modulesByPlatform: Map[String, ModuleDef],
        groupLookup: Map[String, ModuleGroup],
        g: ModuleGroup,
        scalaVersionCtx: Option[ScalaVersionCtx],
    ): String = {
        val isCross = modulesByPlatform.contains("jvm") || modulesByPlatform.contains("js") || modulesByPlatform.contains("native")
        val jvmModule = selectTemplateModule(modulesByPlatform)

        val jvmBody = renderTemplateBody(jvmModule, "ScalaModule", None, scalaVersionCtx, groupLookup, g)
        val testTmpl = renderTestTemplate(jvmModule, scalaVersionCtx, groupLookup)

        if (isCross) {
            val jsTmpl = modulesByPlatform.get("js").map { m =>
                val body = renderJsNativeOverride(
                    "jsTemplate",
                    "template.asJs()",
                    m,
                    scalaJsVersion = m.scalaJsVersion.orElse(Some("1.18.2")),
                    scalaNativeVersion = None
                )
                s"  $body"
            }.getOrElse("")

            val nativeTmpl = modulesByPlatform.get("native").map { m =>
                val body = renderJsNativeOverride(
                    "nativeTemplate",
                    "template.asNative()",
                    m,
                    scalaJsVersion = None,
                    scalaNativeVersion = m.scalaNativeVersion.orElse(Some("0.5.10"))
                )
                s"  $body"
            }.getOrElse("")

            val jsTestTmpl = modulesByPlatform.get("js").map { m =>
                if (m.testDeps.nonEmpty) {
                    val depsStr = renderDeps(m.testDeps, indent = 6)
                    s"  jsTestTemplate = (jsTemplate.asTest()) {\n$depsStr  }"
                } else ""
            }.getOrElse("")

            val nativeTestTmpl = modulesByPlatform.get("native").map { m =>
                if (m.testDeps.nonEmpty) {
                    val depsStr = renderDeps(m.testDeps, indent = 6)
                    s"  nativeTestTemplate = (nativeTemplate.asTest()) {\n$depsStr  }"
                } else ""
            }.getOrElse("")

            val tmpls = Seq(jsTmpl, nativeTmpl, jsTestTmpl, nativeTestTmpl).filter(_.nonEmpty).mkString("\n")
            val tmplsWithNewline = if (tmpls.nonEmpty) tmpls + "\n" else ""

            s"""$jvmBody
               |$tmplsWithNewline$testTmpl""".stripMargin
        } else {
            s"""$jvmBody
               |$testTmpl""".stripMargin
        }
    }

    private def selectTemplateModule(modulesByPlatform: Map[String, ModuleDef]): ModuleDef = {
        modulesByPlatform.get("jvm")
            .orElse(modulesByPlatform.get("main"))
            .orElse(platformOrder.iterator.map(modulesByPlatform.get).collectFirst { case Some(module) => module })
            .orElse(modulesByPlatform.toSeq.sortBy(_._1).headOption.map(_._2))
            .getOrElse(throw IllegalArgumentException("Cannot render module group body: no modules found in version slice"))
    }

    private def renderTemplateBody(
        m: ModuleDef, moduleType: String, extraProps: Option[String],
        scalaVersionCtx: Option[ScalaVersionCtx] = None,
        groupLookup: Map[String, ModuleGroup] = Map.empty,
        g: ModuleGroup,
    ): String = {
        val extra = extraProps.map(e => s"    $e\n").getOrElse("")
        val versionLine = scalaVersionCtx match {
            case Some(ScalaVersionCtx.Placeholder) => Some("    scalaVersion = sv")
            case Some(ScalaVersionCtx.Literal(value)) => Some(s"""    scalaVersion = "$value"""")
            case None if moduleType != "ScalaJsModule" && moduleType != "ScalaNativeModule" =>
                Some(s"""    scalaVersion = "${m.scalaVersion}"""")
            case _ => None
        }
        val props = Seq(
            versionLine,
            Some(extra.trim).filter(_.nonEmpty),
            if (g.usesTpolecat || g.usesTypelevel || m.scalacOptions.nonEmpty)
                Some(renderScalacOptionsSmart(m, g, indent = 4, scalaVersionCtx))
            else None,
            Some(renderJavacOptions(m.javacOptions, indent = 4)).filter(_.nonEmpty),
            Some(renderSourceDirs(m.sources, indent = 4)).filter(_.nonEmpty),
            Some(renderResourceDirs(m.resources, indent = 4)).filter(_.nonEmpty),
            Some(renderDeps(m.deps, indent = 4)).filter(_.nonEmpty),
            Some(renderPluginDeps(m.scalacPluginDeps, indent = 4)).filter(_.nonEmpty),
            Some(renderModuleDepsPkl(m.moduleDeps, indent = 4, scalaVersionCtx, groupLookup)).filter(_.nonEmpty),
            m.publish.map(p => renderPublishInfo(p, indent = 4)).filter(_.nonEmpty),
        ).flatten.mkString("\n")
        s"""  template = new $moduleType {
           |$props
           |  }""".stripMargin
    }

    private def renderJsNativeOverride(
        label: String,
        asFun: String,
        m: ModuleDef,
        scalaJsVersion: Option[String],
        scalaNativeVersion: Option[String]
    ): String = {
        val versionProp = scalaJsVersion.map(v => s"""    scalaJsVersion = "$v"""").orElse(
            scalaNativeVersion.map(v => s"""    scalaNativeVersion = "$v"""")
        ).getOrElse("")

        val hasExtra = m.deps.nonEmpty || m.scalacPluginDeps.nonEmpty
        if (hasExtra) {
            val depsStr = renderDeps(m.deps, indent = 4)
            val pluginsStr = renderPluginDeps(m.scalacPluginDeps, indent = 4)
            s"""$label = ($asFun) {
               |$versionProp
               |${if (depsStr.nonEmpty) s"$depsStr\n" else ""}${if (pluginsStr.nonEmpty) s"$pluginsStr\n" else ""}  }""".stripMargin
        } else {
            s"""$label = ($asFun) { $versionProp }"""
        }
    }

    private def renderTestTemplate(
        jvmModule: ModuleDef,
        scalaVersionCtx: Option[ScalaVersionCtx],
        groupLookup: Map[String, ModuleGroup]
    ): String = {
        val testModuleDepsStr = renderModuleDepsPkl(jvmModule.testModuleDeps, indent = 6, scalaVersionCtx, groupLookup)
        val testDepsStr = if (jvmModule.testDeps.nonEmpty) renderDeps(jvmModule.testDeps, indent = 6)
                          else """    deps { "org.scalameta::munit:1.2.1" }"""
        s"""  testTemplate = (template.asTest()) {
           |$testModuleDepsStr
           |$testDepsStr
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
        scalaVersionCtx: Option[ScalaVersionCtx],
    ): String = {
        val spaces = " " * indent
        val versionRef = scalaVersionCtx match {
            case Some(ScalaVersionCtx.Placeholder) => "sv"
            case Some(ScalaVersionCtx.Literal(v))  => s""""$v""""
            case None                              => "\"\""
        }
        if (g.usesTypelevel) {
            s"""${spaces}// Managed by sbt-typelevel. To customize: override scalacOptions directly.
               |${spaces}scalacOptions = DederTypelevel.forVersion($versionRef)""".stripMargin
        } else if (g.usesTpolecat) {
            s"""${spaces}// Managed by sbt-tpolecat. Mode auto-selected: Ci when $$CI is set, Dev otherwise.
               |${spaces}scalacOptions = DederTpolecat.forVersion($versionRef)""".stripMargin
        } else {
            renderScalacOptions(m.scalacOptions, indent)
        }
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
        refs: Seq[ModuleDepRef], indent: Int,
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

    private def renderPublishInfo(p: PublishInfo, indent: Int): String = {
        val spaces = " " * indent
        val inner = " " * (indent + 2)
        val inner2 = " " * (indent + 4)

        val lines = Seq.newBuilder[String]
        lines += s"${spaces}pomSettings {"
        lines += s"${inner}groupId = \"${p.organization}\""
        lines += s"${inner}artifactId = \"${p.artifactName}\""
        p.description.foreach(d => lines += s"""$inner description = "$d"""")
        p.homepage.foreach(h => lines += s"""$inner url = "$h"""")
        if (p.developers.nonEmpty) {
            val devs = p.developers.map(d =>
                s"""$inner2 new PomDeveloper { id = "${d.id}"; name = "${d.name}"; email = "${d.email}" }"""
            ).mkString("\n")
            lines += s"${inner}developers {"
            lines += devs
            lines += s"$inner }"
        }
        if (p.licenses.nonEmpty) {
            val lics = p.licenses.map(l =>
                s"""$inner2 new PomLicense { name = "${l.name}"; url = "${l.url}" }"""
            ).mkString("\n")
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

    private def refString(r: ModuleDepRef): String = {
        val suffix = r.targetPlatform match {
            case "main"   if r.isTest => ".test"
            case "main"               => ".main"
            case "jvm"    if r.isTest => ".jvm_test"
            case "jvm"                => ".jvm"
            case "js"     if r.isTest => ".js_test"
            case "js"                 => ".js"
            case "native" if r.isTest => ".native_test"
            case "native"             => ".native"
            case _ => s".${r.targetPlatform}"
        }
        s"${r.targetGroup}$suffix"
    }

    private def crossDepFilter(
        ref: ModuleDepRef,
        groupLookup: Map[String, ModuleGroup],
        scalaVersionCtx: ScalaVersionCtx,
    ): String = {
        val name = ref.targetGroup
        val targetGroup = groupLookup.getOrElse(name, null)
        // Non-cross targets are declared as `local const name = ...get` (no Modules suffix).
        // Use the direct accessor rather than nameModules.find(...).
        if (targetGroup != null && targetGroup.crossScalaVersions.isEmpty) {
            return refString(ref)
        }
        val builderType = if (targetGroup != null) builderTypeFor(targetGroup) else "CreateScalaModules"
        val plat = if (ref.targetPlatform == "main") "" else s"-${ref.targetPlatform}"
        val testSuffix = if (ref.isTest) "-test" else ""
        val idWithoutVersion = builderType match {
            case "CreateCrossModules" => s"$name$plat$testSuffix"
            case _                    => s"$name$testSuffix"
        }
        scalaVersionCtx match {
            case ScalaVersionCtx.Placeholder =>
                s"""${name}Modules.find((m) -> m.id == "$idWithoutVersion-\\(sv)")"""
            case ScalaVersionCtx.Literal(ownerVersion) =>
                val targetVersion = ref.targetScalaVersion.getOrElse(ownerVersion)
                s"""${name}Modules.find((m) -> m.id == "$idWithoutVersion-$targetVersion")"""
        }
    }
}
