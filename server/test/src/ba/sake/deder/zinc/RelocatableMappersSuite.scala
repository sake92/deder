package ba.sake.deder.zinc

import java.nio.file.Path

class RelocatableMappersSuite extends munit.FunSuite {

  test("relative paths use ASCII markers and follow the relocated root") {
    val original = os.pwd.toNIO.resolve("original")
    val relocated = os.pwd.toNIO.resolve("relocated")
    val write = RelocatableMappers(original).getWriteMapper
    val read = RelocatableMappers(relocated).getReadMapper
    val mappings: Seq[(Path => Path, Path => Path)] = Seq(
      (write.mapOutputDir, read.mapOutputDir),
      (write.mapSourceDir, read.mapSourceDir),
      (write.mapClasspathEntry, read.mapClasspathEntry)
    )
    for
      (encode, decode) <- mappings
      relative <- Seq("", "src/Main.scala", ".deder/out/app/compile/classes", "../dependency.jar")
    do {
      val encoded = encode(original.resolve(relative))
      assert(encoded.toString.forall(_ < 128), s"Non-ASCII path marker: $encoded")
      assertEquals(decode(encoded).normalize(), relocated.resolve(relative).normalize())
    }
  }

}
