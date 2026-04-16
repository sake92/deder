package ba.sake.deder

class HashableSuite extends munit.FunSuite {

  test("non-existent path hashes to empty string") {
    val dir = os.temp.dir()
    os.remove.all(dir)
    assertEquals(Hashable[os.Path].hashStr(dir), "")
  }

  test("file hash is deterministic and content-driven") {
    val dir = os.temp.dir()
    try {
      val f = dir / "f.txt"
      os.write(f, "hello")
      val h1 = Hashable[os.Path].hashStr(f)
      val h2 = Hashable[os.Path].hashStr(f)
      assertEquals(h1, h2, "same content must produce same hash")

      os.write.over(f, "hello world")
      val h3 = Hashable[os.Path].hashStr(f)
      assertNotEquals(h1, h3, "changed content must change hash")
    } finally os.remove.all(dir)
  }

  test("dir hash changes when a file's content changes") {
    val dir = os.temp.dir()
    try {
      os.write(dir / "a.txt", "content-a")
      os.write(dir / "b.txt", "content-b")
      val before = Hashable[os.Path].hashStr(dir)

      os.write.over(dir / "a.txt", "content-a-modified")
      val after = Hashable[os.Path].hashStr(dir)

      assertNotEquals(before, after)
    } finally os.remove.all(dir)
  }

  test("dir hash changes when a file is renamed (sort order preserved)") {
    val dir = os.temp.dir()
    try {
      os.write(dir / "a.txt", "content-a")
      os.write(dir / "b.txt", "content-b")
      val before = Hashable[os.Path].hashStr(dir)

      // Rename a.txt -> aa.txt (still sorts before b.txt). Content unchanged.
      os.move(dir / "a.txt", dir / "aa.txt")
      val after = Hashable[os.Path].hashStr(dir)

      assertNotEquals(before, after, "renaming a file must change the dir hash even when sort order is preserved")
    } finally os.remove.all(dir)
  }

  test("dir hash changes when sibling files swap contents") {
    val dir = os.temp.dir()
    try {
      os.write(dir / "a.txt", "x")
      os.write(dir / "b.txt", "y")
      val before = Hashable[os.Path].hashStr(dir)

      os.write.over(dir / "a.txt", "y")
      os.write.over(dir / "b.txt", "x")
      val after = Hashable[os.Path].hashStr(dir)

      assertNotEquals(before, after, "swapping contents between siblings must change the dir hash")
    } finally os.remove.all(dir)
  }

  test("dir hash changes when a subdirectory is renamed (contents unchanged)") {
    val dir = os.temp.dir()
    try {
      os.makeDir.all(dir / "foo")
      os.write(dir / "foo" / "x.txt", "contents")
      val before = Hashable[os.Path].hashStr(dir)

      // Rename foo -> fop (sole child, sort order trivially preserved). Contents unchanged.
      os.move(dir / "foo", dir / "fop")
      val after = Hashable[os.Path].hashStr(dir)

      assertNotEquals(before, after, "renaming a subdirectory must change the parent dir hash")
    } finally os.remove.all(dir)
  }

}
