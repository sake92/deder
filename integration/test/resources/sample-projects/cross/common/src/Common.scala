
object Common {
  lazy val commonValue: String = {
    println("Initializing commonValue")
    "This is a common value"
  }

  def main(args: Array[String]): Unit = {
    println(s"Running Common... commonValue: ${commonValue}")
  }

}