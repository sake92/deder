package ba.sake.deder.testing

import ba.sake.tupson.{*, given}

case class TestClassStats(
    durationMs: Long,
    lastStatus: String,
    lastRunEpochMs: Long
) derives JsonRW
