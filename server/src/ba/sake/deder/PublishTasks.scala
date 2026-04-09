package ba.sake.deder

import ba.sake.tupson.JsonRW
import ba.sake.deder.publish.{Hasher, PgpSigner, PomGenerator, PomSettings, Publisher}
import ba.sake.deder.config.DederProject.JavaModule

class PublishTasks(packagingTasks: PackagingTasks) {

  private val coreTasks = packagingTasks.coreTasks

  case class PublishArtifactsRes(
      pom: PomSettings,
      outDir: os.Path
  ) derives JsonRW

  val publishArtifactsTask = CachedTaskBuilder
    .make[Option[PublishArtifactsRes]](
      name = "publishArtifacts"
    )
    .dependsOn(coreTasks.scalaVersionTask)
    .dependsOn(packagingTasks.pomSettingsTask)
    .dependsOn(packagingTasks.moduleDepsPomSettingsTask)
    .dependsOn(coreTasks.mandatoryDependenciesTask)
    .dependsOn(coreTasks.dependenciesTask)
    .dependsOn(packagingTasks.jarTask)
    .dependsOn(packagingTasks.sourcesJarTask)
    .dependsOn(packagingTasks.javadocJarTask)
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
        os.remove.all(ctx.out)
        os.makeDir.all(ctx.out)
        os.copy.over(jar, ctx.out / s"${artifactBaseName}.jar")
        os.copy.over(sourcesJar, ctx.out / s"${artifactBaseName}-sources.jar")
        os.copy.over(javadocJar, ctx.out / s"${artifactBaseName}-javadoc.jar")
        val pomSettings = ctx.module match {
          case jm: JavaModule => jm.pomSettings
        }
        val allDependencies = mandatoryDependencies ++ dependencies
        // drop this module's pom settings
        // then take just the first level, direct module dependencies
        val moduleDepsPomSettingsClean = moduleDepsPomSettings.take(2).drop(1).flatten
        val pomXmlContent =
          PomGenerator.generate(
            pom.groupId,
            pom.artifactId,
            pom.version,
            allDependencies,
            pomSettings,
            moduleDepsPomSettingsClean
          )
        val pomXmlPath = ctx.out / s"${artifactBaseName}.pom"
        os.write.over(pomXmlPath, pomXmlContent)
        PublishArtifactsRes(pom, ctx.out)
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
    publishArtifactsTask,
    publishLocalTask,
    publishTask
  )

}
