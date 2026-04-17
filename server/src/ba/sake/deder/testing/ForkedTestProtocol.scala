package ba.sake.deder.testing

import ba.sake.tupson.JsonRW

case class ForkedTestArgs(
    forkId: Int,
    discoveredTests: Seq[DiscoveredFrameworkTests],
    testSelectors: Seq[String],
    testParallelism: Int,
    resultsFile: String
) derives JsonRW

case class ForkedTestResultsPayload(
    results: DederTestResults,
    perClassStats: Map[String, TestClassStats]
) derives JsonRW

/** JSON-lines messages streamed from ForkedTestMain back to the orchestrator over stdout.
  *
  * Each line is one serialized envelope. Anything written to stdout outside an envelope is user
  * output from before the capturing PrintStream was installed (very rare) — the orchestrator treats
  * such lines as `[fork-N boot]` tagged output.
  */
enum ForkedTestEnvelope derives JsonRW {
  case ForkStarted(forkId: Int)
  case SuiteStarted(suiteName: String, threadId: Long)
  case SuiteCompleted(suiteName: String, threadId: Long, capturedOutput: String)
  case UnattributedOutput(text: String)
  case ForkCompleted(forkId: Int, totals: DederTestResults)
}

object ForkedTestEnvelope {
  /** Marker that precedes every envelope on stdout so the orchestrator can distinguish envelopes
    * from stray bytes written to the original stdout before the capturing stream took over.
    */
  val LinePrefix: String = "@@DEDER-FORK@@ "
}
