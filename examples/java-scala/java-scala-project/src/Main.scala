package ba.sake.deder.javascalaproject

import scala.jdk.CollectionConverters.*

object Main {
  def main(args: Array[String]): Unit = {
    val book = ImmutableBook
      .builder()
      .title("Scala Book")
      .authors(Seq("Author A", "Author B").asJava)
      .isbn("123-4567890123")
      .build()

    println(s"Book: ${book}")
  }
}
