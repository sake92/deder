package ba.sake.deder.publish

import java.io.StringReader
import scala.jdk.CollectionConverters.*
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import ba.sake.deder.config.DederProject.PomSettings as PklPomSettings

object PublishValidator {

  def validateForSonatypeCentral(
      moduleId: String,
      pomSettings: PklPomSettings,
      resolvedVersion: String
  ): Unit =
    validateForSonatypeCentral(moduleId, pomSettings, resolvedVersion, Seq.empty)

  def validateForSonatypeCentral(
      moduleId: String,
      pomSettings: PklPomSettings,
      resolvedVersion: String,
      pomFile: os.Path
  ): Unit = {
    val reader = new MavenXpp3Reader()
    val pom = reader.read(new StringReader(os.read(pomFile)))
    validateForSonatypeCentral(moduleId, pomSettings, resolvedVersion, pom.getDependencies.asScala.toSeq)
  }

  def validateForSonatypeCentral(
      moduleId: String,
      pomSettings: PklPomSettings,
      resolvedVersion: String,
      dependencies: Seq[org.apache.maven.model.Dependency]
  ): Unit = {
    if pomSettings == null then
      throw RuntimeException(
        s"Invalid POM settings for module '$moduleId' when publishing to Sonatype Central:\n" +
        s"  - pomSettings is required\n\n" +
        s"See https://central.sonatype.org/publish/requirements for more information."
      )

    val violations = scala.collection.mutable.ListBuffer.empty[String]

    if isNullOrEmpty(pomSettings.groupId) then
      violations += "groupId is required but not set"

    if resolvedVersion == null || resolvedVersion.isEmpty then
      violations += "version is required but not set"
    else if resolvedVersion.endsWith("-SNAPSHOT") then
      violations += "version must not end with -SNAPSHOT"

    if isNullOrEmpty(pomSettings.description) then
      violations += "description is required but not set"

    if isNullOrEmpty(pomSettings.url) then
      violations += "url is required but not set"

    if pomSettings.licenses == null || pomSettings.licenses.isEmpty then
      violations += "licenses must contain at least one entry"

    if pomSettings.developers == null || pomSettings.developers.isEmpty then
      violations += "developers must contain at least one entry"

    if pomSettings.scm == null then
      violations += "scm.url is required but not set"
      violations += "scm.connection is required but not set"
    else
      if isNullOrEmpty(pomSettings.scm.url) then
        violations += "scm.url is required but not set"
      if isNullOrEmpty(pomSettings.scm.connection) then
        violations += "scm.connection is required but not set"

    if !isNullOrEmpty(resolvedVersion) && !resolvedVersion.endsWith("-SNAPSHOT") then
      val snapshotDependencies = dependencies
        .filter(dependency => isSnapshotVersion(dependency.getVersion))
        .map(dependency => s"${dependency.getGroupId}:${dependency.getArtifactId}:${dependency.getVersion}")
        .distinct
        .sorted
      snapshotDependencies.foreach { coordinate =>
        violations += s"Dependencies to SNAPSHOT versions not allowed: '$coordinate'"
      }

    if violations.nonEmpty then
      val message = new StringBuilder()
      message.append(s"Invalid POM settings for module '$moduleId' when publishing to Sonatype Central:\n")
      violations.foreach(v => message.append(s"  - $v\n"))
      message.append(s"\nSee https://central.sonatype.org/publish/requirements for more information.")
      throw RuntimeException(message.toString())
  }

  private def isSnapshotVersion(version: String): Boolean =
    version != null && version.endsWith("-SNAPSHOT")

  private def isNullOrEmpty(s: String): Boolean = s == null || s.isEmpty
}
