package ba.sake.deder.deps

import dependency.parser.DependencyParser
import dependency.Dependency as CoursierDependency
import dependency.ScalaParameters
import dependency.AnyDependency
import ba.sake.tupson.JsonRW
import org.typelevel.jawn.ast.JValue
import org.typelevel.jawn.ast.JNull

case class Dependency(
    coursierDep: AnyDependency,
    scalaParameters: ScalaParameters
) {
  def applied: CoursierDependency =
    coursierDep.applyParams(scalaParameters)
}

object Dependency {
  def make(declaration: String, scalaVersion: String, platform: Option[String] = None): Dependency =
    val coursierDep = DependencyParser
      .parse(declaration)
      .toOption
      .getOrElse(
        throw new IllegalArgumentException(
          s"Invalid dependency declaration '${declaration}' for scalaVersion='${scalaVersion}'"
        )
      )
    val scalaParameters = ScalaParameters(scalaVersion).copy(platform = platform)
    Dependency(coursierDep, scalaParameters)

  given JsonRW[Dependency] with {
    def parse(path: String, jValue: JValue): Dependency =
      val map = JsonRW[Map[String, JValue]].parse(path, jValue)
      val coursierDepStr = JsonRW[String].parse("coursierDep", map.getOrElse("coursierDep", JNull))
      val scalaVersion = JsonRW[String].parse("scalaVersion", map.getOrElse("scalaVersion", JNull))
      val platform = JsonRW[Option[String]].parse("platform", map.getOrElse("platform", JNull))
      make(coursierDepStr, scalaVersion, platform)

    def write(value: Dependency): JValue =
      JsonRW[Map[String, JValue]].write(
        Map(
          "coursierDep" -> JsonRW[String].write(value.coursierDep.toString),
          "scalaVersion" -> JsonRW[String].write(value.scalaParameters.scalaVersion),
          "platform" -> JsonRW[Option[String]].write(value.scalaParameters.platform)
        )
      )
  }
}
