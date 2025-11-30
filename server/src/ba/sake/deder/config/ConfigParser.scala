package ba.sake.deder.config

import ba.sake.deder.{DederException, ServerNotification, ServerNotificationsLogger}
import org.pkl.config.java.ConfigEvaluator
import org.pkl.core.{ModuleSource, PklException}
import ba.sake.deder.config.DederProject

class ConfigParser(serverNotificationsLogger: ServerNotificationsLogger) {

  def parse(configFile: os.Path): DederProject = try {
    val evaluator = ConfigEvaluator.preconfigured
    val config = evaluator.evaluate(ModuleSource.file(configFile.toIO))
    config.as(classOf[DederProject])
  } catch {
    case pklException: PklException =>
      serverNotificationsLogger.add(
        ServerNotification.message(
          ServerNotification.Level.ERROR,
          s"Failed to parse config file: ${pklException.getMessage}"
        )
      )
      throw DederException("Failed to parse config file", pklException)
  }
}
