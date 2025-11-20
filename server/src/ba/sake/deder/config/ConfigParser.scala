package ba.sake.deder.config

import org.pkl.config.java.ConfigEvaluator
import org.pkl.core.ModuleSource
import ba.sake.deder.config.DederProject

class ConfigParser {

  def parse(configFile: os.Path): DederProject = {
    val evaluator = ConfigEvaluator.preconfigured
    val config = evaluator.evaluate(ModuleSource.file(configFile.toIO))
    config.as(classOf[DederProject])
  }
}
