package ba.sake.deder.publish

import ba.sake.deder.config.DederProject.PomSettings as PklPomSettings

object PublishValidator {

  def validateForSonatypeCentral(
    moduleId: String,
    pomSettings: PklPomSettings,
    resolvedVersion: String
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

    if violations.nonEmpty then
      val message = new StringBuilder()
      message.append(s"Invalid POM settings for module '$moduleId' when publishing to Sonatype Central:\n")
      violations.foreach(v => message.append(s"  - $v\n"))
      message.append(s"\nSee https://central.sonatype.org/publish/requirements for more information.")
      throw RuntimeException(message.toString())
  }

  private def isNullOrEmpty(s: String): Boolean = s == null || s.isEmpty
}
