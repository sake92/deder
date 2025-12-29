package frontend

object Frontend {
  val value = "frontend + " + common.Common.value

  def main(args: Array[String]): Unit = {
    println(s"Hello ! ${value}")
  }
}
