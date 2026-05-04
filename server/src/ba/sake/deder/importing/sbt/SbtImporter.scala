package ba.sake.deder.importing.sbt

import ba.sake.tupson.parseJson
import ba.sake.deder.ServerNotification
import ba.sake.deder.ServerNotificationsLogger

class SbtImporter(
    serverNotificationsLogger: ServerNotificationsLogger
) {

  private def dumpSbtBuild() = {
    val sbtCmd = if (scala.util.Properties.isWin) "sbt.bat" else "sbt"
    val exportBuildStructurePluginVersion = "0.0.3+5-81323fe9-SNAPSHOT"
    val exportBuildStructurePluginSource =
      s"""addSbtPlugin("ba.sake" % "sbt-build-extract" % "$exportBuildStructurePluginVersion")
         |libraryDependencies += "ba.sake" %% "sbt-build-extract-core" % "$exportBuildStructurePluginVersion"
         |""".stripMargin
    val exportBuildStructurePluginPath = os.pwd / "project/exportBuildStructure.sbt"
    os.write.over(exportBuildStructurePluginPath, exportBuildStructurePluginSource)
    val sbtProc = os.spawn((sbtCmd, "exportAllBuildStructures"), mergeErrIntoOut = true)
    try {
      var line = ""
      while {
        line = sbtProc.stdout.readLine()
        line != null
      } do {
        serverNotificationsLogger.add(ServerNotification.logInfo(line))
      }
      sbtProc.waitFor()
    } finally {
      // Ensure sbt process is destroyed even if we're interrupted
      if sbtProc.isAlive() then
        sbtProc.destroy()
        sbtProc.waitFor(5000L)
        if sbtProc.isAlive() then sbtProc.destroyForcibly()
      os.remove(exportBuildStructurePluginPath)
    }
  }

  private def readAndParseExportedModules(): IndexedSeq[ProjectExport] = {
    val exportDir = os.pwd / "target/build-export"
    val exportedSbtModuleFiles = os.list(exportDir).filter(_.ext == "json")
    val allModules = exportedSbtModuleFiles
      .map(mf => os.read(mf).parseJson[ProjectExport])
    // skip root aggregating project
    if (allModules.length > 1) allModules.filterNot(_.base == os.pwd.toString) else allModules
  }

  def doImport() = {
    dumpSbtBuild()
    val exportedSbtModules = readAndParseExportedModules()
    val exporter = new DederSbtExporter(exportedSbtModules, serverNotificationsLogger)
    exporter.writeBuild()
  }
}
