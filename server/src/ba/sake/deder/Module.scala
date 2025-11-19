package ba.sake.deder

// just plain data, read from build.pkl
trait Module {
  def tpe: ModuleType
  def id: String
  def moduleDeps: Seq[String] // module ids
}

case class JavaModule(
    id: String,
    sources: Seq[DederPath],
    javacOptions: Seq[String],
    moduleDeps: Seq[String]
) extends Module {
  def tpe: ModuleType = ModuleType.Java
}

case class ScalaModule(id: String, moduleDeps: Seq[String]) extends Module {
  def tpe: ModuleType = ModuleType.Scala
}

case class ScalaJsModule(id: String, moduleDeps: Seq[String]) extends Module {
  def tpe: ModuleType = ModuleType.ScalaJS
}
