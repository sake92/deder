package ba.sake.deder.config

import scala.util.Using
import scala.jdk.CollectionConverters.*
import org.pkl.config.java.ConfigEvaluator
import org.pkl.core.{EvaluatorBuilder, ModuleSource, OutputFormat, PklException}
import ba.sake.deder.config.DederProject
import ba.sake.deder.DederGlobals
import org.pkl.config.java.ConfigEvaluatorBuilder

class ConfigParser(writeJson: Boolean) {

  /** Parse a config file and return either a structured [[InvalidConfig]] (with extracted
    * location info) or the parsed [[DederProject]].
    */
  def parse(configFile: os.Path): Either[InvalidConfig, DederProject] =
    parse(ModuleSource.file(configFile.toIO))

  def parse(moduleSource: ModuleSource): Either[InvalidConfig, DederProject] = try {
    Using.resource(ConfigEvaluator.preconfigured) { configEvaluator =>
      val config = configEvaluator.evaluate(moduleSource)
      val dederProject = config.as(classOf[DederProject])
      val moduleIds = dederProject.modules.asScala.map(_.id)
      val diff = moduleIds.diff(moduleIds.distinct)
      if diff.nonEmpty then
        Left(InvalidConfig(s"Duplicate module ids found: ${diff.distinct.mkString(", ")}", None, 1, 0))
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
      Left(extractInvalidConfig(pklException))
  }

  /** Extract structured location info from a [[PklException]] rendered message.
    *
    * Pkl renders errors as multi-line text like:
    * {{{
    * –– Pkl Error ––
    * <message summary>
    * <lineNo> | <source line>
    *            ^^^
    * at <member> (<fileUri>)
    * }}}
    *
    * We try to pull the file URI and first line number out with simple regexes.
    * If extraction fails we fall back to line 1 / col 0 so callers always get a valid range.
    */
  private def extractInvalidConfig(e: PklException): InvalidConfig = {
    val rawMsg = e.getMessage
    val summary = buildSummary(rawMsg)

    // "at someProperty (file:///path/to/deder.pkl)" – grab innermost URI
    val fileUriPattern = """at [^\s(]+ \(([^)]+)\)""".r
    val fileUri = fileUriPattern
      .findAllMatchIn(rawMsg)
      .map(_.group(1))
      .find(uri => uri.startsWith("file:") || uri.startsWith("pkl:"))

    // "<lineNo> | <source>" – first occurrence gives the relevant line
    val linePattern = """(\d+) \|""".r
    val startLine = linePattern.findFirstMatchIn(rawMsg).map(_.group(1).toInt).getOrElse(1)

    InvalidConfig(summary, fileUri, startLine, 0)
  }

  /** Produce a concise summary from a potentially verbose Pkl error message.
    *
    * Strips the "–– Pkl Error ––" header line and ANSI escape sequences,
    * then takes the first non-blank paragraph (up to ~300 chars).
    */
  private def buildSummary(raw: String): String = {
    // Remove ANSI escape codes (Pkl may include them even without a tty on some JVMs)
    val ansiStripped = raw.replaceAll("\u001b\\[[;\\d]*m", "")
    // Drop "–– Pkl Error ––" header line if present
    val withoutHeader = ansiStripped
      .linesIterator
      .dropWhile(l => l.trim.startsWith("––") || l.trim.isEmpty)
      .mkString("\n")
    // Take the first non-blank block (up to 300 chars)
    val firstBlock = withoutHeader
      .linesIterator
      .takeWhile(l => !l.trim.startsWith("at ") && l.trim.nonEmpty)
      .mkString(" ")
      .trim
    if firstBlock.nonEmpty then firstBlock.take(300)
    else ansiStripped.take(300)
  }
}
