// Workaround: sbt-typelevel and sbt-build-extract have conflicting upickle versions.
// Downgrade eviction errors to warnings so sbt can proceed.
evictionErrorLevel := Level.Warn
