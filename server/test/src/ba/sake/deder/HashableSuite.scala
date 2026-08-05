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

  test("DederPath hash changes when a file is renamed (content unchanged)") {
    val root = os.temp.dir()
    System.setProperty("DEDER_PROJECT_ROOT_DIR", root.toString)
    try {
      val a = root / "a.txt"
      os.write(a, "same content")
      val h1 = Hashable[DederPath].hashStr(DederPath("a.txt"))

      // Rename a.txt -> b.txt, content unchanged.
      os.move(a, root / "b.txt")
      val h2 = Hashable[DederPath].hashStr(DederPath("b.txt"))

      assertNotEquals(h1, h2, "renaming a file must change the DederPath hash even when content is unchanged")
    } finally System.clearProperty("DEDER_PROJECT_ROOT_DIR")
  }

}
