package ba.sake.deder.testing.forked

import scala.util.control.NonFatal
import ba.sake.tupson.{*, given}

case class TestClassStats(
    durationMs: Long,
    lastStatus: String,
    lastRunEpochMs: Long
) derives JsonRW

case class TestHistory(
    stats: Map[String, TestClassStats]
) derives JsonRW {
  def durationOf(className: String): Option[Long] = stats.get(className).map(_.durationMs)

  def merge(newStats: Map[String, TestClassStats]): TestHistory =
    copy(stats = stats ++ newStats)
}

object TestHistory {
  val empty: TestHistory = TestHistory(Map.empty)

  def fileFor(outDir: os.Path): os.Path = outDir / "test-history.json"

  def load(outDir: os.Path): TestHistory = {
    val file = fileFor(outDir)
    if os.exists(file) then
      try os.read(file).parseJson[TestHistory]
      catch { case NonFatal(_) => empty }
    else empty
  }

  /** Atomic write via tmp file + rename. Silent on failure — history is advisory, never fatal. */
  def save(outDir: os.Path, history: TestHistory): Unit = {
    try {
      os.makeDir.all(outDir)
      val target = fileFor(outDir)
      val tmp = outDir / "test-history.json.tmp"
      os.write.over(tmp, history.toJson)
      os.move(tmp, target, replaceExisting = true)
    } catch {
      case NonFatal(_) => ()
    }
  }
}
