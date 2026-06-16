package ba.sake.deder

class ArgfileSuite extends munit.FunSuite {

  test("write creates file with expected content when jvmOptions are non-empty") {
    val dir = os.temp.dir()
    val file = Argfile.write(dir, "run", Seq("-Dfoo=bar", "-Xmx2g"), "/a.jar:/b.jar")

    assert(file.ext == "txt")
    assert(file.last.contains("run-jvm-opts"), s"filename should contain 'run-jvm-opts'")

    val content = os.read(file)
    val expected =
      """|-Dfoo=bar
         |-Xmx2g
         |-cp
         |/a.jar:/b.jar""".stripMargin
    assertEquals(content, expected)
  }

  test("write creates file with expected content when jvmOptions are empty") {
    val dir = os.temp.dir()
    val file = Argfile.write(dir, "scalafix", Seq.empty, "/x.jar:/y.jar")

    val content = os.read(file)
    val expected =
      """|-cp
         |/x.jar:/y.jar""".stripMargin
    assertEquals(content, expected)
  }

  test("write rejects invalid keys") {
    val dir = os.temp.dir()
    intercept[IllegalArgumentException] {
      Argfile.write(dir, "my key", Seq.empty, "/cp")
    }
    intercept[IllegalArgumentException] {
      Argfile.write(dir, "", Seq.empty, "/cp")
    }
  }

  test("write creates parent directories if needed") {
    val parent = os.temp.dir()
    val dir = parent / "nested" / "dir"
    val file = Argfile.write(dir, "run", Seq.empty, "/cp")

    assert(os.exists(file))
  }
}
