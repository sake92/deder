package ba.sake.deder.plugin

import java.net.URLClassLoader
import java.security.MessageDigest
import scala.jdk.CollectionConverters.*
import scala.util.Using
import com.typesafe.scalalogging.StrictLogging
import ba.sake.deder.*
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.{DederPlugin, ScalaModule}
import ba.sake.deder.deps.{Dependency, DependencyResolverApi}

trait PluginLoaderApi {
  def extractPluginDeps(project: DederProject): Seq[(String, String)]
  def fingerprint(project: DederProject, pklFile: os.Path): Either[String, String]
  def load(pklFile: os.Path): Either[String, PluginLoader.PluginLoadResult]
}

class PluginLoader(
    coreTasksApi: CoreTasksApi,
    dependencyResolver: DependencyResolverApi
) extends PluginLoaderApi, StrictLogging {

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

  /** Serialize a single plugin config as JSON via Pkl's evaluator with OutputFormat.JSON.
   *  The plugin can re-evaluate this JSON via Pkl (since Pkl is a JSON superset) to get
   *  a typed config object.
   */
  def serializePluginConfig(pklFile: os.Path, pluginId: String): Either[String, String] = try {
    val snippet =
      s"""
         |import "${pklFile.toIO.toURI}" as cfg
         |
         |output {
         |  value = cfg.plugins.toList()
         |            .filter((it) -> it.id == "$pluginId")
         |            .first
         |            .toMap()
         |  .remove("id")
         |  .remove("deps")
         |  .toDynamic()
         |}
         |""".stripMargin
    val evaluator = org.pkl.core.EvaluatorBuilder.preconfigured()
      .setOutputFormat(org.pkl.core.OutputFormat.PCF)
      .build()
    val res = evaluator.evaluateOutputText(org.pkl.core.ModuleSource.text(snippet))
    Right(res)
  } catch {
    case e: Exception =>
      val msg = s"Failed to serialize plugin config for id='$pluginId': ${e.getMessage}"
      logger.warn(msg, e)
      Left(msg)
  }

  def fingerprint(project: DederProject, pklFile: os.Path): Either[String, String] = {
    val depsWithScalaVer = extractPluginDeps(project)
    val pluginIds = Option(project.plugins).toSeq.flatMap(_.asScala).map(_.id)
    PluginLoader.sequence(pluginIds.map(id => serializePluginConfig(pklFile, id).map(id -> _))).map { pluginConfigs =>
      // Keep the fingerprint input deterministic: serialized deps + serialized plugin config text.
      // Separators are intentionally uncommon to reduce accidental boundary ambiguity.
      val serializedDeps = depsWithScalaVer.map { case (dep, scalaVer) => s"$dep::$scalaVer" }.mkString("\n")
      val serializedCfgs = pluginConfigs.map { case (id, text) => s"$id::$text" }.mkString("\n---\n")
      PluginLoader.sha256(s"$serializedDeps\n===\n$serializedCfgs")
    }
  }

  /** Load all plugin implementations via ServiceLoader and collect their tasks. */
  def loadPlugins(
      pluginConfigs: Seq[(String, String)],
      pluginJarPaths: Seq[os.Path]
  ): Either[String, PluginLoader.PluginLoadResult] = try {
    if pluginConfigs.isEmpty then return Right(PluginLoader.PluginLoadResult(Seq.empty, None))
    val pluginUrls = pluginJarPaths.map(_.toIO.toURI.toURL).toArray
    val pluginClassLoader = new URLClassLoader(pluginUrls, getClass.getClassLoader)

    try {
      val tasks = pluginConfigs.flatMap { case (pluginId, configText) =>
        val serviceLoader = java.util.ServiceLoader.load(PluginLoader.DederPluginApiClass, pluginClassLoader)
        val impls = serviceLoader.iterator().asScala.toSeq
        val matchingImpl = impls.find(_.id == pluginId)

        matchingImpl match {
          case Some(plugin) =>
            logger.info(s"Loaded plugin '$pluginId'")
            logger.info(s"Plugin config Pkl text: $configText")
            val ts = plugin.tasks(coreTasksApi, configText)
            logger.info(s"Plugin '$pluginId' contributed ${ts.size} tasks")
            ts
          case None =>
            logger.warn(
              s"No DederPluginApi implementation found for id='$pluginId'. " +
              s"Available: ${impls.map(_.id).mkString(", ")}"
            )
            Seq.empty
        }
      }
      Right(PluginLoader.PluginLoadResult(tasks, Some(pluginClassLoader)))
    } catch {
      case e: Exception =>
        try pluginClassLoader.close()
        catch {
          case _: Exception =>
        }
        val msg = s"Failed to load plugins: ${e.getMessage}"
        logger.error(msg, e)
        Left(msg)
    }
  } catch {
    case e: Exception =>
      val msg = s"Failed to load plugins: ${e.getMessage}"
      logger.error(msg, e)
      Left(msg)
  }

  def load(pklFile: os.Path): Either[String, PluginLoader.PluginLoadResult] = {
    evaluatePhase1(pklFile) match {
      case Left(err) => Left(err)
      case Right(project) =>
        val depsWithScalaVer = extractPluginDeps(project)
        if depsWithScalaVer.isEmpty then return Right(PluginLoader.PluginLoadResult(Seq.empty, None))

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
        logger.info(s"Resolved plugin JARs: ${pluginJarPaths.map(_.last).mkString(", ")}")

        // Collect plugin ids from project-level plugins
        val pluginIds = Option(project.plugins).toSeq.flatMap(_.asScala).map(_.id)

        val pluginConfigs =
          PluginLoader.sequence(pluginIds.map(id => serializePluginConfig(pklFile, id).map(text => id -> text))) match {
            case Left(err) => return Left(err)
            case Right(values) => values
          }
        logger.debug(s"Serialized ${pluginConfigs.size} plugin config(s) as Pkl text")

        loadPlugins(pluginConfigs, pluginJarPaths)
    }
  }
}

object PluginLoader {
  case class PluginLoadResult(
      tasks: Seq[AbstractTask[?]],
      classLoader: Option[URLClassLoader]
  )

  val DederPluginApiClass = classOf[DederPluginApi]

  def extractPluginDeps(project: DederProject): Seq[(String, String)] = {
    import scala.jdk.CollectionConverters.*
    val scalaVer = project.modules.asScala.toSeq.collectFirst {
      case sm: ScalaModule => sm.scalaVersion
    }.getOrElse("")
    for {
      plugin <- Option(project.plugins).toSeq.flatMap(_.asScala)
      dep <- Option(plugin.deps).toSeq.flatMap(_.asScala)
    } yield (dep, scalaVer)
  }

  def extractDeps(project: DederProject): Seq[String] =
    extractPluginDeps(project).map(_._1)

  private[plugin] def sequence[A](values: Seq[Either[String, A]]): Either[String, Seq[A]] =
    values.foldLeft(Right(Seq.empty): Either[String, Seq[A]]) { (acc, current) =>
      for {
        xs <- acc
        x <- current
      } yield xs.appended(x)
    }

  private[plugin] def sha256(value: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.digest(value.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }
}
