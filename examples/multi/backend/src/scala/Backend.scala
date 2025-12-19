package backend

object Backend {
  val value = "backend + " + common.Common.value

  def main(args: Array[String]): Unit = {
    println(s"Hello from backend module! Args = ${args.toList}")
  }
}
