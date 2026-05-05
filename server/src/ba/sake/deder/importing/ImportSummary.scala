package ba.sake.deder.importing

import ba.sake.deder.ServerNotification
import ba.sake.deder.ServerNotificationsLogger

case class ImportSummary(
    modulesImported:    Int,
    moduleGroups:       Int,
    dependenciesMapped: Int,
    depsFiltered:       Int,
    depsSkipped:        Int,
    warnings:           Seq[ImportWarning],
    errors:             Seq[String]
) {
    def log(logger: ServerNotificationsLogger): Unit = {
        logger.add(ServerNotification.logInfo("=== Import Summary ==="))
        logger.add(ServerNotification.logInfo(s"Module groups: $moduleGroups ($modulesImported concrete modules)"))
        logger.add(ServerNotification.logInfo(s"Dependencies mapped: $dependenciesMapped"))
        if (depsFiltered > 0) {
            logger.add(ServerNotification.logInfo(s"Ignored (auto-added by Deder): $depsFiltered"))
        }
        if (depsSkipped > 0) {
            logger.add(ServerNotification.logWarning(s"Skipped dependencies: $depsSkipped"))
        }
        if (warnings.nonEmpty) {
            logger.add(ServerNotification.logWarning(s"Warnings:"))
            warnings.foreach { w =>
                logger.add(ServerNotification.logWarning(s"  - ${formatWarning(w)}"))
            }
        }
        if (errors.nonEmpty) {
            errors.foreach { e =>
                logger.add(ServerNotification.logError(s"  - $e"))
            }
        }
    }

    private def formatWarning(w: ImportWarning): String = w match {
        case ImportWarning.CrossScalaVersionsNotSupported(name, versions, selected) =>
            s"$name: crossScalaVersions ${versions.mkString("[", ", ", "]")} not supported, using $selected"
    }
}
