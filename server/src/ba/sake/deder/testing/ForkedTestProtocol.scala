package ba.sake.deder.testing

import ba.sake.tupson.JsonRW

case class ForkedTestArgs(
    discoveredTests: Seq[DiscoveredFrameworkTests],
    testSelectors: Seq[String],
    testParallelism: Int,
    resultsFile: String
) derives JsonRW
