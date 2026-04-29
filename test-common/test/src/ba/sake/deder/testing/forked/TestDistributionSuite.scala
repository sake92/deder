package ba.sake.deder.testing.forked

import ba.sake.deder.testing.*

class TestDistributionSuite extends munit.FunSuite {

  private val fp = JsonableFingerprint.Subclass("java.lang.Object", false, false)

  private def cls(name: String) = DiscoveredFrameworkTest(name, fp)

  private def dft(classes: String*) =
    DiscoveredFrameworkTests("munit", "munit.Framework", classes.map(cls).toSeq)

  private def stats(durationMs: Long) = TestClassStats(durationMs, "passed", 0L)

  test("empty input returns empty output") {
    val result = TestDistribution.distribute(Seq.empty, TestHistory.empty, maxForks = 4)
    assertEquals(result, Seq.empty)
  }

  test("maxForks <= 0 returns empty output") {
    val input = Seq(dft("A"))
    assertEquals(TestDistribution.distribute(input, TestHistory.empty, maxForks = 0), Seq.empty)
    assertEquals(TestDistribution.distribute(input, TestHistory.empty, maxForks = -1), Seq.empty)
  }

  test("single class always produces one bucket") {
    val result = TestDistribution.distribute(Seq(dft("A")), TestHistory.empty, maxForks = 4)
    assertEquals(result.size, 1)
    assertEquals(allClasses(result), Set("A"))
  }

  test("effective forks is capped at total class count") {
    val result = TestDistribution.distribute(Seq(dft("A", "B")), TestHistory.empty, maxForks = 10)
    assertEquals(result.size, 2)
  }

  test("all classes are distributed — none lost, none duplicated") {
    val input = Seq(dft("A", "B", "C", "D", "E"))
    val result = TestDistribution.distribute(input, TestHistory.empty, maxForks = 3)
    assertEquals(allClasses(result), Set("A", "B", "C", "D", "E"))
  }

  test("maxForks=1 puts everything in one bucket") {
    val input = Seq(dft("A", "B", "C"))
    val result = TestDistribution.distribute(input, TestHistory.empty, maxForks = 1)
    assertEquals(result.size, 1)
    assertEquals(allClasses(result), Set("A", "B", "C"))
  }

  test("heavy class lands in its own bucket") {
    val history = TestHistory(Map("Heavy" -> stats(1000L), "Light1" -> stats(10L), "Light2" -> stats(10L)))
    val input = Seq(dft("Heavy", "Light1", "Light2"))
    val result = TestDistribution.distribute(input, history, maxForks = 2)
    assertEquals(result.size, 2)
    val bucketSets = result.map(bucket => allClasses(Seq(bucket)))
    assert(
      bucketSets.exists(b => b.contains("Heavy") && b.size == 1),
      s"Expected Heavy to be isolated: $result"
    )
  }

  test("framework grouping is preserved within each bucket") {
    val munitDft = DiscoveredFrameworkTests("munit", "munit.Framework", Seq(cls("A"), cls("B")))
    val scalatestDft = DiscoveredFrameworkTests("scalatest", "org.scalatest.Framework", Seq(cls("C")))
    val result = TestDistribution.distribute(Seq(munitDft, scalatestDft), TestHistory.empty, maxForks = 3)
    result.foreach { bucket =>
      bucket.foreach { dft =>
        assert(dft.frameworkName == "munit" || dft.frameworkName == "scalatest")
      }
    }
    assertEquals(allClasses(result), Set("A", "B", "C"))
  }

  test("unknown class duration falls back to median of known durations") {
    val history = TestHistory(Map("Known" -> stats(100L)))
    val input = Seq(dft("Known", "New"))
    val result = TestDistribution.distribute(input, history, maxForks = 2)
    assertEquals(result.size, 2)
    assertEquals(allClasses(result), Set("Known", "New"))
  }

  // Collects all class names across all buckets
  private def allClasses(result: Seq[Seq[DiscoveredFrameworkTests]]): Set[String] =
    result.flatMap(_.flatMap(_.testClasses.map(_.className))).toSet
}
