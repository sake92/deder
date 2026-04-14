package ba.sake.deder.publish

import scala.jdk.CollectionConverters.*
import ba.sake.tupson.JsonRW
import ba.sake.deder.publish.{Hasher, PgpSigner, PomGenerator, PomSettings, Publisher}
import ba.sake.deder.config.DederProject.*
import ba.sake.deder.{*, given}
import ba.sake.deder.jvm.*
import dependency.ScalaVersion
import ba.sake.deder.deps.DependencyResolver
import ba.sake.deder.deps.Dependency
import java.io.File
import javax.tools.ToolProvider

class PublishTasks(coreTasks: CoreTasks) {

  val manifestSettingsTask = ConfigValueTask[ManifestEntries](
    name = "manifest",
    execute = { ctx =>
      ctx.module match {
        case m: JavaModule =>
          ManifestEntries(
            mainAttributes = m.manifest.mainAttributes.asScala.toMap,
            groups = m.manifest.groups.asScala.view.mapValues(_.asScala.toMap).toMap
          )
        case _ => ManifestEntries.Empty
      }
    }
  )

  val pomSettingsTask = ConfigValueTask[Option[PomSettings]](
    name = "pomSettings",
    execute = { ctx =>
      ctx.module match {
        case jm: JavaModule =>
          val pom = jm.pomSettings
          if jm.publish then {
            if pom == null then throw RuntimeException(s"POM settings are not set for ${jm.id}")
            val finalArtifactId = jm match {
              case m: ScalaJsModule =>
                s"${pom.artifactId}_sjs${ScalaVersion.jsBinary(m.scalaJsVersion).get}_${ScalaVersion.binary(m.scalaVersion)}"
              case m: ScalaNativeModule =>
                s"${pom.artifactId}_native${ScalaVersion.nativeBinary(m.scalaNativeVersion).get}_${ScalaVersion.binary(m.scalaVersion)}"
              case m: ScalaModule =>
                s"${pom.artifactId}_${ScalaVersion.binary(m.scalaVersion)}"
              case _ =>
                pom.artifactId
            }
            val resolvedVersion =
              if pom.version != null then pom.version
              else GitSemVer.detectVersion(DederGlobals.projectRootDir)
            Some(
              PomSettings(
                groupId = pom.groupId,
                artifactId = finalArtifactId,
                version = resolvedVersion
              )
            )
          } else {
            ctx.notifications.add(
              ServerNotification.logDebug(
                s"Skipping POM generation for module ${jm.id} because publish is set to false"
              )
            )
            None
          }
        case other => throw RuntimeException(s"POM settings cannot be applied to $other")
      }
    }
  )

  val finalManifestSettingsTask = CachedTaskBuilder
    .make[ManifestEntries](name = "finalManifest")
    .dependsOn(manifestSettingsTask)
    .dependsOn(coreTasks.finalMainClassTask)
    .dependsOn(pomSettingsTask)
    .build { ctx =>
      val (manifestEntries, mainClass, pomSettings) = ctx.depResults
      import java.util.jar.Attributes.Name as JarName

      // defaults from pom settings (when available)
      val pomDefaults = pomSettings match {
        case Some(pom) =>
          Map(
            JarName.IMPLEMENTATION_TITLE.toString -> pom.artifactId,
            JarName.IMPLEMENTATION_VERSION.toString -> pom.version,
            JarName.IMPLEMENTATION_VENDOR.toString -> pom.groupId,
            JarName.SPECIFICATION_TITLE.toString -> pom.artifactId,
            JarName.SPECIFICATION_VERSION.toString -> pom.version,
            JarName.SPECIFICATION_VENDOR.toString -> pom.groupId
          )
        case None =>
          Map(
            JarName.IMPLEMENTATION_TITLE.toString -> ctx.module.id
          )
      }

      // main class entry (when available)
      val mainClassEntry = mainClass.map(mc => JarName.MAIN_CLASS.toString -> mc)

      // precedence: pomDefaults < mainClass < user manifest entries
      val merged = pomDefaults ++ mainClassEntry ++ manifestEntries.mainAttributes
      manifestEntries.copy(mainAttributes = merged)
    }

  val jarTask = CachedTaskBuilder
    .make[os.Path](name = "jar")
    .dependsOn(coreTasks.compileTask)
    .dependsOn(finalManifestSettingsTask)
    .build { ctx =>
      val (localClasspath, manifestEntries) = ctx.depResults
      val resultJarPath = ctx.out / s"${ctx.module.id}.jar"
      val jarInputPaths = Seq(localClasspath.absPath)
      JarUtils.createJar(
        resultJarPath,
        jarInputPaths,
        manifestEntries.toJarManifest
      )
      resultJarPath
    }

  val allJarsTask = CachedTaskBuilder
    .make[Seq[os.Path]](
      name = "allJars",
      transitive = true
    )
    .dependsOn(jarTask)
    .build { ctx =>
      val jar = ctx.depResults._1
      ctx.transitiveResults.flatten.flatten.prepended(jar).reverse.distinct.reverse
    }

  val assemblyTask = CachedTaskBuilder
    .make[os.Path](
      name = "assembly",
      supportedModuleTypes = Set(ModuleType.JAVA, ModuleType.JAVA_TEST, ModuleType.SCALA, ModuleType.SCALA_TEST)
    )
    .dependsOn(coreTasks.scalaVersionTask)
    .dependsOn(finalManifestSettingsTask)
    .dependsOn(coreTasks.mandatoryDependenciesTask)
    .dependsOn(coreTasks.allDependenciesTask)
    .dependsOn(allJarsTask)
    .build { ctx =>
      val (scalaVersion, manifestEntries, mandatoryDependencies, dependencies, allModulesJars) =
        ctx.depResults
      val depsJars = DependencyResolver.fetchFiles(mandatoryDependencies ++ dependencies, Some(ctx.notifications))
      val tmpDir = ctx.out / "jars"
      os.makeDir.all(tmpDir)
      allModulesJars.zipWithIndex.foreach { case (jar, index) =>
        val jarName = s"${index + 1}-${jar.last}"
        os.copy.over(jar, tmpDir / jarName)
      }
      val allJars = os.list(tmpDir) ++ depsJars
      val mergedJar = ctx.out / "mergedJar.jar"
      JarUtils.mergeJars(mergedJar, allJars, manifestEntries.toJarManifest)
      val resultJarPath = ctx.out / "out.jar"
      JarUtils.createAssemblyJar(resultJarPath, mergedJar)
      resultJarPath
    }

  val moduleDepsPomSettingsTask = CachedTaskBuilder
    .make[Seq[Seq[PomSettings]]](
      name = "moduleDepsPomSettings",
      transitive = true
    )
    .dependsOn(pomSettingsTask)
    .build { ctx =>
      val pom: Option[PomSettings] = ctx.depResults._1
      // Index 0: this module's own pom settings (0 or 1 item)
      // Index 1: all direct module deps' pom settings combined (each dep contributes its index-0)
      val directDepsPoms = ctx.transitiveResults
        .headOption
        .getOrElse(Seq.empty)
        .flatMap(_.headOption)
        .flatten
      Seq(pom.toSeq, directDepsPoms)
    }

  val sourcesJarTask = CachedTaskBuilder
    .make[Option[os.Path]](name = "sourcesJar")
    .dependsOn(pomSettingsTask)
    .dependsOn(coreTasks.sourcesTask)
    .build { ctx =>
      val (pomSettingsOpt, sources) = ctx.depResults
      pomSettingsOpt.map { pomSettings =>
        os.makeDir.all(ctx.out)
        val resultJarPath = ctx.out / s"${pomSettings.artifactId}-sources.jar"
        os.remove(resultJarPath)
        os.zip(resultJarPath, sources.map(_.absPath).filter(os.exists(_)))
        resultJarPath
      }
    }

  val javadocJarTask = CachedTaskBuilder
    .make[Option[os.Path]](name = "javadocJar")
    .dependsOn(coreTasks.scalaVersionTask)
    .dependsOn(pomSettingsTask)
    .dependsOn(coreTasks.sourcesTask)
    .dependsOn(coreTasks.compilerDepsTask)
    .dependsOn(coreTasks.compileClasspathTask)
    .dependsOn(coreTasks.compileTask)
    .dependsOn(coreTasks.classesTask)
    .build { ctx =>
      val (scalaVersion, pomSettingsOpt, sources, compilerDeps, compileClasspath, _, classesDir) = ctx.depResults
      pomSettingsOpt.map { pomSettings =>
        os.remove.all(ctx.out)
        os.makeDir.all(ctx.out)
        val generatedDir = ctx.out / "generated"
        os.makeDir.all(generatedDir)
        val sourceFiles = sources
          .map(_.absPath)
          .flatMap { sourceDir =>
            if os.exists(sourceDir) then
              os.walk(
                sourceDir,
                skip = p => {
                  if os.isDir(p) then false
                  else if os.isFile(p) then !(p.ext == "scala" || p.ext == "java")
                  else true
                }
              )
            else Seq.empty
          }
          .filter(os.isFile)
        ctx.module match {
          case module: ScalaModule =>
            if scalaVersion.startsWith("3.") then {
              val tastyFiles =
                if os.exists(classesDir) then
                  os.walk(
                    classesDir,
                    skip = p => {
                      if os.isDir(p) then false
                      else if os.isFile(p) then !(p.ext == "tasty")
                      else true
                    }
                  )
                else Seq.empty
              if tastyFiles.isEmpty then
                throw RuntimeException(s"No .tasty files found in ${classesDir} for generating scaladoc")
              val deps = Seq(Dependency.make(s"org.scala-lang::scaladoc:${scalaVersion}", scalaVersion))
              val depsJars = DependencyResolver.fetchFiles(deps, Some(ctx.notifications))
              ClassLoaderUtils.withClassLoader(depsJars, parent = null) { classLoader =>
                val scaladocClass = classLoader.loadClass("dotty.tools.scaladoc.Main")
                val scaladocMethod = scaladocClass.getMethod("run", classOf[Array[String]])
                val args = Array[String](
                  "-d",
                  generatedDir.toString,
                  "-classpath",
                  compileClasspath.mkString(File.pathSeparator),
                  "--"
                ) ++ tastyFiles.filter(os.isFile(_)).map(_.toString)
                val scaladocObj = scaladocClass.getConstructor().newInstance()
                scaladocMethod.invoke(scaladocObj, args)
              }
            } else {
              val depsJars = DependencyResolver.fetchFiles(compilerDeps, Some(ctx.notifications))
              ClassLoaderUtils.withClassLoader(depsJars, parent = null) { classLoader =>
                val scaladocClass = classLoader.loadClass("scala.tools.nsc.ScalaDoc")
                val scaladocMethod = scaladocClass.getMethod("process", classOf[Array[String]])
                val args = Array[String](
                  "-d",
                  generatedDir.toString,
                  "-classpath",
                  compileClasspath.mkString(File.pathSeparator),
                  "--"
                ) ++ sourceFiles.filter(os.isFile(_)).map(_.toString)
                val scaladocObj = scaladocClass.getConstructor().newInstance()
                scaladocMethod.invoke(scaladocObj, args)
              }
            }
          case _ =>
            val javadocTool = ToolProvider.getSystemDocumentationTool
            val outStream = new java.io.ByteArrayOutputStream()
            val args = Array(
              "-classpath",
              compileClasspath.mkString(File.pathSeparator),
              "-d",
              generatedDir.toString
            ) ++ sourceFiles.filter(_.ext == "java").map(_.toString)
            javadocTool.run(null, outStream, outStream, args*)
        }

        val resultJarPath = ctx.out / s"${pomSettings.artifactId}-javadoc.jar"
        os.remove(resultJarPath)
        os.zip(resultJarPath, Seq(generatedDir))
        resultJarPath
      }
    }

  case class PublishArtifactsRes(
      pom: PomSettings,
      outDir: os.Path
  ) derives JsonRW

  val publishArtifactsTask = CachedTaskBuilder
    .make[Option[PublishArtifactsRes]](
      name = "publishArtifacts"
    )
    .dependsOn(coreTasks.scalaVersionTask)
    .dependsOn(pomSettingsTask)
    .dependsOn(moduleDepsPomSettingsTask)
    .dependsOn(coreTasks.mandatoryDependenciesTask)
    .dependsOn(coreTasks.dependenciesTask)
    .dependsOn(jarTask)
    .dependsOn(sourcesJarTask)
    .dependsOn(javadocJarTask)
    .build { ctx =>
      val (
        scalaVersion,
        pomOpt,
        moduleDepsPomSettings,
        mandatoryDependencies,
        dependencies,
        jar,
        sourcesJarOpt,
        javadocJarOpt
      ) = ctx.depResults
      pomOpt.zip(sourcesJarOpt).zip(javadocJarOpt).map { case ((pom, sourcesJar), javadocJar) =>
        val artifactBaseName = s"${pom.artifactId}-${pom.version}"
        val artifactsDir = ctx.out / "artifacts"
        os.remove.all(artifactsDir)
        os.makeDir.all(artifactsDir)
        os.copy.over(jar, artifactsDir / s"${artifactBaseName}.jar")
        os.copy.over(sourcesJar, artifactsDir / s"${artifactBaseName}-sources.jar")
        os.copy.over(javadocJar, artifactsDir / s"${artifactBaseName}-javadoc.jar")
        val pomSettings = ctx.module match {
          case jm: JavaModule => jm.pomSettings
        }
        val allDependencies = mandatoryDependencies ++ dependencies
        // drop index 0 (self), keep index 1 (direct module deps' pom settings)
        val moduleDepsPomSettingsClean = moduleDepsPomSettings.drop(1).flatten
        val pomXmlContent =
          PomGenerator.generate(
            pom.groupId,
            pom.artifactId,
            pom.version,
            allDependencies,
            pomSettings,
            moduleDepsPomSettingsClean
          )
        val pomXmlPath = artifactsDir / s"${artifactBaseName}.pom"
        os.write.over(pomXmlPath, pomXmlContent)
        PublishArtifactsRes(pom, artifactsDir)
      }
    }

  val publishLocalTask = TaskBuilder
    .make[Option[os.Path]](
      name = "publishLocal"
    )
    .dependsOn(publishArtifactsTask)
    .build { ctx =>
      val publishArtifactsResOpt = ctx.depResults._1
      publishArtifactsResOpt.map { publishArtifactsRes =>
        val pom = publishArtifactsRes.pom
        val artifacts = os.list(publishArtifactsRes.outDir)
        // generate hashes
        val allFiles = artifacts.flatMap { f =>
          Seq(f) ++ Hasher.generateChecksums(f)
        }
        ctx.module match {
          case javaModule: JavaModule =>
            if javaModule.publish then {
              val publisher = Publisher(ctx.notifications, ctx.module.id)
              publisher.publishLocalM2(pom, allFiles)
            } else {
              ctx.notifications.add(
                ServerNotification
                  .logInfo(s"Skipping ${ctx.module.id} publishing because it is disabled", ctx.module.id)
              )
            }
          case _ =>
        }
        ctx.out
      }
    }

  val publishTask = TaskBuilder
    .make[String](
      name = "publish"
    )
    .dependsOn(publishArtifactsTask)
    .build { ctx =>
      val publishArtifactsResOpt = ctx.depResults._1
      publishArtifactsResOpt.map { publishArtifactsRes =>
        val pom = publishArtifactsRes.pom
        val artifacts = os.list(publishArtifactsRes.outDir)
        // sign files
        val pgpPassphrase =
          sys.env.getOrElse("DEDER_PGP_PASSPHRASE", throw RuntimeException("DEDER_PGP_PASSPHRASE env var not set"))
        val pgpSecret =
          sys.env.getOrElse("DEDER_PGP_SECRET", throw RuntimeException("DEDER_PGP_SECRET env var not set"))
        artifacts.foreach(f => PgpSigner.signFile(f, pgpSecret, pgpPassphrase.toCharArray))
        // generate hashes
        val allFiles = artifacts.flatMap { f =>
          val checksumFile = f / os.up / s"${f.last}.asc"
          Seq(f, checksumFile) ++
            Hasher.generateChecksums(f) ++
            Hasher.generateChecksums(checksumFile)
        }
        val username =
          sys.env.getOrElse(
            "DEDER_SONATYPE_USERNAME",
            throw RuntimeException("DEDER_SONATYPE_USERNAME env var not set")
          )
        val password =
          sys.env.getOrElse(
            "DEDER_SONATYPE_PASSWORD",
            throw RuntimeException("DEDER_SONATYPE_PASSWORD env var not set")
          )
        os.remove.all(ctx.out)
        val filesDir =
          ctx.out / "final" / os.SubPath(s"${pom.groupId.replace('.', '/')}/${pom.artifactId}/${pom.version}")
        os.makeDir.all(filesDir)
        allFiles.foreach(f => os.copy(f, filesDir / f.last))
        val filesZip = ctx.out / s"${pom.artifactId}_bundle.zip"
        os.zip(filesZip, Seq(ctx.out / "final"))
        val publisher = Publisher(ctx.notifications, ctx.module.id)
        if pom.version.endsWith("-SNAPSHOT") then publisher.publishSonatypeSnapshot(username, password, pom, allFiles)
        else publisher.publishSonatypeCentral(username, password, pom, filesZip)
      }
      ""
    }

  val all: Seq[Task[?, ?]] = Seq(
    manifestSettingsTask,
    pomSettingsTask,
    finalManifestSettingsTask,
    jarTask,
    allJarsTask,
    assemblyTask,
    moduleDepsPomSettingsTask,
    sourcesJarTask,
    javadocJarTask,
    publishArtifactsTask,
    publishLocalTask,
    publishTask
  )

}
