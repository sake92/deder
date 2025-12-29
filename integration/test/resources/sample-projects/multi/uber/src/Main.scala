package uber

import org.jsoup.Jsoup // testing deps

object Main {
  def main(args: Array[String]): Unit = {
    pprint.pprintln(s"Hello from uber module!")
    println(s"Args = ${args.toList.mkString(", ")}")
    println(frontend.Frontend.value)
    println(backend.Backend.value)
    Thread.sleep(1000) // simulate some work
  }
}
