package ba.sake.deder.importing

import ba.sake.deder.DederGlobals
import ba.sake.deder.cli.ImportBuildTool

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
    "javaSemanticdbVersion",
    "scalaVersion",
    "scalacOptions",
    "scalacPluginDeps",
    "scalaSemanticdbVersion",
    "testFrameworks",
    "manifest"
  )

  def sanitizeId(id: String): String =
    if ReservedIdentifiers.contains(id) then s"_${id}"
    else id

  /** Detects the build tool used in the current project by inspecting known marker files. Currently only sbt
    * (`build.sbt`) is supported.
    *
    * @return
    *   Some build tool if a supported build file exists, None otherwise.
    */
  def detectBuildTool(): Option[ImportBuildTool] = {
    val projectRoot = DederGlobals.projectRootDir
    if os.exists(projectRoot / "build.sbt") then Some(ImportBuildTool.sbt)
    else None
  }

  /** Finds the next available backup path for deder.pkl. Returns `deder.pkl.bak1`, then `.bak2`, etc., skipping
    * existing files.
    */
  def findNextBackupPath(): os.Path = {
    val projectRoot = DederGlobals.projectRootDir
    var idx = 1
    var backupPath = projectRoot / s"deder.pkl.bak$idx"
    while os.exists(backupPath) do {
      idx += 1
      backupPath = projectRoot / s"deder.pkl.bak$idx"
    }
    backupPath
  }

}
