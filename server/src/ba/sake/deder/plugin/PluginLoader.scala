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

  /** Serialize a single plugin config as JSON via Pkl's evaluator with OutputFormat.JSON.
   *  The plugin can re-evaluate this JSON via Pkl (since Pkl is a JSON superset) to get
   *  a typed config object.
   */
  def serializePluginConfig(pklFile: os.Path, pluginId: String): Option[String] = try {
    val snippet =
      s"""
         |import "${pklFile.toIO.toURI}" as cfg
         |
         |output {
         |  value = (cfg.plugins.toList() + cfg.modules.toList().flatMap((it) -> it.plugins.toList()))
         |            .filter((it) -> it.id == "$pluginId")
         |            .first
         |}
         |""".stripMargin
    val evaluator = org.pkl.core.EvaluatorBuilder.preconfigured()
      .setOutputFormat(org.pkl.core.OutputFormat.JSON)
      .build()
      val res = evaluator.evaluateOutputText(org.pkl.core.ModuleSource.text(snippet))
    Some(res)
  } catch {
    case e: Exception =>
      logger.warn(s"Failed to serialize plugin config for id='$pluginId': ${e.getMessage}", e)
      None
  }

  /** Load all plugin implementations via ServiceLoader and collect their tasks. */
  def loadPlugins(
      pluginConfigs: Seq[(String, String)],
      pluginJarPaths: Seq[os.Path]
  ): Seq[AbstractTask[?]] = try {
    val pluginUrls = pluginJarPaths.map(_.toIO.toURI.toURL).toArray
    val pluginClassLoader = new URLClassLoader(pluginUrls, getClass.getClassLoader)
    val dederPluginClass = classOf[DederPlugin]

    pluginConfigs.flatMap { case (pluginId, configText) =>
      val serviceLoader = java.util.ServiceLoader.load(dederPluginClass, pluginClassLoader)
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
        logger.info(s"Resolved plugin JARs: ${pluginJarPaths.map(_.last).mkString(", ")}")

        // Collect plugin ids from both project-level and module-level plugins
        val projectPlugins = Option(project.plugins).toSeq.flatMap(_.asScala)
        val modulePlugins = for {
          module <- project.modules.asScala.toSeq
          plugin <- Option(module.plugins).toSeq.flatMap(_.asScala)
        } yield plugin
        val pluginIds = (projectPlugins ++ modulePlugins).map(_.id)

        val pluginConfigs = pluginIds.flatMap { id =>
          serializePluginConfig(pklFile, id).map(text => id -> text)
        }
        logger.debug(s"Serialized ${pluginConfigs.size} plugin config(s) as Pkl text")

        Right(loadPlugins(pluginConfigs, pluginJarPaths))
    }
  }
}

object PluginLoader {
  def extractPluginDeps(project: DederProject): Seq[(String, String)] = {
    import scala.jdk.CollectionConverters.*
    // Project-level plugins (no scala version context)
    val projectDeps = for {
      plugin <- Option(project.plugins).toSeq.flatMap(_.asScala)
      dep <- Option(plugin.deps).toSeq.flatMap(_.asScala)
    } yield (dep, "")

    // Module-level plugins (scala version from the module)
    val moduleDeps = for {
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

    projectDeps ++ moduleDeps
  }

  def extractDeps(project: DederProject): Seq[String] =
    extractPluginDeps(project).map(_._1)
}
