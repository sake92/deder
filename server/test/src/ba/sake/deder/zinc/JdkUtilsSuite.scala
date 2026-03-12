package ba.sake.deder.zinc

class JdkUtilsSuite extends munit.FunSuite {

  test("getVersion parses legacy 1.x format") {
    assertEquals(JdkUtils.getVersion("1.8"), 8)
    assertEquals(JdkUtils.getVersion("1.6"), 6)
  }

  test("getVersion parses modern format") {
    assertEquals(JdkUtils.getVersion("11"), 11)
    assertEquals(JdkUtils.getVersion("17"), 17)
    assertEquals(JdkUtils.getVersion("21"), 21)
    assertEquals(JdkUtils.getVersion("25"), 25)
  }

  test("checkCompat allows exact minimum version") {
    // JDK 11 requires at least 2.12.4
    JdkUtils.checkCompat(11, "2.12.4")
  }

  test("checkCompat allows version above minimum") {
    JdkUtils.checkCompat(11, "2.12.15")
    JdkUtils.checkCompat(11, "2.12.20")
  }

  test("checkCompat numeric comparison: 2.12.15 is valid on JDK 17") {
    // Regression: string comparison would reject .15 < .4 style issues
    // JDK 17 requires at least 2.12.15
    JdkUtils.checkCompat(17, "2.12.15")
    JdkUtils.checkCompat(17, "2.12.18")
  }

  test("checkCompat numeric comparison: 2.13.11 is valid on JDK 21") {
    // JDK 21 requires at least 2.13.11
    // String comparison would think .11 < .6 (from JDK 17 entry) — but
    // the sorted list means JDK 21 entry is checked first, so .11 >= .11
    JdkUtils.checkCompat(21, "2.13.11")
    JdkUtils.checkCompat(21, "2.13.17")
  }

  test("checkCompat allows Scala 3 on JDK 21") {
    JdkUtils.checkCompat(21, "3.3.1")
    JdkUtils.checkCompat(21, "3.3.7")
  }

  test("checkCompat Scala 3: binaryVersion is '3', not '3.x' — cross-minor valid") {
    // binaryVersion("3.3.0") == "3", so all 3.x entries are relevant
    // JDK 11 requires at least 3.3.0
    JdkUtils.checkCompat(11, "3.3.0")
    JdkUtils.checkCompat(11, "3.3.5")
    JdkUtils.checkCompat(11, "3.4.0")
  }

  test("checkCompat Scala 3: rejects 3.0.x on JDK 11") {
    // JDK 11 requires at least 3.3.0 — 3.0.x is below that
    val ex = intercept[RuntimeException] {
      JdkUtils.checkCompat(11, "3.0.0")
    }
    assert(clue(ex.getMessage).contains("3.3.0"))
  }

  test("checkCompat Scala 3: rejects 3.2.x on JDK 11") {
    val ex = intercept[RuntimeException] {
      JdkUtils.checkCompat(11, "3.2.2")
    }
    assert(clue(ex.getMessage).contains("3.3.0"))
  }

  test("checkCompat Scala 3: rejects 3.3.0 on JDK 21") {
    // JDK 21 requires at least 3.3.1
    val ex = intercept[RuntimeException] {
      JdkUtils.checkCompat(21, "3.3.0")
    }
    assert(clue(ex.getMessage).contains("3.3.1"))
  }

  test("checkCompat allows lowest JDK 8 baselines") {
    JdkUtils.checkCompat(8, "2.12.0")
    JdkUtils.checkCompat(8, "2.13.0")
    JdkUtils.checkCompat(8, "3.0.0")
  }

  test("checkCompat allows high patch versions") {
    JdkUtils.checkCompat(8, "2.12.99")
    JdkUtils.checkCompat(8, "2.13.99")
    JdkUtils.checkCompat(8, "3.0.99")
  }

  // -- checkCompat: invalid combinations (should throw) --

  test("checkCompat rejects version below minimum for JDK 11") {
    // JDK 11 requires at least 2.12.4
    val ex = intercept[RuntimeException] {
      JdkUtils.checkCompat(11, "2.12.3")
    }
    assert(clue(ex.getMessage).contains("2.12.4"))
    assert(clue(ex.getMessage).contains("2.12.3"))
  }

  test("checkCompat rejects version below minimum for JDK 17 (numeric edge case)") {
    // JDK 17 requires at least 2.12.15
    val ex = intercept[RuntimeException] {
      JdkUtils.checkCompat(17, "2.12.14")
    }
    assert(clue(ex.getMessage).contains("2.12.15"))
  }

  test("checkCompat rejects version below minimum for JDK 21 Scala 2.13") {
    // JDK 21 requires at least 2.13.11
    val ex = intercept[RuntimeException] {
      JdkUtils.checkCompat(21, "2.13.10")
    }
    assert(clue(ex.getMessage).contains("2.13.11"))
  }

  test("checkCompat rejects version below minimum for JDK 25 Scala 3") {
    // JDK 25 requires at least 3.3.7
    val ex = intercept[RuntimeException] {
      JdkUtils.checkCompat(25, "3.3.6")
    }
    assert(clue(ex.getMessage).contains("3.3.7"))
  }

  test("checkCompat rejects Scala 2.13.0 on JDK 17") {
    // JDK 17 requires at least 2.13.6
    val ex = intercept[RuntimeException] {
      JdkUtils.checkCompat(17, "2.13.0")
    }
    assert(clue(ex.getMessage).contains("2.13.6"))
  }

  test("checkCompat error message includes docs URL") {
    val ex = intercept[RuntimeException] {
      JdkUtils.checkCompat(17, "2.12.0")
    }
    assert(clue(ex.getMessage).contains(JdkUtils.docsUrl))
  }

  // -- checkCompat: unknown / unsupported JDK --
/*
  test("checkCompat throws for unsupported JDK version") {
    // JDK 99 has no entry for any Scala version
    val ex = intercept[RuntimeException] {
      JdkUtils.checkCompat(99, "2.12.20")
    }
    assert(clue(ex.getMessage).contains("No minimum JDK specified"))
  }*/
}
