package ba.sake.deder.scalanative

import ba.sake.tupson.JsonRW

/** Linker inputs assembled from individual configuration tasks. */
private[scalanative] case class ScalaNativeLinkerSettings(
    gc: String,
    mode: String,
    multithreading: Boolean,
    lto: String,
    embedResources: Boolean,
    linkStubs: Boolean,
    check: Boolean,
    checkFatalWarnings: Boolean,
    optimize: Boolean,
    targetTriple: Option[String],
    nativeLinkingOptions: Seq[String],
    nativeCompileOptions: Seq[String],
    nativeCOptions: Seq[String],
    nativeCppOptions: Seq[String],
    resourceIncludePatterns: Seq[String],
    resourceExcludePatterns: Seq[String]
) derives JsonRW
