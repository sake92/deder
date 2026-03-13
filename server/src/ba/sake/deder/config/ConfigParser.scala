package ba.sake.deder.config

import ba.sake.deder.DederGlobals

import scala.jdk.CollectionConverters.*
import org.pkl.config.java.ConfigEvaluator
import org.pkl.core.{EvaluatorBuilder, ModuleSource, OutputFormat, PklException}
import ba.sake.deder.config.DederProject

import scala.util.Using

class ConfigParser(writeJson: Boolean) {

  def parse(configFile: os.Path): Either[String, DederProject] =
    parse(ModuleSource.file(configFile.toIO))

  def parse(moduleSource: ModuleSource): Either[String, DederProject] = try {
    Using.resource(ConfigEvaluator.preconfigured) { evaluator =>
      val config = evaluator.evaluate(moduleSource)
      val dederProject = config.as(classOf[DederProject])
      val moduleIds = dederProject.modules.asScala.map(_.id)
      val diff = moduleIds.diff(moduleIds.distinct)
      if diff.nonEmpty then Left(s"Duplicate module ids found: ${diff.distinct.mkString(", ")}")
      else
        Right {
          if writeJson then
            Using.resource(EvaluatorBuilder.preconfigured.setOutputFormat(OutputFormat.JSON).build) { evaluator =>
              val text = evaluator.evaluateOutputText(moduleSource)
              val jsonFile = DederGlobals.projectRootDir / ".deder/out/project.json"
              os.makeDir.all(jsonFile / os.up)
              os.write.over(jsonFile, text) // useful for debugging
            }
          dederProject
        }
    }
  } catch {
    case pklException: PklException =>
      Left(s"Failed to parse config file: ${pklException.getMessage}")
  }
}
