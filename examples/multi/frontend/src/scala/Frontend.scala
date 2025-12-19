package frontend

object Frontend {
  val value = "frontend + " + common.Common.value

  def main(args: Array[String]): Unit = {
    println(s"Hello from frontendddd module! Args = ${args.toList}")
  }
}
