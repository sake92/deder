package ba.sake.deder.testing

import ba.sake.tupson.JsonRW

case class ForkedTestArgs(
    discoveredTests: Seq[DiscoveredFrameworkTests],
    testSelectors: Seq[String],
    workerThreads: Int,
    resultsFile: String
) derives JsonRW
