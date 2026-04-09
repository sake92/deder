package ba.sake.deder

import javax.tools.ToolProvider
import java.io.File
import scala.jdk.CollectionConverters.*
import dependency.ScalaVersion
import ba.sake.tupson.JsonRW
import ba.sake.deder.config.DederProject.{
  JavaModule,
  ModuleType,
  ScalaJsModule,
  ScalaNativeModule,
  ScalaModule
}
import ba.sake.deder.config.DederProject
import ba.sake.deder.deps.Dependency
import ba.sake.deder.deps.DependencyResolver
import ba.sake.deder.deps.given
import ba.sake.deder.jar.{JarManifest, JarUtils}
import ba.sake.deder.publish.{GitSemVer, PomGenerator, PomSettings}

class PackagingTasks(val coreTasks: CoreTasks) {

  case class ManifestEntries(
      mainAttributes: Map[String, String],
      groups: Map[String, Map[String, String]]
  ) derives JsonRW {
    def toJarManifest: JarManifest = {
      val base = JarManifest.Default.add(mainAttributes.toSeq*)
      groups.foldLeft(base) { case (m, (group, attrs)) =>
        m.addGroup(group, attrs.toSeq*)
      }
    }
  }

  object ManifestEntries {
    val Empty: ManifestEntries = ManifestEntries(Map.empty, Map.empty)

    given Hashable[ManifestEntries] with {
      override def hashStr(value: ManifestEntries): String =
        Seq(value.mainAttributes.hashStr, value.groups.hashStr).hashStr
    }
  }

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
      Seq(pom.toSeq) ++ ctx.transitiveResults.flatten.flatten
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

  val all: Seq[Task[?, ?]] = Seq(
    manifestSettingsTask,
    pomSettingsTask,
    finalManifestSettingsTask,
    jarTask,
    allJarsTask,
    assemblyTask,
    moduleDepsPomSettingsTask,
    sourcesJarTask,
    javadocJarTask
  )

}
