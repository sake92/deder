package ba.sake.deder.migrating.importing

import ba.sake.deder.{ServerNotification, ServerNotificationsLogger}
import ba.sake.deder.cli.ImportBuildTool
import ba.sake.deder.migrating.importing.sbt.SbtImporter

class Importer(
    serverNotificationsLogger: ServerNotificationsLogger
) {

  def doImport(from: ImportBuildTool): Boolean = {
    serverNotificationsLogger.add(ServerNotification.logInfo(s"Starting build import from '${from}'..."))
    from match {
      case ImportBuildTool.sbt =>
        val sbtImporter = new SbtImporter(serverNotificationsLogger)
        sbtImporter.doImport()
    }
    serverNotificationsLogger.add(ServerNotification.logInfo(s"Build import from '${from}' succeeded."))
    true
  }
}
