package ba.sake.deder

class DederCleanerSuite extends munit.FunSuite {
  val tmpDir = os.temp.dir(prefix = "deder-cleaner-test")

  override def afterAll(): Unit =
    os.remove.all(tmpDir)

  test("scanSize returns total bytes of files in directory") {
    val dir = tmpDir / "scan-test"
    os.makeDir.all(dir)
    os.write(dir / "a.txt", "hello")   // 5 bytes
    os.write(dir / "b.txt", "world!")  // 6 bytes

    val size = DederCleaner.scanSize(dir)
    assertEquals(size, 11L)
  }

  test("scanSize returns 0 for non-existent directory") {
    val dir = tmpDir / "nope"
    val size = DederCleaner.scanSize(dir)
    assertEquals(size, 0L)
  }

  test("scanSize returns 0 for empty directory") {
    val dir = tmpDir / "empty"
    os.makeDir.all(dir)
    val size = DederCleaner.scanSize(dir)
    assertEquals(size, 0L)
  }

  test("scanSize counts nested files") {
    val dir = tmpDir / "nested"
    os.makeDir.all(dir / "sub")
    os.write(dir / "top.txt", "hi")        // 2 bytes
    os.write(dir / "sub" / "deep.txt", "a") // 1 byte

    val size = DederCleaner.scanSize(dir)
    assertEquals(size, 3L)
  }

  test("cleanDir deletes directory and returns bytes freed") {
    val dir = tmpDir / "clean-dir-test"
    os.makeDir.all(dir)
    os.write(dir / "x.txt", "abcdef")  // 6 bytes

    val size = DederCleaner.cleanDir(dir)
    assertEquals(size, 6L)
    assert(!os.exists(dir), "directory should be deleted")
  }

  test("cleanDir returns 0 and succeeds when directory doesn't exist (already clean)") {
    val dir = tmpDir / "never-existed"
    val size = DederCleaner.cleanDir(dir)
    assertEquals(size, 0L)
    assert(!os.exists(dir))
  }

  test("humanReadable formats bytes correctly") {
    assertEquals(DederCleaner.humanReadable(0L), "0 B")
    assertEquals(DederCleaner.humanReadable(500L), "500 B")
    assertEquals(DederCleaner.humanReadable(1000L), "1 KB")
    assertEquals(DederCleaner.humanReadable(1500L), "2 KB")
    assertEquals(DederCleaner.humanReadable(1_000_000L), "1.0 MB")
    assertEquals(DederCleaner.humanReadable(8_100_000L), "8.1 MB")
    assertEquals(DederCleaner.humanReadable(12_400_000L), "12.4 MB")
    assertEquals(DederCleaner.humanReadable(999_000_000L), "999.0 MB")
    assertEquals(DederCleaner.humanReadable(1_000_000_000L), "1.0 GB")
    assertEquals(DederCleaner.humanReadable(2_500_000_000L), "2.5 GB")
  }
}
