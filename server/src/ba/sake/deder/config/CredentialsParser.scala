package ba.sake.deder.config

import scala.util.Using
import org.pkl.config.java.ConfigEvaluator
import org.pkl.core.{ModuleSource, PklException}

object CredentialsParser {

  def parse(credentialsFile: os.Path): Either[String, DederCredentials] = try {
    Using.resource(ConfigEvaluator.preconfigured) { evaluator =>
      val config = evaluator.evaluate(ModuleSource.file(credentialsFile.toIO))
      Right(config.as(classOf[DederCredentials]))
    }
  } catch {
    case pklException: PklException =>
      Left(s"Failed to parse credentials file: ${pklException.getMessage}")
  }
}
