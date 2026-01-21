package ba.sake.deder.importing

object ImportingUtils {

  // modules cant have these names coz they clash with Pkl properties
  private val ReservedIdentifiers = Set(
    "root",
    "id",
    "sources",
    "resources",
    "moduleDeps",
    "type",
    "javaHome",
    "jvmOptions",
    "javaVersion",
    "javacOptions",
    "mainClass",
    "deps",
    "javacAnnotationProcessorDeps",
    "semanticdbEnabled",
    "javaSemanticdbVersion",
    "scalaVersion",
    "scalacOptions",
    "scalacPluginDeps",
    "scalaSemanticdbVersion",
    "testFrameworks"
  )

  def sanitizeId(id: String): String =
    if ReservedIdentifiers.contains(id) then s"_${id}"
    else id

}
