package tagsprobe

import java.nio.file.{Files, Path, StandardOpenOption}

class TagsSuite extends munit.FunSuite {

  private def mark(name: String): Unit = {
    Files.writeString(Path.of("markers.txt"), s"$name\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    ()
  }

  test("fast") {
    mark("FAST")
  }

  test("slow".tag(new munit.Tag("Slow"))) {
    mark("SLOW")
  }
}
