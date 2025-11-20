package ba.sake.deder.config

import org.pkl.config.java.ConfigEvaluator
import org.pkl.core.ModuleSource
import ba.sake.deder.config.DederProject

class ConfigParser {

  def parse() = {

    val configFile=  os.pwd / "examples/multi/deder.pkl"
    val evaluator = ConfigEvaluator.preconfigured
    val config = evaluator.evaluate(ModuleSource.file(configFile.toIO))
    
    val project = config.as(classOf[DederProject])
    println(project)
  }
}
