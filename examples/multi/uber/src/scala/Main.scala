package uber

import org.jsoup.Jsoup // testing deps

object Main {
  def main(args: Array[String]): Unit = {
    pprint.pprintln(s"Hello from uber module!")
    println(s"Args = ${args.toList}")
    println(frontend.Frontend.value)
    println(backend.Backend.value)

    // org.jsoup.Jsoup.connect("https://example.com").get()

    while ({
      print("Type something: ")
      val line = scala.io.StdIn.readLine()
      println(s"You typed: $line")
      Thread.sleep(1000)
      true
    }) {}

  }
}
