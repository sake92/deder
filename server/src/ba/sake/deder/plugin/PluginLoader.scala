package ba.sake.deder.plugin

import java.net.URLClassLoader
import java.security.MessageDigest
import scala.annotation.tailrec
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
    val serializedConfigs = PluginLoader.sequence(pluginIds.map { id =>
      serializePluginConfig(pklFile, id).map(configText => (id, configText))
    })
    serializedConfigs.map { pluginConfigs =>
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
    if pluginConfigs.isEmpty then return Right(PluginLoader.PluginLoadResult(Seq.empty))
    inspectPluginJarPathInfos(pluginJarPaths) match {
      case Left(err) => Left(err)
      case Right(pluginJarPathInfos) =>
        loadPluginsIndividually(pluginConfigs.toList, pluginJarPathInfos, Vector.empty)
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
        val projectPlugins = Option(project.plugins).toSeq.flatMap(_.asScala)
        if projectPlugins.isEmpty then return Right(PluginLoader.PluginLoadResult(Seq.empty))

        val scalaVer = project.modules.asScala.toSeq.collectFirst {
          case sm: ScalaModule => sm.scalaVersion
        }.getOrElse("")
        loadConfiguredPlugins(projectPlugins.toList, scalaVer, pklFile, Vector.empty)
    }
  }

  @tailrec
  private def loadPluginsIndividually(
      remainingPluginConfigs: List[(String, String)],
      pluginJarPathInfos: Seq[PluginLoader.PluginJarPathInfo],
      loadedPlugins: Vector[PluginLoader.LoadedPlugin]
  ): Either[String, PluginLoader.PluginLoadResult] =
    remainingPluginConfigs match {
      case Nil =>
        Right(PluginLoader.PluginLoadResult(loadedPlugins))
      case (pluginId, configText) :: tail =>
        findPluginJarPaths(pluginId, pluginJarPathInfos) match {
          case Left(err) =>
            closeLoadedPlugins(loadedPlugins)
            Left(err)
          case Right(None) =>
            loadPluginsIndividually(tail, pluginJarPathInfos, loadedPlugins)
          case Right(Some(paths)) =>
            loadPluginFromPaths(pluginId, configText, paths) match {
              case Left(err) =>
                closeLoadedPlugins(loadedPlugins)
                Left(err)
              case Right(None) =>
                loadPluginsIndividually(tail, pluginJarPathInfos, loadedPlugins)
              case Right(Some(loadedPlugin)) =>
                loadPluginsIndividually(tail, pluginJarPathInfos, loadedPlugins :+ loadedPlugin)
            }
        }
    }

  @tailrec
  private def loadConfiguredPlugins(
      remainingPlugins: List[DederPlugin],
      scalaVer: String,
      pklFile: os.Path,
      loadedPlugins: Vector[PluginLoader.LoadedPlugin]
  ): Either[String, PluginLoader.PluginLoadResult] =
    remainingPlugins match {
      case Nil =>
        Right(PluginLoader.PluginLoadResult(loadedPlugins))
      case plugin :: tail =>
        val pluginId = plugin.id
        serializePluginConfig(pklFile, pluginId) match {
          case Left(err) =>
            closeLoadedPlugins(loadedPlugins)
            Left(err)
          case Right(configText) =>
            logger.debug(s"Serialized plugin config for '$pluginId' as Pkl text")
            val depStrings = Option(plugin.deps).toSeq.flatMap(_.asScala)
            logger.info(s"Discovered plugin dependencies for '$pluginId': ${depStrings.mkString(", ")}")
            val dependencies = depStrings.map(depStr => Dependency.make(depStr, scalaVer))
            resolvePluginJarPaths(pluginId, dependencies) match {
              case Left(err) =>
                closeLoadedPlugins(loadedPlugins)
                Left(err)
              case Right(pluginJarPaths) =>
                logger.info(s"Resolved plugin JARs for '$pluginId': ${pluginJarPaths.map(_.last).mkString(", ")}")
                loadPluginFromPaths(pluginId, configText, pluginJarPaths) match {
                  case Left(err) =>
                    closeLoadedPlugins(loadedPlugins)
                    Left(err)
                  case Right(None) =>
                    loadConfiguredPlugins(tail, scalaVer, pklFile, loadedPlugins)
                  case Right(Some(loadedPlugin)) =>
                    loadConfiguredPlugins(tail, scalaVer, pklFile, loadedPlugins :+ loadedPlugin)
                }
            }
        }
    }

  private def resolvePluginJarPaths(
      pluginId: String,
      dependencies: Seq[Dependency]
  ): Either[String, Seq[os.Path]] = try {
    Right(dependencyResolver.fetchFiles(dependencies, None))
  } catch {
    case e: Exception =>
      logger.warn(s"Failed to resolve plugin dependencies for '$pluginId': ${e.getMessage}", e)
      Left(s"Failed to resolve plugin dependencies for '$pluginId': ${e.getMessage}")
  }

  private def findPluginJarPaths(
      pluginId: String,
      pluginJarPathInfos: Seq[PluginLoader.PluginJarPathInfo]
  ): Either[String, Option[Seq[os.Path]]] =
    val availablePluginIds = pluginJarPathInfos.flatMap(_.pluginIds).distinct
    val matchingPluginJarPaths = pluginJarPathInfos.collect {
      case pluginJarPathInfo if
            pluginJarPathInfo.pluginIds.isEmpty || pluginJarPathInfo.pluginIds.contains(pluginId) =>
        pluginJarPathInfo.path
    }
    if pluginJarPathInfos.exists(_.pluginIds.contains(pluginId)) then
      Right(Some(matchingPluginJarPaths))
    else
      logger.warn(
        s"No DederPluginApi implementation found for id='$pluginId'. " +
        s"Available: ${availablePluginIds.mkString(", ")}"
      )
      Right(None)

  private def inspectPluginJarPathInfos(
      pluginJarPaths: Seq[os.Path]
  ): Either[String, Seq[PluginLoader.PluginJarPathInfo]] =
    PluginLoader.sequence(pluginJarPaths.map(inspectPluginJarPathInfo))

  private def inspectPluginJarPathInfo(
      pluginJarPath: os.Path
  ): Either[String, PluginLoader.PluginJarPathInfo] =
    inspectPluginIds(Seq(pluginJarPath), pluginJarPath.last)
      .map(pluginIds => PluginLoader.PluginJarPathInfo(pluginJarPath, pluginIds))

  private def inspectPluginIds(
      pluginJarPaths: Seq[os.Path],
      inspectionLabel: String
  ): Either[String, Seq[String]] = {
    val pluginUrls = pluginJarPaths.map(_.toIO.toURI.toURL).toArray
    val pluginClassLoader = new URLClassLoader(pluginUrls, getClass.getClassLoader)
    try {
      val serviceLoader = java.util.ServiceLoader.load(PluginLoader.DederPluginApiClass, pluginClassLoader)
      Right(serviceLoader.iterator().asScala.toSeq.map(_.id).distinct)
    } catch {
      case e: Exception =>
        val msg = s"Failed to inspect plugin '$inspectionLabel': ${e.getMessage}"
        logger.error(msg, e)
        Left(msg)
      case e: java.util.ServiceConfigurationError =>
        val msg = s"Failed to inspect plugin '$inspectionLabel': ${e.getMessage}"
        logger.error(msg, e)
        Left(msg)
      case e: LinkageError =>
        val msg = s"Failed to inspect plugin '$inspectionLabel': ${e.getMessage}"
        logger.error(msg, e)
        Left(msg)
    } finally {
      closeClassLoaderQuietly(pluginClassLoader)
    }
  }

  private def loadPluginFromPaths(
      pluginId: String,
      configText: String,
      pluginJarPaths: Seq[os.Path]
  ): Either[String, Option[PluginLoader.LoadedPlugin]] = {
    val pluginUrls = pluginJarPaths.map(_.toIO.toURI.toURL).toArray
    val pluginClassLoader = new URLClassLoader(pluginUrls, getClass.getClassLoader)

    try {
      val serviceLoader = java.util.ServiceLoader.load(PluginLoader.DederPluginApiClass, pluginClassLoader)
      val impls = serviceLoader.iterator().asScala.toSeq
      val matchingImpl = impls.find(_.id == pluginId)

      matchingImpl match {
        case Some(plugin) =>
          logger.info(s"Loaded plugin '$pluginId'")
          logger.info(s"Plugin config Pkl text: $configText")
          val tasks = plugin.tasks(coreTasksApi, configText)
          logger.info(s"Plugin '$pluginId' contributed ${tasks.size} tasks")
          Right(Some(PluginLoader.LoadedPlugin(pluginId, tasks, pluginClassLoader)))
        case None =>
          logger.warn(
            s"No DederPluginApi implementation found for id='$pluginId'. " +
            s"Available: ${impls.map(_.id).mkString(", ")}"
          )
          closeClassLoaderQuietly(pluginClassLoader)
          Right(None)
      }
    } catch {
      case e: Exception =>
        closeClassLoaderQuietly(pluginClassLoader)
        val msg = s"Failed to load plugin '$pluginId': ${e.getMessage}"
        logger.error(msg, e)
        Left(msg)
      case e: java.util.ServiceConfigurationError =>
        closeClassLoaderQuietly(pluginClassLoader)
        val msg = s"Failed to load plugin '$pluginId': ${e.getMessage}"
        logger.error(msg, e)
        Left(msg)
      case e: LinkageError =>
        closeClassLoaderQuietly(pluginClassLoader)
        val msg = s"Failed to load plugin '$pluginId': ${e.getMessage}"
        logger.error(msg, e)
        Left(msg)
    }
  }

  private def closeLoadedPlugins(loadedPlugins: Seq[PluginLoader.LoadedPlugin]): Unit =
    loadedPlugins.foreach(loadedPlugin => closeClassLoaderQuietly(loadedPlugin.classLoader))

  private def closeClassLoaderQuietly(pluginClassLoader: URLClassLoader): Unit =
    try pluginClassLoader.close()
    catch {
      case _: Exception =>
    }
}

object PluginLoader {
  case class PluginJarPathInfo(
      path: os.Path,
      pluginIds: Seq[String]
  )

  case class LoadedPlugin(
      pluginId: String,
      tasks: Seq[AbstractTask[?]],
      classLoader: URLClassLoader
  )

  final class PluginLoadResult private (
      val loadedPlugins: Seq[LoadedPlugin]
  ) {
    lazy val tasks: Seq[AbstractTask[?]] =
      loadedPlugins.flatMap(_.tasks)
    lazy val classLoaders: Seq[URLClassLoader] =
      loadedPlugins.map(_.classLoader)
  }

  object PluginLoadResult {
    def apply(loadedPlugins: Seq[LoadedPlugin]): PluginLoadResult =
      new PluginLoadResult(loadedPlugins)
  }

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
    val hex = new StringBuilder(64)
    digest.digest(value.getBytes("UTF-8")).foreach { b =>
      hex.append(f"${b & 0xff}%02x")
    }
    hex.toString()
  }
}
