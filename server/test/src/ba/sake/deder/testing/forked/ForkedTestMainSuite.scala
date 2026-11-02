package ba.sake.deder.testing.forked

class ForkedTestMainSuite extends munit.FunSuite {

  test("forked runtime preserves OS detection after shading") {
    assertEquals(scala.util.Properties.osName, System.getProperty("os.name"))
    assertEquals(scala.util.Properties.isWin, java.io.File.separatorChar == '\\')
  }

  test("cancelFile existence check works correctly") {
    // ForkedTestMain uses: () => cancelFile.nonEmpty && os.exists(cancelFilePath)
    // Test the logic inline.
    val tmpDir = os.temp.dir()
    try {
      val cancelFilePath = tmpDir / "cancel"

      // Before file exists: callback returns false
      val check1 = () => cancelFilePath.toString.nonEmpty && os.exists(cancelFilePath)
      assert(!check1())

      // After creating file: callback returns true
      os.write(cancelFilePath, "")
      assert(check1())

      // After deleting file: callback returns false again
      os.remove(cancelFilePath)
      assert(!check1())
    } finally {
      os.remove.all(tmpDir)
    }
  }
}
