package ba.sake.deder.importing

import ba.sake.deder.config.DederProject

object DederPklRenderer {

    def render(build: DederBuild): String = {
        val header = s"""amends "https://sake92.github.io/deder/config/${build.dederVersion}/DederProject.pkl""""
        val repos = renderRepositories(build.repositories)
        val groupLookup = build.moduleGroups.map(g => g.builderVarName -> g).toMap

        val crossGroups = build.moduleGroups.filter(_.crossScalaVersions.nonEmpty)
        val sharedVersionListName: Option[String] =
            if (crossGroups.map(_.crossScalaVersions).distinct.size == 1 && crossGroups.size > 1) Some("projectScalaVersions")
            else None

        val sharedVersionsDecl = sharedVersionListName.flatMap { _ =>
            crossGroups.headOption.map { g =>
                val vs = g.crossScalaVersions.map(v => s""""$v"""").mkString("List(", ", ", ")")
                s"local const projectScalaVersions = $vs"
            }
        }

        val builders = build.moduleGroups.map(g => renderGroup(g, groupLookup, sharedVersionListName)).mkString("\n\n")
        val modulesBlock = renderModulesBlock(build.moduleGroups)

        List(Some(header), sharedVersionsDecl, if (repos.nonEmpty) Some(repos) else None, Some(builders), Some(modulesBlock))
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

    // ---- group rendering ----

    private def renderGroup(g: ModuleGroup, groupLookup: Map[String, ModuleGroup], sharedVersionListName: Option[String]): String = {
        val builderType = builderTypeFor(g)
        val body = renderGroupBody(g, groupLookup)
        if (g.crossScalaVersions.nonEmpty) {
            val versionsListName = sharedVersionListName.getOrElse(s"${g.builderVarName}ScalaVersions")
            val versionsDecl = if (sharedVersionListName.isEmpty)
                s"local const $versionsListName = ${g.crossScalaVersions.map(v => s""""$v"""").mkString("List(", ", ", ")")}\n\n"
            else ""
            s"""${versionsDecl}local const ${g.builderVarName}Modules = $versionsListName
               |  .map((sv) ->
               |    new $builderType {
               |      root = "${g.root}"
               |      id = "${crossVersionId(g, builderType)}"
               |      layout = "${g.layout.toString.toLowerCase.replace("_","-")}"
               |$body
               |    }.get.all
               |  ).flatten()""".stripMargin
        } else {
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
        else if (g.jsModule.isDefined) "CreateScalaJsModules"
        else if (g.nativeModule.isDefined) "CreateScalaNativeModules"
        else "CreateScalaModules"
    }

    private def crossVersionId(g: ModuleGroup, builderType: String): String =
        if (builderType == "CreateCrossModules") s"${g.builderVarName}"
        else s"${g.builderVarName}-\\(sv)"

    private def renderGroupBody(g: ModuleGroup, groupLookup: Map[String, ModuleGroup]): String = {
        val useVP = g.crossScalaVersions.nonEmpty
        val isCross = g.hasJsModule || g.hasNativeModule

        val jvmBody = renderTemplateBody(g.jvmModule, "ScalaModule", None, useVP, groupLookup)
        val testTmpl = renderTestTemplate(g, groupLookup)

        if (isCross) {
            val jsTmpl = g.jsModule.map { m =>
                val body = renderJsNativeOverride("jsTemplate", "template.asJs()", m, scalaJsVersion = g.jsModule.flatMap(_.scalaJsVersion).orElse(Some("1.18.2")), scalaNativeVersion = None)
                s"  $body"
            }.getOrElse("")

            val nativeTmpl = g.nativeModule.map { m =>
                val body = renderJsNativeOverride("nativeTemplate", "template.asNative()", m, scalaJsVersion = None, scalaNativeVersion = g.nativeModule.flatMap(_.scalaNativeVersion).orElse(Some("0.5.10")))
                s"  $body"
            }.getOrElse("")

            val jsTestTmpl = g.jsModule.map { m =>
                if (m.testDeps.nonEmpty) {
                    val depsStr = renderDeps(m.testDeps, indent = 6)
                    s"  jsTestTemplate = (jsTemplate.asTest()) {\n$depsStr  }"
                } else ""
            }.getOrElse("")

            val nativeTestTmpl = g.nativeModule.map { m =>
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

    private def renderTemplateBody(
        m: ModuleDef, moduleType: String, extraProps: Option[String],
        useVersionPlaceholder: Boolean = false,
        groupLookup: Map[String, ModuleGroup] = Map.empty
    ): String = {
        val extra = extraProps.map(e => s"    $e\n").getOrElse("")
        val versionLine = if (useVersionPlaceholder) Some("    scalaVersion = sv")
            else if (moduleType != "ScalaJsModule" && moduleType != "ScalaNativeModule") Some(s"""    scalaVersion = "${m.scalaVersion}"""")
            else None
        val props = Seq(
            versionLine,
            Some(extra.trim).filter(_.nonEmpty),
            Some(renderScalacOptions(m.scalacOptions, indent = 4)).filter(_.nonEmpty),
            Some(renderJavacOptions(m.javacOptions, indent = 4)).filter(_.nonEmpty),
            Some(renderSourceDirs(m.sources, indent = 4)).filter(_.nonEmpty),
            Some(renderResourceDirs(m.resources, indent = 4)).filter(_.nonEmpty),
            Some(renderDeps(m.deps, indent = 4)).filter(_.nonEmpty),
            Some(renderPluginDeps(m.scalacPluginDeps, indent = 4)).filter(_.nonEmpty),
            Some(renderModuleDepsPkl(m.moduleDeps, indent = 4, useVersionPlaceholder, groupLookup)).filter(_.nonEmpty),
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

    private def renderTestTemplate(g: ModuleGroup, groupLookup: Map[String, ModuleGroup]): String = {
        val useVP = g.crossScalaVersions.nonEmpty
        val testModuleDepsStr = renderModuleDepsPkl(g.jvmModule.testModuleDeps, indent = 6, useVP, groupLookup)
        val testDepsStr = if (g.jvmModule.testDeps.nonEmpty) renderDeps(g.jvmModule.testDeps, indent = 6)
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
        useVersionPlaceholder: Boolean = false,
        groupLookup: Map[String, ModuleGroup] = Map.empty
    ): String = {
        if (refs.isEmpty) ""
        else {
            val entries = if (useVersionPlaceholder) refs.map(r => crossDepFilter(r, groupLookup)).mkString("\n")
                          else refs.map(refString).mkString("\n")
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

    private def crossDepFilter(ref: ModuleDepRef, groupLookup: Map[String, ModuleGroup]): String = {
        val name = ref.targetGroup
        val targetGroup = groupLookup.getOrElse(name, null)
        val builderType = if (targetGroup != null) builderTypeFor(targetGroup) else "CreateScalaModules"
        val plat = if (ref.targetPlatform == "main") "" else s"-${ref.targetPlatform}"
        val testSuffix = if (ref.isTest) "-test" else ""
        val idWithoutVersion = builderType match {
            case "CreateCrossModules" => s"$name$plat$testSuffix"
            case _                    => s"$name$testSuffix"
        }
        s"""moduleById(${name}Modules, "$idWithoutVersion-\\(sv)")"""
    }
}
