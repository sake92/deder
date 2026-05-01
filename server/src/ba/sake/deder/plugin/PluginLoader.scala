package ba.sake.deder.plugin

import java.net.URLClassLoader
import scala.jdk.CollectionConverters.*
import scala.util.Using
import com.typesafe.scalalogging.StrictLogging
import ba.sake.deder.*
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.{DederModule, Plugin}
import ba.sake.deder.deps.{Dependency, DependencyResolverApi}

class PluginLoader(
    coreTasksApi: CoreTasksApi,
    dependencyResolver: DependencyResolverApi
) extends StrictLogging {

  /** Extract all plugin deps from the project config. */
  def extractDeps(project: DederProject): Seq[String] = PluginLoader.extractDeps(project)

  /** Phase 1: Evaluate deder.pkl minimally (no plugin JARs) to extract plugin deps.
   *  This works because Plugin base class is always on the classpath (in config/),
   *  and Pkl maps plugin subclass instances to the base Plugin type for the `plugins` field.
   */
  def evaluatePhase1(pklFile: os.Path): Either[String, Seq[String]] = try {
    val moduleSource = org.pkl.core.ModuleSource.file(pklFile.toIO)
    val project = Using.resource(org.pkl.config.java.ConfigEvaluator.preconfigured) { evaluator =>
      evaluator.evaluate(moduleSource).as(classOf[DederProject])
    }
    Right(extractDeps(project))
  } catch {
    case e: Exception =>
      logger.warn(s"Phase 1 evaluation failed: ${e.getMessage}", e)
      Left(s"Phase 1 evaluation failed: ${e.getMessage}")
  }

  /** Phase 2: Full evaluation with plugin JARs on classpath.
   *  Uses thread context classloader to make plugin JAR classes visible to Pkl.
   */
  def evaluatePhase2(pklFile: os.Path, pluginJarPaths: Seq[os.Path]): Either[String, DederProject] = try {
    val pluginUrls = pluginJarPaths.map(_.toIO.toURI.toURL).toArray
    val pluginClassLoader = new URLClassLoader(pluginUrls, getClass.getClassLoader)

    val originalTCCL = Thread.currentThread().getContextClassLoader
    try {
      Thread.currentThread().setContextClassLoader(pluginClassLoader)

      val builder = org.pkl.config.java.ConfigEvaluatorBuilder.preconfigured()
      builder.getEvaluatorBuilder()
        .addModuleKeyFactory(org.pkl.core.module.ModuleKeyFactories.classPath(pluginClassLoader))

      val evaluator = builder.build()
      val moduleSource = org.pkl.core.ModuleSource.file(pklFile.toIO)
      val project = evaluator.evaluate(moduleSource).as(classOf[DederProject])
      Right(project)
    } finally {
      Thread.currentThread().setContextClassLoader(originalTCCL)
    }
  } catch {
    case e: Exception =>
      logger.warn(s"Phase 2 evaluation failed: ${e.getMessage}", e)
      Left(s"Phase 2 evaluation failed: ${e.getMessage}")
  }

  /** Phase 3: Load all plugin implementations via ServiceLoader and collect their tasks. */
  def loadPlugins(
      project: DederProject,
      pluginJarPaths: Seq[os.Path]
  ): Seq[AbstractTask[?]] = try {
    val pluginUrls = pluginJarPaths.map(_.toIO.toURI.toURL).toArray
    val pluginClassLoader = new URLClassLoader(pluginUrls, getClass.getClassLoader)

    val dederPluginClass = classOf[DederPlugin]

    project.modules.asScala.toSeq.flatMap { module =>
      Option(module.plugins).toSeq.flatMap(_.asScala).flatMap { pluginConfig =>
        val serviceLoader = java.util.ServiceLoader.load(dederPluginClass, pluginClassLoader)
        val impls = serviceLoader.iterator().asScala.toSeq
        val matchingImpl = impls.find(_.id == pluginConfig.id)

        matchingImpl match {
          case Some(plugin) =>
            logger.info(s"Loaded plugin '${plugin.id}' for module '${module.id}'")
            val tasks = plugin.tasks(coreTasksApi, pluginConfig)
            logger.debug(s"Plugin '${plugin.id}' contributed ${tasks.size} tasks")
            tasks
          case None =>
            logger.warn(
              s"No DederPlugin implementation found for id='${pluginConfig.id}' " +
              s"in module '${module.id}'. Available implementations: ${impls.map(_.id).mkString(", ")}"
            )
            Seq.empty
        }
      }
    }
  } catch {
    case e: Exception =>
      logger.error(s"Failed to load plugins: ${e.getMessage}", e)
      Seq.empty
  }

  /** Full load pipeline: phase 1 -> resolve deps -> phase 2 -> load plugins.
   *  Returns all plugin-contributed tasks.
   */
  def load(pklFile: os.Path): Either[String, Seq[AbstractTask[?]]] = {
    evaluatePhase1(pklFile) match {
      case Left(err) => Left(err)
      case Right(allDeps) if allDeps.isEmpty =>
        Right(Seq.empty) // no plugins declared
      case Right(allDeps) =>
        logger.info(s"Discovered plugin dependencies: ${allDeps.mkString(", ")}")

        // Resolve plugin JARs. Plugin deps are standard Maven coordinates (single colon).
        val dependencies = allDeps.map(Dependency.make(_, ""))
        val pluginJarPaths = try {
          dependencyResolver.fetchFiles(dependencies, None)
        } catch {
          case e: Exception =>
            logger.warn(s"Failed to resolve plugin dependencies: ${e.getMessage}", e)
            return Left(s"Failed to resolve plugin dependencies: ${e.getMessage}")
        }

        logger.debug(s"Resolved plugin JARs: ${pluginJarPaths.map(_.last).mkString(", ")}")

        // Phase 2: full evaluation with plugin JARs on classpath
        evaluatePhase2(pklFile, pluginJarPaths) match {
          case Left(err) => Left(err)
          case Right(project) =>
            // Phase 3: load plugins and collect tasks
            Right(loadPlugins(project, pluginJarPaths))
        }
    }
  }
}

object PluginLoader {
  /** Extract plugin deps from all modules in the project config. */
  def extractDeps(project: DederProject): Seq[String] = {
    import scala.jdk.CollectionConverters.*
    for {
      module <- project.modules.asScala.toSeq
      plugin <- Option(module.plugins).toSeq.flatMap(_.asScala)
      dep <- Option(plugin.deps).toSeq.flatMap(_.asScala)
    } yield dep
  }
}
