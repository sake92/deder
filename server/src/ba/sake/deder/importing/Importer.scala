package ba.sake.deder.importing

import ba.sake.deder.{DederGlobals, ServerNotification, ServerNotificationsLogger}
import ba.sake.deder.cli.ImportBuildTool
import ba.sake.deder.importing.sbt.SbtImporter

class Importer(
    serverNotificationsLogger: ServerNotificationsLogger
) {

  def doImport(from: Option[ImportBuildTool]): Unit = {
    val resolvedFrom = from match {
      case Some(bt) => bt
      case None =>
        ImportingUtils.detectBuildTool() match {
          case Some(bt) => bt
          case None =>
            throw new IllegalArgumentException(
              "No supported build file detected. Currently only sbt (`build.sbt`) is supported. " +
                "You can also specify the build tool explicitly with `--from sbt`."
            )
        }
    }

    serverNotificationsLogger.add(ServerNotification.logInfo(s"Starting build import from '${resolvedFrom}'..."))

    val pklContent = resolvedFrom match {
      case ImportBuildTool.sbt =>
        val sbtImporter = new SbtImporter(serverNotificationsLogger)
        sbtImporter.doImport()
    }

    // Safe write: backup existing deder.pkl only after content is ready
    val targetPath = DederGlobals.projectRootDir / "deder.pkl"
    if os.exists(targetPath) then {
      val backupPath = ImportingUtils.findNextBackupPath()
      os.copy(targetPath, backupPath)
      serverNotificationsLogger.add(
        ServerNotification.logInfo(s"Backed up existing deder.pkl to ${backupPath.last}")
      )
    }
    os.write.over(targetPath, pklContent)

    serverNotificationsLogger.add(ServerNotification.logInfo(s"Build import from '${resolvedFrom}' succeeded."))
  }
}
