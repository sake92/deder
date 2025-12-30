package uber

object Main {
  def main(args: Array[String]): Unit = {
    println(s"Args = ${args.toList.mkString(", ")}")
    println(frontend.Frontend.value)
    println(backend.Backend.value)
    Thread.sleep(1000) // simulate some work
  }
}
