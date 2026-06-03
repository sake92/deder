package ba.sake.deder

import java.time.Duration

class DurationFormatSuite extends munit.FunSuite {

  test("seconds only, no decimals") {
    assertEquals(Duration.ofSeconds(5).toPrettyString, "5.00s")
  }

  test("seconds with decimals, rounded to 2 places") {
    assertEquals(Duration.ofNanos(5_123_456_789L).toPrettyString, "5.12s")
  }

  test("seconds with decimals, rounding up") {
    assertEquals(Duration.ofNanos(5_995_000_000L).toPrettyString, "6.00s")
  }

  test("minutes and seconds") {
    assertEquals(Duration.ofSeconds(125).toPrettyString, "2m5.00s")
  }

  test("minutes and fractional seconds") {
    assertEquals(Duration.ofNanos(125_500_000_000L).toPrettyString, "2m5.50s")
  }

  test("hours, minutes, and seconds") {
    assertEquals(Duration.ofSeconds(3725).toPrettyString, "1h2m5.00s")
  }

  test("hours and seconds (no minutes)") {
    assertEquals(Duration.ofSeconds(3605).toPrettyString, "1h0m5.00s")
  }

  test("zero duration") {
    assertEquals(Duration.ZERO.toPrettyString, "0.00s")
  }

  test("sub-millisecond duration") {
    assertEquals(Duration.ofNanos(500_000).toPrettyString, "0.00s")
  }

  test("very small but rounds up") {
    assertEquals(Duration.ofNanos(5_000_000).toPrettyString, "0.01s")
  }

  test("typical build time (e.g. 43.620014957s)") {
    assertEquals(Duration.ofNanos(43_620_014_957L).toPrettyString, "43.62s")
  }

}
