package ba.sake.deder.importing.sbt

import ba.sake.deder.ServerNotification
import ba.sake.deder.ServerNotificationsLogger
import ba.sake.deder.importing.DederPklRenderer

class SbtImporter(
    serverNotificationsLogger: ServerNotificationsLogger,
    private[sbt] val runSbtExportCommand: String => Int
) {

  def this(serverNotificationsLogger: ServerNotificationsLogger) =
    this(serverNotificationsLogger, SbtImporter.runSbtExportCommand(serverNotificationsLogger))

  private val sbtExportBuildVersion = "0.0.5"

  def doImport() = {
    dumpSbtBuild()
    val exportedSbtModules = readAndParseExportedModules()
    // Analysis phase
    val analyzer = new SbtProjectAnalyzer(serverNotificationsLogger)
    val build = analyzer.analyze(exportedSbtModules)

    // Render phase
    val pklContent = DederPklRenderer.render(build)
    os.write.over(os.pwd / "deder.pkl", pklContent)

    // Summary
    val summary = analyzer.summary()
    summary.log(serverNotificationsLogger)
  }

  private def dumpSbtBuild() = {
    val exportDir = os.pwd / "target/build-export"
    if os.exists(exportDir) then os.remove.all(exportDir)
    os.makeDir.all(exportDir)

    val sbtCmd = if (scala.util.Properties.isWin) "sbt.bat" else "sbt"
    val exportBuildStructurePluginSource =
      s"""addSbtPlugin("ba.sake" % "sbt-build-extract" % "$sbtExportBuildVersion")
         |libraryDependencies += "ba.sake" %% "sbt-build-extract-core" % "$sbtExportBuildVersion"
         |""".stripMargin
    val exportBuildStructurePluginPath = os.pwd / "project/exportBuildStructure.sbt"
    os.write.over(exportBuildStructurePluginPath, exportBuildStructurePluginSource)
    try {
      val exitCode = runSbtExportCommand(sbtCmd)
      if exitCode != 0 then
        throw new IllegalStateException(s"'$sbtCmd exportAllBuildStructures' failed with exit code $exitCode.")
    } finally {
      if os.exists(exportBuildStructurePluginPath) then os.remove(exportBuildStructurePluginPath)
    }
  }

  private def readAndParseExportedModules(): IndexedSeq[ExportedProjectExportFile] = {
    val exportDir = os.pwd / "target/build-export"
    val exportedSbtModuleFiles = os.list(exportDir).filter(_.ext == "json")
    exportedSbtModuleFiles
      .map(mf => ExportedProjectExportFile.parse(mf, os.read(mf)))
  }

}

object SbtImporter {

  private[sbt] def runSbtExportCommand(
      serverNotificationsLogger: ServerNotificationsLogger
  ): String => Int = { sbtCmd =>
    val sbtProc = os.spawn((sbtCmd, "exportAllBuildStructures"), mergeErrIntoOut = true)
    try {
      var line = ""
      while {
        line = sbtProc.stdout.readLine()
        line != null
      } do {
        serverNotificationsLogger.add(ServerNotification.logInfo(line))
      }
      sbtProc.wrapped.waitFor()
    } finally {
      // Ensure sbt process is destroyed even if we're interrupted
      if sbtProc.wrapped.isAlive() then
        sbtProc.wrapped.destroy()
        sbtProc.wrapped.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        if sbtProc.wrapped.isAlive() then sbtProc.wrapped.destroyForcibly()
    }
  }

}
