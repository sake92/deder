package uber

object Main extends App {
  println(s"Hello from uber module!")
  println(frontend.Frontend.value)
  println(backend.Backend.value)

  org.jsoup.Jsoup.connect("https://example.com").get()

  while ({
    print("Type something: ")
    val line = scala.io.StdIn.readLine()
    println(s"You typed: $line")
    true
  }) {}
}
