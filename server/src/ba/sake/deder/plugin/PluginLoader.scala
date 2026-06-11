package ba.sake.deder.plugin

import java.net.URLClassLoader
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*
import scala.util.{Using, Try}
import scala.util.control.NonFatal
import com.typesafe.scalalogging.StrictLogging
import ba.sake.deder.*
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederPlugins.DederPlugin
import ba.sake.deder.deps.{Dependency, DependencyResolverApi}

val DederPluginApiClass = classOf[DederPluginApi]

trait PluginLoaderApi {
  def load(loadedPlugins: Seq[LoadedPlugin], pklFile: os.Path, dederProject: DederProject): PluginsLoadResult
}

class PluginLoader(
    coreTasksApi: CoreTasksApi,
    scalaJsTasksApi: ScalaJsTasksApi,
    scalaNativeTasksApi: ScalaNativeTasksApi,
    dependencyResolver: DependencyResolverApi,
    internals: DederProjectInternals
) extends PluginLoaderApi,
      StrictLogging {

  private val scalaVer = "3.7.4" // deder scala version

  override def load(
      loadedPlugins: Seq[LoadedPlugin],
      pklFile: os.Path,
      dederProject: DederProject
  ): PluginsLoadResult = {
    val pluginConfigs = dederProject.plugins.asScala.toSeq
    loadConfiguredPlugins(loadedPlugins, pluginConfigs, pklFile, dederProject)
  }

  private def loadConfiguredPlugins(
      loadedPlugins: Seq[LoadedPlugin],
      pluginConfigs: Seq[DederPlugin],
      pklFile: os.Path,
      dederProject: DederProject
  ): PluginsLoadResult = {
    // Step 1: Serialize all new configs and compute hashes
    val serializedMap: Map[String, (String, Seq[String], String)] = pluginConfigs.flatMap { cfg =>
      serializePluginConfig(pklFile, cfg.id) match {
        case Left(err) =>
          logger.warn(s"Failed to serialize plugin config for '${cfg.id}': $err. Skipping plugin.")
          None
        case Right(configText) =>
          val rawDeps = cfg.deps.asScala.toSeq
          val hash = ba.sake.deder.HashUtils.hashStr(configText + rawDeps.sorted.mkString("\n"))
          Some(cfg.id -> (configText, rawDeps, hash))
      }
    }.toMap

    // Step 2: Partition existing plugins — keep unchanged, unload changed/removed
    val newHashes = serializedMap.view.mapValues(_._3).toMap
    val (toKeep, toUnload) = partitionPlugins(loadedPlugins, newHashes)

    // Step 3: Unload changed or removed plugins
    toUnload.foreach { loadedPlugin =>
      Try(loadedPlugin.plugin.close())
      closeClassLoaderQuietly(loadedPlugin.classLoader)
    }

    // Step 4: Determine which configs to load (new or changed)
    val keepIds = toKeep.map(_.plugin.id).toSet
    val toLoadConfigs = serializedMap.filterKeys(id => !keepIds.contains(id))

    // Step 5: Load new/changed plugins
    val toLoad = toLoadConfigs.flatMap { case (pluginId, (configText, rawDeps, hash)) =>
      val pluginDeps = rawDeps.map(depStr => Dependency.make(depStr, scalaVer))
      val pluginJarPaths = dependencyResolver.fetchFiles(pluginDeps, None)
      loadPluginFromPaths(
        pluginId = pluginId,
        configText = configText,
        deps = rawDeps,
        configHash = hash,
        pluginJarPaths = pluginJarPaths,
        dederProject = dederProject
      ) match {
        case Left(err) =>
          logger.warn(s"Failed to load plugin '$pluginId': $err. Skipping plugin.")
          None
        case Right(loadedPlugin) =>
          Some(loadedPlugin)
      }
    }

    PluginsLoadResult(toKeep ++ toLoad)
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
         |  .filter((key, _) -> key == "config")
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
      deps: Seq[String],
      configHash: String,
      pluginJarPaths: Seq[os.Path],
      dederProject: DederProject
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
          val params = PluginInitParams(configText, coreTasksApi, scalaJsTasksApi, scalaNativeTasksApi, internals, dederProject)
          plugin.init(params) match {
            case Left(err) =>
              logger.warn(s"Failed to init plugin '$pluginId': $err")
              Try(plugin.close())
              closeClassLoaderQuietly(pluginClassLoader)
              Left(s"Failed to init plugin '$pluginId': $err")
            case Right(tasks) =>
              // TODO validate task names, check for duplicates across plugins, etc
              logger.debug(s"Plugin '$pluginId' contributed ${tasks.size} tasks: ${tasks.map(_.name).mkString(", ")}")
              Right(LoadedPlugin(plugin, configText, deps, configHash, tasks, pluginClassLoader))
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
    deps: Seq[String],
    configHash: String,
    tasks: Seq[AbstractTask[?]],
    classLoader: URLClassLoader
) {
  def closeClassLoader(): Unit =
    closeClassLoaderQuietly(classLoader)
}

case class PluginsLoadResult(
    loadedPlugins: Seq[LoadedPlugin]
)

private[deder] def partitionPlugins(
    existing: Seq[LoadedPlugin],
    newConfigHashes: Map[String, String]
): (Seq[LoadedPlugin], Seq[LoadedPlugin]) = {
  val (toKeep, toUnload) = existing.partition { loaded =>
    newConfigHashes.get(loaded.plugin.id).contains(loaded.configHash)
  }
  (toKeep, toUnload)
}

private def closeClassLoaderQuietly(pluginClassLoader: URLClassLoader): Unit =
  try pluginClassLoader.close()
  catch case NonFatal(_) => ()
