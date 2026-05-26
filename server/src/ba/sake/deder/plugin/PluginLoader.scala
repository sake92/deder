package ba.sake.deder.plugin

import java.net.URLClassLoader
import java.security.MessageDigest
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*
import scala.util.{Using, Try}
import scala.util.control.NonFatal
import com.typesafe.scalalogging.StrictLogging
import ba.sake.deder.*
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.{DederPlugin, ScalaModule}
import ba.sake.deder.deps.{Dependency, DependencyResolverApi}

val DederPluginApiClass = classOf[DederPluginApi]

trait PluginLoaderApi {
  def load(loadedPlugins: Seq[LoadedPlugin], pklFile: os.Path, dederProject: DederProject): PluginsLoadResult
}

class PluginLoader(
    coreTasksApi: CoreTasksApi,
    scalaJsTasksApi: ScalaJsTasksApi,
    scalaNativeTasksApi: ScalaNativeTasksApi,
    dependencyResolver: DependencyResolverApi
) extends PluginLoaderApi,
      StrictLogging {

  private val scalaVer = "3.7.4" // deder scala version

  override def load(
      loadedPlugins: Seq[LoadedPlugin],
      pklFile: os.Path,
      dederProject: DederProject
  ): PluginsLoadResult = {
    val pluginConfigs = dederProject.plugins.asScala.toSeq
    loadConfiguredPlugins(loadedPlugins, pluginConfigs, pklFile)
  }

  private def loadConfiguredPlugins(
      loadedPlugins: Seq[LoadedPlugin],
      pluginConfigs: Seq[DederPlugin],
      pklFile: os.Path
  ): PluginsLoadResult = {
    // TODO optimize, unload only the necessary plugins, compare config hashes, etc
    loadedPlugins.foreach { loadedPlugin =>
      Try(loadedPlugin.plugin.onClose())
      closeClassLoaderQuietly(loadedPlugin.classLoader)
    }
    val newLoadedPlugins = pluginConfigs.flatMap { pluginConfig =>
      serializePluginConfig(pklFile, pluginConfig.id) match {
        case Left(err) =>
          logger.warn(s"Failed to serialize plugin config for '${pluginConfig.id}': $err. Skipping plugin.", err)
          None
        case Right(serializedPluginConfig) =>
          val pluginDeps = pluginConfig.deps.asScala.toSeq.map(depStr => Dependency.make(depStr, scalaVer))
          val pluginJarPaths = dependencyResolver.fetchFiles(pluginDeps, None)
          loadPluginFromPaths(
            pluginId = pluginConfig.id,
            configText = serializedPluginConfig,
            pluginJarPaths = pluginJarPaths
          ) match {
            case Left(err) =>
              logger.warn(s"Failed to load plugin '${pluginConfig.id}': $err. Skipping plugin.", err)
              None
            case Right(loadedPlugin) =>
              Some(loadedPlugin)
          }
      }
    }
    PluginsLoadResult(newLoadedPlugins)
  }

  /** Serialize a single plugin config as JSON via Pkl's evaluator with OutputFormat.JSON. The plugin can re-evaluate
    * this JSON via Pkl (since Pkl is a JSON superset) to get a typed config object.
    */
  private def serializePluginConfig(pklFile: os.Path, pluginId: String): Either[String, String] = try {
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
    val evaluator = org.pkl.core.EvaluatorBuilder
      .preconfigured()
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

  private def loadPluginFromPaths(
      pluginId: String,
      configText: String,
      pluginJarPaths: Seq[os.Path]
  ): Either[String, LoadedPlugin] = {
    val pluginUrls = pluginJarPaths.map(_.toIO.toURI.toURL).toArray
    val pluginClassLoader = new URLClassLoader(pluginUrls, getClass.getClassLoader)

    try {
      val serviceLoader = java.util.ServiceLoader.load(DederPluginApiClass, pluginClassLoader)
      val impls = serviceLoader.iterator().asScala.toSeq
      val matchingImpl = impls.find(_.id == pluginId)
      matchingImpl match {
        case Some(plugin) =>
          logger.debug(s"Loaded plugin '$pluginId'")
          logger.debug(s"Plugin config Pkl text: $configText")
          val params = PluginTasksParams(configText, coreTasksApi, scalaJsTasksApi, scalaNativeTasksApi)
          plugin.tasks(params) match {
            case Left(err) =>
              logger.warn(s"Failed to get tasks from plugin '$pluginId': $err")
              closeClassLoaderQuietly(pluginClassLoader)
              Left(s"Failed to get tasks from plugin '$pluginId': $err")
            case Right(tasks) =>
              // TODO validate task names, check for duplicates across plugins, etc
              logger.debug(s"Plugin '$pluginId' contributed ${tasks.size} tasks: ${tasks.map(_.name).mkString(", ")}")
              Right(LoadedPlugin(plugin, configText, tasks, pluginClassLoader))
          }
        case None =>
          closeClassLoaderQuietly(pluginClassLoader)
          Left(
            s"No DederPluginApi implementation found for id='$pluginId'. " +
              s"Available: ${impls.map(_.id).mkString(", ")}"
          )
      }
    } catch {
      case NonFatal(e) =>
        closeClassLoaderQuietly(pluginClassLoader)
        val msg = s"Failed to load plugin '$pluginId'"
        logger.error(msg, e)
        Left(msg)
      // NonFatal(_) doesnt cover these class loading errors:
      case e: java.util.ServiceConfigurationError =>
        closeClassLoaderQuietly(pluginClassLoader)
        val msg = s"Failed to load plugin '$pluginId'"
        logger.error(msg, e)
        Left(msg)
      case e: LinkageError =>
        closeClassLoaderQuietly(pluginClassLoader)
        val msg = s"Failed to load plugin '$pluginId'"
        logger.error(msg, e)
        Left(msg)
    }
  }

}

case class LoadedPlugin(
    plugin: DederPluginApi,
    configText: String,
    tasks: Seq[AbstractTask[?]],
    classLoader: URLClassLoader
) {
  def closeClassLoader(): Unit =
    closeClassLoaderQuietly(classLoader)
}

case class PluginsLoadResult(
    loadedPlugins: Seq[LoadedPlugin]
)

private def closeClassLoaderQuietly(pluginClassLoader: URLClassLoader): Unit =
  try pluginClassLoader.close()
  catch case NonFatal(_) => ()
