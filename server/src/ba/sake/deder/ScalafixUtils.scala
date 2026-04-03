package ba.sake.deder

import java.io.File

object ScalafixUtils {

  val ScalafixVersion = "0.14.6"

  private val ScalaVersionRegex = """(\d+)\.(\d+)\.(\d+).*""".r

  /** Maps user's Scala version to nearest supported scalafix-cli Scala version */
  def scalafixCliScalaVersion(scalaVersion: String): String = scalaVersion match {
    case ScalaVersionRegex(major, minor, _) =>
      if major.toInt == 3 then {
        if minor.toInt >= 8 then "3.8.2"
        else if minor.toInt >= 7 then "3.7.4"
        else if minor.toInt >= 6 then "3.6.4"
        else if minor.toInt >= 5 then "3.5.2"
        else "3.3.7"
      } else if major.toInt == 2 && minor.toInt == 13 then "2.13.18"
      else "2.12.21"
    case _ => "2.13.18"
  }

  /** Builds the scalafix-cli Maven dependency string */
  def scalafixDep(scalaVersion: String): String = {
    val cliScalaVersion = scalafixCliScalaVersion(scalaVersion)
    s"ch.epfl.scala:scalafix-cli_$cliScalaVersion:$ScalafixVersion"
  }

  /** Builds scalafix CLI arguments */
  def buildArgs(
      scalaVersion: String,
      scalacOptions: Seq[String],
      compileClasspath: Seq[os.Path],
      semanticdbDir: os.Path,
      sourcePaths: Seq[String],
      extraArgs: Seq[String]
  ): Seq[String] = {
    val scalafixClasspath = (compileClasspath.appended(semanticdbDir)).mkString(File.pathSeparator)
    val scalacOptionsArgs =
      if scalacOptions.nonEmpty then Seq("--scalac-options", scalacOptions.mkString(","))
      else Seq.empty

    Seq(
      "--sourceroot",
      DederGlobals.projectRootDir.toString,
      "--classpath",
      scalafixClasspath,
      "--scala-version",
      scalaVersion
    ) ++ scalacOptionsArgs ++ sourcePaths ++ extraArgs
  }
}
