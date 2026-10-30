package ba.sake.deder

import scala.util.Properties

object JvmUtils {

  def javaBinary(javaHome: Option[os.Path]): String =
    javaHome.map(home => (home / "bin" / executableName("java")).toString).getOrElse("java")

  def javaBinary(javaHome: Option[os.Path], fallbackToEnvironment: Boolean): String = {
    val environmentJavaHome =
      if fallbackToEnvironment then Option(System.getenv("JAVA_HOME")).filter(_.nonEmpty).map(os.Path(_))
      else None
    val selectedJavaHome = javaHome.orElse(environmentJavaHome)
    javaBinary(selectedJavaHome)
  }

  def jshellBinary(javaHome: Option[os.Path]): String =
    javaHome.map(home => (home / "bin" / executableName("jshell")).toString).getOrElse("jshell")

  private def executableName(name: String): String =
    if Properties.isWin then s"$name.exe" else name
}
