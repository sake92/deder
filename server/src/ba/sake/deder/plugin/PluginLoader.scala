package ba.sake.deder.plugin

import java.net.URLClassLoader
import scala.jdk.CollectionConverters.*
import scala.util.Using
import com.typesafe.scalalogging.StrictLogging
import ba.sake.deder.*
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.{DederModule, Plugin, ScalaModule}
import ba.sake.deder.deps.{Dependency, DependencyResolverApi}

class PluginLoader(
    coreTasksApi: CoreTasksApi,
    dependencyResolver: DependencyResolverApi
) extends StrictLogging {

  def extractPluginDeps(project: DederProject): Seq[(String, String)] = PluginLoader.extractPluginDeps(project)

  /** Phase 1: Evaluate deder.pkl minimally (no plugin JARs) to get project config. */
  def evaluatePhase1(pklFile: os.Path): Either[String, DederProject] = try {
    val moduleSource = org.pkl.core.ModuleSource.file(pklFile.toIO)
    val project = Using.resource(org.pkl.config.java.ConfigEvaluator.preconfigured) { evaluator =>
      evaluator.evaluate(moduleSource).as(classOf[DederProject])
    }
    Right(project)
  } catch {
    case e: Exception =>
      logger.warn(s"Phase 1 evaluation failed: ${e.getMessage}", e)
      Left(s"Phase 1 evaluation failed: ${e.getMessage}")
  }

  /** Extract plugin config as Pkl source text from the raw Pkl file.
   *  Each entry is a valid Pkl expression like: new hc.HelloConfig {\n  greeting = "Hi"\n}
   */
  def extractPluginConfigPkl(pklText: String): Seq[(String, String)] = {
    // Returns (pluginId, pklConfigText) — matched by scanning for plugin id inside the config text
    val results = Seq.newBuilder[(String, String)]

    // Find all "plugins {" blocks in the text
    val pluginBlocks = extractBlocks(pklText, "plugins")
    for block <- pluginBlocks do
      // Inside each plugins block, find "new ... { ... }" entries
      val newExprs = extractNewExprs(block)
      for expr <- newExprs do
        // Extract id from the config: look for "id = \"...\"" or pick from the type name
        val id = extractId(expr)
        results += (id -> expr.trim)

    results.result()
  }

  /** Extract top-level blocks like "plugins { ... }". Returns content between outermost braces. */
  private def extractBlocks(text: String, keyword: String): Seq[String] = {
    val blocks = Seq.newBuilder[String]
    var i = 0
    while i < text.length do
      val kw = text.indexOf(keyword, i)
      if kw < 0 then i = text.length
      else
        // Find the opening brace after the keyword
        val open = text.indexOf('{', kw + keyword.length)
        if open < 0 then i = text.length
        else
          val close = findMatchingBrace(text, open)
          if close < 0 then i = text.length
          else
            blocks += text.substring(open + 1, close)
            i = close + 1
    blocks.result()
  }

  /** Extract "new Xxx { ... }" expressions from text. */
  private def extractNewExprs(text: String): Seq[String] = {
    val exprs = Seq.newBuilder[String]
    var i = 0
    while i < text.length do
      // Look for "new " followed by a type name and opening brace
      val newKw = findKeyword(text, "new", i)
      if newKw < 0 then i = text.length
      else
        // Skip "new " and find the opening brace
        val afterNew = newKw + 3
        // Skip whitespace and type name to find {
        var j = afterNew
        while j < text.length && text(j) != '{' && text(j) != '}' do j += 1
        if j < text.length && text(j) == '{' then
          val close = findMatchingBrace(text, j)
          if close >= 0 then
            exprs += text.substring(newKw, close + 1)
            i = close + 1
          else i = text.length
        else i = j + 1
    exprs.result()
  }

  /** Extract plugin id from config text. Looks for "id = \"...\"" or uses the class name. */
  private def extractId(expr: String): String = {
    val idPattern = """id\s*=\s*"([^"]*)"""".r
    idPattern.findFirstMatchIn(expr).map(_.group(1)).getOrElse {
      // Fallback: extract class name after "new "
      val clsPattern = """new\s+(\S+Config)\s*\{""".r
      clsPattern.findFirstMatchIn(expr).map(_.group(1)).getOrElse("unknown")
    }
  }

  private def findKeyword(text: String, keyword: String, from: Int): Int = {
    var i = from
    while i < text.length - keyword.length do
      if text.substring(i, i + keyword.length) == keyword then
        // Must be at a word boundary
        val before = if i == 0 then ' ' else text(i - 1)
        val after = if i + keyword.length >= text.length then ' ' else text(i + keyword.length)
        if !before.isLetterOrDigit && (after == ' ' || after == '\n' || after == '\t' || after == '.' || after == '{') then
          return i
      i += 1
    -1
  }

  /** Find the matching closing brace for the opening brace at position `open`. */
  private def findMatchingBrace(text: String, open: Int): Int = {
    // skip strings to avoid matching braces inside strings
    var depth = 0
    var inString = false
    var inSingleLineComment = false
    var i = open
    while i < text.length do
      val c = text(i)
      if inSingleLineComment then
        if c == '\n' then inSingleLineComment = false
      else if inString then
        if c == '"' && text(i - 1) != '\\' then inString = false
      else
        c match
          case '/' if i + 1 < text.length && text(i + 1) == '/' => inSingleLineComment = true
          case '"' => inString = true
          case '{' => depth += 1
          case '}' =>
            depth -= 1
            if depth == 0 then return i
          case _ =>
      i += 1
    -1
  }

  /** Load all plugin implementations via ServiceLoader and collect their tasks.
   *  Passes Pkl config text to each plugin; the plugin uses its own Pkl evaluator to parse it.
   */
  def loadPlugins(
      pluginConfigTexts: Seq[(String, String)],
      pluginJarPaths: Seq[os.Path]
  ): Seq[AbstractTask[?]] = try {
    val pluginUrls = pluginJarPaths.map(_.toIO.toURI.toURL).toArray
    val pluginClassLoader = new URLClassLoader(pluginUrls, getClass.getClassLoader)
    val dederPluginClass = classOf[DederPlugin]

    pluginConfigTexts.flatMap { case (pluginId, configText) =>
      val serviceLoader = java.util.ServiceLoader.load(dederPluginClass, pluginClassLoader)
      val impls = serviceLoader.iterator().asScala.toSeq
      val matchingImpl = impls.find(_.id == pluginId)

      matchingImpl match {
        case Some(plugin) =>
          logger.info(s"Loaded plugin '${plugin.id}'")
          logger.debug(s"Plugin config Pkl text: $configText")
          val ts = plugin.tasks(coreTasksApi, configText)
          logger.debug(s"Plugin '${plugin.id}' contributed ${ts.size} tasks")
          ts
        case None =>
          logger.warn(
            s"No DederPlugin implementation found for id='$pluginId'. " +
            s"Available: ${impls.map(_.id).mkString(", ")}"
          )
          Seq.empty
      }
    }
  } catch {
    case e: Exception =>
      logger.error(s"Failed to load plugins: ${e.getMessage}", e)
      Seq.empty
  }

  def load(pklFile: os.Path): Either[String, Seq[AbstractTask[?]]] = {
    evaluatePhase1(pklFile) match {
      case Left(err) => Left(err)
      case Right(project) =>
        val depsWithScalaVer = extractPluginDeps(project)
        if depsWithScalaVer.isEmpty then return Right(Seq.empty)

        val allDepStrings = depsWithScalaVer.map(_._1)
        logger.info(s"Discovered plugin dependencies: ${allDepStrings.mkString(", ")}")

        val dependencies = depsWithScalaVer.map { case (depStr, scalaVer) =>
          Dependency.make(depStr, scalaVer)
        }
        val pluginJarPaths = try {
          dependencyResolver.fetchFiles(dependencies, None)
        } catch {
          case e: Exception =>
            logger.warn(s"Failed to resolve plugin dependencies: ${e.getMessage}", e)
            return Left(s"Failed to resolve plugin dependencies: ${e.getMessage}")
        }
        logger.debug(s"Resolved plugin JARs: ${pluginJarPaths.map(_.last).mkString(", ")}")

        // Extract plugin config as Pkl text
        val pklText = os.read(pklFile)
        val pluginConfigTexts = extractPluginConfigPkl(pklText)
        logger.debug(s"Extracted ${pluginConfigTexts.size} plugin config(s) as Pkl text")

        Right(loadPlugins(pluginConfigTexts, pluginJarPaths))
    }
  }
}

object PluginLoader {
  def extractPluginDeps(project: DederProject): Seq[(String, String)] = {
    import scala.jdk.CollectionConverters.*
    for {
      module <- project.modules.asScala.toSeq
      plugin <- Option(module.plugins).toSeq.flatMap(_.asScala)
      dep <- Option(plugin.deps).toSeq.flatMap(_.asScala)
    } yield {
      val scalaVer = module match {
        case sm: ScalaModule => sm.scalaVersion
        case _               => ""
      }
      (dep, scalaVer)
    }
  }

  def extractDeps(project: DederProject): Seq[String] =
    extractPluginDeps(project).map(_._1)
}
