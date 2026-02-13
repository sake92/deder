package ba.sake.deder.zinc

import dependency.ScalaParameters

case class JdkCompat(jdkVersion: Int, scalaVersion: String)

object JdkUtils {
  val docsUrl = "https://docs.scala-lang.org/overviews/jdk-compatibility/overview.html"
  private val minimumVersions = Seq(
    // 2.12
    JdkCompat(8, "2.12.0"),
    JdkCompat(11, "2.12.4"),
    JdkCompat(17, "2.12.15"),
    JdkCompat(21, "2.12.18"),
    JdkCompat(25, "2.12.21"),
    // 2.13
    JdkCompat(8, "2.13.0"),
    JdkCompat(17, "2.13.6"),
    JdkCompat(21, "2.13.11"),
    JdkCompat(25, "2.13.17"),
    JdkCompat(26, "2.13.18"),
    // 3
    JdkCompat(8, "3.0.0"),
    JdkCompat(11, "3.3.0"),
    JdkCompat(21, "3.3.1"),
    JdkCompat(25, "3.3.7"),
    JdkCompat(26, "3.3.8")
  ).sortBy(-_.jdkVersion)

  def getVersion(javaSpecVersion: String): Int = {
    if javaSpecVersion.startsWith("1.") then javaSpecVersion.split(".")(1).toInt
    else javaSpecVersion.toInt
  }

  def checkCompat(jdkVersion: Int, scalaVersion: String): Unit = {
    val binaryVersion = ScalaParameters(scalaVersion).scalaBinaryVersion
    val relevantChecks = minimumVersions.filter(_.scalaVersion.startsWith(binaryVersion))
    relevantChecks.find(_.jdkVersion <= jdkVersion) match {
      case Some(jdkCompat) =>
        val minorVersion = scalaVersion.stripPrefix(binaryVersion)
        val minMinorVersion = jdkCompat.scalaVersion.stripPrefix(binaryVersion)
        if minorVersion < minMinorVersion then
          throw RuntimeException(
            s"Minimum scalaVersion required for JDK ${jdkVersion} is ${jdkCompat.scalaVersion} but you are using ${scalaVersion}\n" +
              s"Check docs at ${docsUrl}"
          )
      case None =>
        throw RuntimeException(
          s"No minimum JDK specified for scalaVersion=${scalaVersion} and jdkVersion=${jdkVersion}"
        )
    }
  }

}

/*
INSTEAD OF THIS SCARY ERROR you get a nice, friendly error :)

[error] Compilation failed: error while loading AccessFlag,
class file /modules/java.base/java/lang/reflect/AccessFlag.class is broken, reading aborted with class java.lang.RuntimeException
bad constant pool index: 0 at pos: 5189
error while loading ElementType,
class file /modules/java.base/java/lang/annotation/ElementType.class is broken, reading aborted with class java.lang.RuntimeException
bad constant pool index: 0 at pos: 1220
[error] Compilation failed
 */
