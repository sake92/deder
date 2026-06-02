package ba.sake.deder.testing.forked

import ba.sake.deder.testing.TestClassStats

class TestHistorySuite extends munit.FunSuite {

  private def stats(durationMs: Long) = TestClassStats(durationMs, "passed", 0L)

  // --- merge ---

  test("merge adds new entries") {
    val h = TestHistory.empty.merge(Map("A" -> stats(100L)))
    assertEquals(h.durationOf("A"), Some(100L))
  }

  test("merge overwrites existing entries") {
    val h = TestHistory(Map("A" -> stats(100L))).merge(Map("A" -> stats(999L)))
    assertEquals(h.durationOf("A"), Some(999L))
  }

  test("merge preserves entries not in the update") {
    val h = TestHistory(Map("A" -> stats(100L))).merge(Map("B" -> stats(200L)))
    assertEquals(h.durationOf("A"), Some(100L))
    assertEquals(h.durationOf("B"), Some(200L))
  }

  test("durationOf returns None for unknown class") {
    assertEquals(TestHistory.empty.durationOf("Unknown"), None)
  }

  // --- load / save ---

  test("save then load round-trips correctly") {
    val dir = os.temp.dir()
    val history = TestHistory(Map("com.example.MySuite" -> stats(42L)))
    TestHistory.save(dir, history)
    val loaded = TestHistory.load(dir)
    assertEquals(loaded, history)
  }

  test("load returns empty when file does not exist") {
    val dir = os.temp.dir()
    assertEquals(TestHistory.load(dir), TestHistory.empty)
  }

  test("load returns empty when file is corrupted") {
    val dir = os.temp.dir()
    os.write(TestHistory.fileFor(dir), "not-valid-json{{")
    assertEquals(TestHistory.load(dir), TestHistory.empty)
  }

  test("save creates missing directories") {
    val dir = os.temp.dir() / "nested" / "deep"
    val history = TestHistory(Map("A" -> stats(1L)))
    TestHistory.save(dir, history)
    assertEquals(TestHistory.load(dir), history)
  }

  test("save leaves no .tmp file behind") {
    val dir = os.temp.dir()
    TestHistory.save(dir, TestHistory(Map("A" -> stats(1L))))
    assert(!os.exists(dir / "test-history.json.tmp"))
  }
}
