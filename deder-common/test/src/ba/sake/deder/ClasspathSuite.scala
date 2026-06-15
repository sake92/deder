package ba.sake.deder

class ClasspathSuite extends munit.FunSuite {
  private def p(s: String) = os.Path(s)

  test("++ concatenates entries") {
    val cp = Classpath(Seq(p("/a"))) ++ Classpath(Seq(p("/b")))
    assertEquals(cp.entries, Seq(p("/a"), p("/b")))
  }

  test("++ dedups keeping the LAST occurrence (shadowing semantics preserved)") {
    val cp = Classpath(Seq(p("/a"), p("/b"))) ++ Classpath(Seq(p("/a"), p("/c")))
    assertEquals(cp.entries, Seq(p("/b"), p("/a"), p("/c")))
  }

  test("diamond: same dep via two paths appears once, at its last position") {
    val left = Classpath(Seq(p("/own"), p("/A"), p("/shared")))
    val right = Classpath(Seq(p("/B"), p("/shared")))
    assertEquals((left ++ right).entries, Seq(p("/own"), p("/A"), p("/B"), p("/shared")))
  }

  test("a dep that is both direct and transitive ends up LAST, not first") {
    // a -> {b, c, d} and b -> d, c -> d : d appears early (direct) and late (via b/c);
    // keep-last pushes the shared foundation d to the end.
    val cp = Classpath(Seq(p("/d"), p("/b"), p("/c"))) ++ Classpath(Seq(p("/d")))
    assertEquals(cp.entries, Seq(p("/b"), p("/c"), p("/d")))
  }

  test("empty is identity") {
    val cp = Classpath(Seq(p("/a")))
    assertEquals((Classpath.empty ++ cp).entries, cp.entries)
    assertEquals((cp ++ Classpath.empty).entries, cp.entries)
  }
}
