package ba.sake.deder.zinc

import java.nio.file.Path

class RelocatableMappersSuite extends munit.FunSuite {

  test("absolute external analysis paths remain unchanged on read") {
    val root = os.pwd.toNIO.resolve("project")
    val external = os.pwd.toNIO.resolve("dependency.jar")
    val read = RelocatableMappers(root).getReadMapper
    assertEquals(read.mapClasspathEntry(external), external)
    assertEquals(read.mapOutputDir(external), external)
    assertEquals(read.mapSourceDir(external), external)
  }

  private val crossDriveTest = {
    val options = new munit.TestOptions("Windows analysis paths on another drive remain absolute")
    if java.io.File.separatorChar == '\\' then options else options.ignore
  }

  test(crossDriveTest) {
    val write = RelocatableMappers(Path.of("D:/a/deder/deder")).getWriteMapper
    val read = RelocatableMappers(Path.of("E:/relocated/deder")).getReadMapper
    val external = Path.of("C:/Users/runner/.cache/coursier/dependency.jar")
    val mappings: Seq[(Path => Path, Path => Path)] = Seq(
      (write.mapOutputDir, read.mapOutputDir),
      (write.mapSourceDir, read.mapSourceDir),
      (write.mapClasspathEntry, read.mapClasspathEntry)
    )
    for (encode, decode) <- mappings do {
      assertEquals(encode(external), external)
      assertEquals(decode(encode(external)), external)
    }
  }

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
