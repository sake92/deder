package ba.sake.deder.scalanative

import scala.util.Properties

class ScalaNativeTasksSuite extends munit.FunSuite {

  test("findNativeBinary ignores cached metadata files") {
    val tmpDir = os.temp.dir()
    try {
      val metadataFile = tmpDir / "metadata.json"
      val nativeBinaryName = if Properties.isWin then "cli-test.exe" else "cli-test"
      val nativeBinary = tmpDir / nativeBinaryName

      os.write(metadataFile, """{"result":"cached"}""")
      os.write(nativeBinary, "#!/bin/sh\nexit 0\n")
      if !Properties.isWin then
        os.perms.set(nativeBinary, "rwxr-xr-x")

      val resolvedBinary = ScalaNativeTasks.findNativeBinary(tmpDir)

      assertEquals(resolvedBinary, nativeBinary)
    } finally {
      os.remove.all(tmpDir)
    }
  }
}
