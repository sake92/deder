package ba.sake.deder.deps

import ba.sake.deder.DependencyDedup
import ba.sake.deder.deps.Dependency

class DependencyDedupSuite extends munit.FunSuite {

  private val sv = "3.7.4"

  private def dep(decl: String): Dependency =
    Dependency.make(decl, sv)

  test("no duplicates: pass-through") {
    val deps = Seq(dep("org.scalameta::munit:1.0.2"))
    val result = DependencyDedup.deduplicate(deps)
    assertEquals(result.deduplicated.length, 1)
    assertEquals(result.conflicts.length, 0)
  }

  test("two versions, same lib: keep last") {
    val deps = Seq(
      dep("org.scalameta::munit:1.1.0"),
      dep("org.scalameta::munit:1.0.2")
    )
    val result = DependencyDedup.deduplicate(deps)
    assertEquals(result.deduplicated.length, 1)
    assertEquals(result.deduplicated.head.applied.version, "1.0.2")
    assertEquals(result.conflicts.length, 1)
    assertEquals(result.conflicts.head.versions, Seq("1.1.0", "1.0.2"))
    assertEquals(result.conflicts.head.keptVersion, "1.0.2")
    assert(result.conflicts.head.coordinate.contains("munit"))
  }

  test("three versions: keep last") {
    val deps = Seq(
      dep("org.scalameta::munit:1.1.0"),
      dep("org.scalameta::munit:1.0.0"),
      dep("org.scalameta::munit:1.0.2")
    )
    val result = DependencyDedup.deduplicate(deps)
    assertEquals(result.deduplicated.length, 1)
    assertEquals(result.deduplicated.head.applied.version, "1.0.2")
    assertEquals(result.conflicts.length, 1)
    assertEquals(result.conflicts.head.versions, Seq("1.1.0", "1.0.0", "1.0.2"))
  }

  test("different classifiers: both kept, no conflict") {
    val deps = Seq(
      dep("org.foo::bar:1.0"),
      dep("org.baz::qux:1.0")
    )
    val result = DependencyDedup.deduplicate(deps)
    assertEquals(result.deduplicated.length, 2)
    assertEquals(result.conflicts.length, 0)
  }

  test("platform-specific (JVM vs JS): different resolved names, both kept") {
    val deps = Seq(
      dep("org.scalameta::munit:1.0.2"),
      dep("org.scalacheck::scalacheck:1.18.1")
    )
    val result = DependencyDedup.deduplicate(deps)
    assertEquals(result.deduplicated.length, 2)
    assertEquals(result.conflicts.length, 0)
  }

  test("mixed duplicates and uniques: keep all uniques, dedup duplicates") {
    val deps = Seq(
      dep("org.a::lib-a:1.0"),
      dep("org.scalameta::munit:1.1.0"),
      dep("org.a::lib-a:2.0"),
      dep("org.b::lib-b:3.0")
    )
    val result = DependencyDedup.deduplicate(deps)
    assertEquals(result.deduplicated.length, 3)
    val libB = result.deduplicated.find(_.applied.module.name.value == "lib-b_3").get
    assertEquals(libB.applied.version, "3.0")
    val libA = result.deduplicated.filter(_.applied.module.name.value == "lib-a_3")
    assertEquals(libA.length, 1)
    assertEquals(libA.head.applied.version, "2.0")
    val munit = result.deduplicated.find(_.applied.module.name.value == "munit_3").get
    assertEquals(munit.applied.version, "1.1.0")
    assertEquals(result.conflicts.length, 1)
    assertEquals(result.conflicts.head.versions, Seq("1.0", "2.0"))
  }

  test("exact duplicates: keep one, warn") {
    val deps = Seq(
      dep("org.scalameta::munit:1.0.2"),
      dep("org.scalameta::munit:1.0.2")
    )
    val result = DependencyDedup.deduplicate(deps)
    assertEquals(result.deduplicated.length, 1)
    assertEquals(result.deduplicated.head.applied.version, "1.0.2")
    assertEquals(result.conflicts.length, 1)
    assertEquals(result.conflicts.head.versions, Seq("1.0.2", "1.0.2"))
  }

  test("empty list") {
    val result = DependencyDedup.deduplicate(Seq.empty)
    assertEquals(result.deduplicated, Seq.empty)
    assertEquals(result.conflicts.length, 0)
  }

  test("different :: and explicit suffix resolve to same applied name, deduped") {
    val deps = Seq(
      dep("org.scalameta::munit:1.1.0"),
      dep("org.scalameta:munit_3:1.0.2")
    )
    val result = DependencyDedup.deduplicate(deps)
    assertEquals(result.deduplicated.length, 1)
    assertEquals(result.conflicts.length, 1)
  }
}
