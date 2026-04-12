package ba.sake.deder.scalanative

class ScalaNativeTasksSuite extends munit.FunSuite {

  test("findNativeBinary ignores cached metadata files") {
    val tmpDir = os.temp.dir()
    try {
      val metadataFile = tmpDir / "metadata.json"
      val nativeBinary = tmpDir / "cli-test"

      os.write(metadataFile, """{"result":"cached"}""")
      os.write(nativeBinary, "#!/bin/sh\nexit 0\n")
      os.perms.set(nativeBinary, "rwxr-xr-x")

      val resolvedBinary = ScalaNativeTasks.findNativeBinary(tmpDir)

      assertEquals(resolvedBinary, nativeBinary)
    } finally {
      os.remove.all(tmpDir)
    }
  }
}
