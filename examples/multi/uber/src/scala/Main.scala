package uber

import scala.jdk.CollectionConverters._
import org.jsoup.Jsoup

object Main {
  def main(args: Array[String]): Unit = {
    pprint.pprintln(s"Hello from uber module!")
    println(s"Args = ${args.toList}")
    println(frontend.Frontend.value)
    println(backend.Backend.value)

    println(s"ENV VARS: ${System.getenv().asScala.toMap.filterKeys(_.startsWith("MY_")).toMap}")

    // org.jsoup.Jsoup.connect("https://example.com").get()

    while ({
      print("Type something: ")
      val line = scala.io.StdIn.readLine()
      println(s"You said: $line")
      true
    }) {}

  }
}
