package bla

// just plain data, read from build.pkl
trait Module {
  def tpe: String
  def id: String
  def moduleDeps: Seq[String] // module ids
}

case class JavaModule(
    id: String,
    moduleDeps: Seq[String]
) extends Module {
  def tpe: String = "java"
}

case class ScalaModule(id: String, moduleDeps: Seq[String]) extends Module {
  def tpe: String = "scala"
}

case class ScalaJsModule(id: String, moduleDeps: Seq[String]) extends Module {
  def tpe: String = "scalajs"
}
