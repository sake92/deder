package ba.sake.deder.graalvm

import java.io.File
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.util.zip.ZipFile
import scala.util.{Properties, Using}
import scala.jdk.CollectionConverters.*
import com.typesafe.scalalogging.StrictLogging
import ba.sake.tupson.{*, given}
import org.typelevel.jawn.ast.*
import ba.sake.deder.config.DederProject.{JavaModule, ModuleType}
import ba.sake.deder.{*, given}

class GraalVmNativeImageTasks(coreTasks: CoreTasks) extends StrictLogging {

  private val REACHABILITY_METADATA_VERSION = "0.3.32"

  val graalvmHomeTask = ConfigValueTask[Option[os.Path]](
    name = "graalvmHome",
    supportedModuleTypes = Set(ModuleType.JAVA, ModuleType.SCALA),
    execute = { ctx =>
      ctx.module match {
        case m: JavaModule if m.graalvm != null =>
          Option(m.graalvm.graalvmHome)
            .map(os.Path(_))
            .orElse(sys.env.get("GRAALVM_HOME").map(os.Path(_)))
        case _ => None
      }
    }
  )

  val nativeImageOptionsTask = ConfigValueTask[Seq[String]](
    name = "nativeImageOptions",
    supportedModuleTypes = Set(ModuleType.JAVA, ModuleType.SCALA),
    execute = { ctx =>
      ctx.module match {
        case m: JavaModule if m.graalvm != null => m.graalvm.nativeImageOptions.asScala.toSeq
        case _                                  => Seq.empty
      }
    }
  )

  val nativeIncludedResourcesOptionsTask = CachedTaskBuilder
    .make[Seq[String]](
      name = "nativeIncludedResourcesOptions",
      supportedModuleTypes = Set(ModuleType.JAVA, ModuleType.SCALA)
    )
    .dependsOn(coreTasks.resourcesTask)
    .build { ctx =>
      val resourceDirs = ctx.depResults._1
      val resourceFiles = resourceDirs
        .map(_.absPath)
        .filter(os.isDir(_))
        .flatMap { dir =>
          os.walk(dir)
            .filter(os.isFile(_))
            .map(_.subRelativeTo(dir).toString)
            .filterNot(_.startsWith("META-INF/native-image"))
        }
      if resourceFiles.isEmpty then Seq.empty
      else Seq(s"-H:IncludeResources=${resourceFiles.mkString("|")}")
    }

  private case class ReachabilityMetadataIndexEntry(
      module: String,
      directory: Option[String] = None,
      requires: Seq[String] = Nil
  ) derives JsonRW

  val graalvmReachabilityMetadataOptionsTask = CachedTaskBuilder
    .make[Seq[String]](
      name = "graalvmReachabilityMetadataOptions",
      supportedModuleTypes = Set(ModuleType.JAVA, ModuleType.SCALA)
    )
    .dependsOn(coreTasks.allDependenciesTask)
    .build { ctx =>
      val allDeps = ctx.depResults._1

      // Download ZIP from GitHub releases (cached inside ctx.out by CachedTask)
      val metadataZip = ctx.out / "graalvm-reachability-metadata.zip"
      if !os.exists(metadataZip) then
        val url =
          s"https://github.com/oracle/graalvm-reachability-metadata/releases/download/$REACHABILITY_METADATA_VERSION/graalvm-reachability-metadata-$REACHABILITY_METADATA_VERSION.zip"
        logger.info(s"Downloading GraalVM reachability metadata from $url")
        os.makeDir.all(ctx.out)
        val httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
        val request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(metadataZip.toNIO))
        if response.statusCode() != 200 then
          os.remove(metadataZip)
          throw DederException(s"Failed to download GraalVM reachability metadata: HTTP ${response.statusCode()}")

      // Root index: array of { "module": "g:a", "directory": "g/a", "requires": [...] }
      val rootEntries = readStringFromZip(metadataZip, "index.json")
        .parseJson[Seq[ReachabilityMetadataIndexEntry]]
      val coordToDirectory: Map[String, String] = rootEntries.flatMap { entry =>
        entry.directory.map(dir => entry.module -> dir)
      }.toMap
      val coordToRequires: Map[String, Seq[String]] =
        rootEntries.map(e => e.module -> e.requires).toMap

      // Resolve all deps with Coursier to capture transitive coordinates too
      val resolvedCoords: Seq[(String, String, String)] =
        ctx.dependencyResolver.resolveTransitiveCoordinates(allDeps, Some(ctx.notifications))

      // Finds the config dir for a module+version: tries exact version match, falls back to latest.
      // Required modules (from `requires`) don't have a version, so pass "" to always fall back to latest.
      def configDirFor(moduleCoord: String, version: String): Option[os.Path] =
        coordToDirectory.get(moduleCoord).flatMap { directory =>
          val versionEntries = readStringFromZip(metadataZip, s"$directory/index.json")
            .parseJson[Seq[Map[String, JValue]]]
          val matchingEntry = versionEntries
            .find { entry =>
              JsonRW[Seq[String]]
                .parse("tested-versions", entry.getOrElse("tested-versions", JNull))
                .contains(version)
            }
            .orElse(versionEntries.find { entry =>
              JsonRW[Option[Boolean]]
                .parse("latest", entry.getOrElse("latest", JNull))
                .getOrElse(false)
            })
          matchingEntry.map { entry =>
            val metadataVer = JsonRW[String].parse("metadata-version", entry.getOrElse("metadata-version", JNull))
            val prefix  = s"$directory/$metadataVer/"
            val destDir = ctx.out / os.RelPath(directory) / os.RelPath(metadataVer)
            extractDirFromZip(metadataZip, prefix, destDir)
            destDir
          }
        }

      val directDirs = resolvedCoords.flatMap { (org, name, version) =>
        configDirFor(s"$org:$name", version)
      }

      // For each matched module, also include metadata for its `requires` (version-less, use latest)
      val requiredDirs = resolvedCoords.flatMap { (org, name, _) =>
        coordToRequires.getOrElse(s"$org:$name", Nil).flatMap { reqCoord =>
          configDirFor(reqCoord, "")
        }
      }

      (directDirs ++ requiredDirs).distinct
        .map(dir => s"-H:ConfigurationFileDirectories=$dir")
    }

  val graalvmNativeImageTask = CachedTaskBuilder
    .make[os.Path](
      name = "graalvmNativeImage",
      supportedModuleTypes = Set(ModuleType.JAVA, ModuleType.SCALA)
    )
    .dependsOn(coreTasks.runClasspathTask)
    .dependsOn(coreTasks.finalMainClassTask)
    .dependsOn(graalvmHomeTask)
    .dependsOn(nativeImageOptionsTask)
    .dependsOn(nativeIncludedResourcesOptionsTask)
    .dependsOn(graalvmReachabilityMetadataOptionsTask)
    .build { ctx =>
      val (
        runClasspath,
        finalMainClass,
        graalvmHome,
        nativeImageOptions,
        includedResourcesOptions,
        reachabilityMetadataOptions
      ) = ctx.depResults

      ctx.module match {
        case m: JavaModule if m.graalvm == null =>
          throw DederException(
            s"Module '${ctx.module.id}' does not have a 'graalvm' section in deder.pkl. " +
              "Add a 'graalvm { }' block to enable the graalvmNativeImage task."
          )
        case _ =>
      }

      val nativeImageTool = graalvmHome match {
        case Some(home) =>
          val toolName = if Properties.isWin then "native-image.cmd" else "native-image"
          val toolPath = home / "bin" / toolName
          if !os.exists(toolPath) then throw DederException(s"native-image tool not found at: $toolPath")
          toolPath
        case None =>
          throw DederException(
            "GraalVM home not configured. Set 'graalvm.graalvmHome' in deder.pkl " +
              "or set the GRAALVM_HOME environment variable (and restart the server)."
          )
      }

      val mainClass = finalMainClass.getOrElse(
        throw DederException(
          s"No main class found for module '${ctx.module.id}'. " +
            "Set 'mainClass' in deder.pkl."
        )
      )

      val executableName = "native-executable"
      val cp = runClasspath.map(_.toString).mkString(File.pathSeparator)
      val command = Seq(nativeImageTool.toString) ++
        nativeImageOptions ++
        reachabilityMetadataOptions ++
        includedResourcesOptions ++
        Seq("-cp", cp, mainClass, (ctx.out / executableName).toString)

      ctx.notifications.add(
        ServerNotification.logInfo(s"Running native-image for module '${ctx.module.id}'", Some(ctx.module.id))
      )

      val outputHandler = os.ProcessOutput.Readlines { line =>
        ctx.notifications.add(ServerNotification.logInfo(line, Some(ctx.module.id)))
      }
      os.makeDir.all(ctx.out)
      logger.info(s"Executing command: ${command.mkString(" ")}")
      os.proc(command).call(cwd = ctx.out, stdout = outputHandler, stderr = outputHandler)

      val ext = if Properties.isWin then ".exe" else ""
      val executable = ctx.out / s"${executableName}${ext}"
      if !os.exists(executable) then throw DederException(s"native-image produced no executable at: $executable")
      executable
    }

  val all: Seq[Task[?, ?]] = Seq(
    graalvmHomeTask,
    nativeImageOptionsTask,
    nativeIncludedResourcesOptionsTask,
    graalvmReachabilityMetadataOptionsTask,
    graalvmNativeImageTask
  )

  // Reads a single ZIP/JAR entry as a UTF-8 string.
  private def readStringFromZip(zipPath: os.Path, entryPath: String): String =
    Using.resource(new ZipFile(zipPath.toIO)) { zf =>
      val entry = zf.getEntry(entryPath)
      if entry == null then throw DederException(s"Entry '$entryPath' not found in $zipPath")
      new String(zf.getInputStream(entry).readAllBytes(), "UTF-8")
    }

  // Extracts all files under `prefix` inside the ZIP to `dest`. No-op if `dest` already exists.
  private def extractDirFromZip(zipPath: os.Path, prefix: String, dest: os.Path): Unit =
    if os.exists(dest) then return
    os.makeDir.all(dest)
    Using.resource(new ZipFile(zipPath.toIO)) { zf =>
      zf.entries()
        .asScala
        .filter(e => !e.isDirectory && e.getName.startsWith(prefix))
        .foreach { entry =>
          val relName = entry.getName.stripPrefix(prefix)
          if relName.nonEmpty then
            val target = dest / os.SubPath(relName)
            os.makeDir.all(target / os.up)
            os.write(target, zf.getInputStream(entry).readAllBytes())
        }
    }
}
