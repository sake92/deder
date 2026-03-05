package ba.sake.deder.config

import ba.sake.deder.DederGlobals

import scala.jdk.CollectionConverters.*
import org.pkl.config.java.ConfigEvaluator
import org.pkl.core.{EvaluatorBuilder, ModuleSource, OutputFormat, PklException}
import ba.sake.deder.config.DederProject

class ConfigParser(writeJson: Boolean) {

  def parse(str: String): Either[String, DederProject] =
    parse(ModuleSource.text(str))

  def parse(configFile: os.Path): Either[String, DederProject] =
    parse(ModuleSource.file(configFile.toIO))

  def parse(moduleSource: ModuleSource): Either[String, DederProject] = try {
    val evaluator = ConfigEvaluator.preconfigured
    val config = evaluator.evaluate(moduleSource)
    val dederProject = config.as(classOf[DederProject])
    val moduleIds = dederProject.modules.asScala.map(_.id)
    val diff = moduleIds.diff(moduleIds.distinct)
    if diff.nonEmpty then Left(s"Duplicate module ids found: ${diff.distinct.mkString(", ")}")
    else
      Right {
        val evaluator = EvaluatorBuilder.preconfigured.setOutputFormat(OutputFormat.JSON).build
        if writeJson then
          try {
            val text = evaluator.evaluateOutputText(moduleSource)
            os.write.over(DederGlobals.projectRootDir / ".deder/out/project.json", text) // useful for debugging
          } finally if (evaluator != null) evaluator.close()
        dederProject
      }
  } catch {
    case pklException: PklException =>
      Left(s"Failed to parse config file: ${pklException.getMessage}")
  }
}
