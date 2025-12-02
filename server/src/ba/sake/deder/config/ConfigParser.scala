package ba.sake.deder.config

import org.pkl.config.java.ConfigEvaluator
import org.pkl.core.{ModuleSource, PklException}
import ba.sake.deder.config.DederProject

class ConfigParser() {

  def parse(configFile: os.Path): Either[String, DederProject] = try {
    val evaluator = ConfigEvaluator.preconfigured
    val config = evaluator.evaluate(ModuleSource.file(configFile.toIO))
    Right(config.as(classOf[DederProject]))
  } catch {
    case pklException: PklException =>
      Left(s"Failed to parse config file: ${pklException.getMessage}")
  }
}
