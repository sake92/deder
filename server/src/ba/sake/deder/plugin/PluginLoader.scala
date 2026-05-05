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

  /** Extract plugin deps with their module's Scala version for proper `::` resolution. */
  def extractPluginDeps(project: DederProject): Seq[(String, String)] = PluginLoader.extractPluginDeps(project)

  /** Phase 1: Evaluate deder.pkl minimally (no plugin JARs) to get project config.
   *  This works because Plugin base class is always on the classpath (in config/),
   *  and Pkl maps plugin subclass instances to the base Plugin type for the `plugins` field.
   */
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

  /** Phase 2 + 3 combined: evaluate Pkl with plugin JARs on classpath AND load plugins via ServiceLoader,
   *  all using the SAME URLClassLoader so typed config subclasses (e.g. HelloConfig) are compatible.
   */
  def evaluateAndLoadPlugins(
      pklFile: os.Path,
      pluginJarPaths: Seq[os.Path]
  ): Either[String, Seq[AbstractTask[?]]] = try {
    val pluginUrls = pluginJarPaths.map(_.toIO.toURI.toURL).toArray
    // SINGLE classloader instance used for both Pkl evaluation and ServiceLoader
    val pluginClassLoader = new URLClassLoader(pluginUrls, getClass.getClassLoader)

    val originalTCCL = Thread.currentThread().getContextClassLoader
    try {
      Thread.currentThread().setContextClassLoader(pluginClassLoader)

      // Phase 2: Full Pkl evaluation with plugin JARs available (typed Plugin subclasses resolved)
      val builder = org.pkl.config.java.ConfigEvaluatorBuilder.preconfigured()
      builder.getEvaluatorBuilder()
        .addModuleKeyFactory(org.pkl.core.module.ModuleKeyFactories.classPath(pluginClassLoader))

      val evaluator = builder.build()
      val moduleSource = org.pkl.core.ModuleSource.file(pklFile.toIO)
      val project = evaluator.evaluate(moduleSource).as(classOf[DederProject])

      // Phase 3: ServiceLoader (same classloader → same HelloConfig class → no ClassCastException)
      val dederPluginClass = classOf[DederPlugin]
      val tasks = project.modules.asScala.toSeq.flatMap { module =>
        Option(module.plugins).toSeq.flatMap(_.asScala).flatMap { pluginConfig =>
          val serviceLoader = java.util.ServiceLoader.load(dederPluginClass, pluginClassLoader)
          val impls = serviceLoader.iterator().asScala.toSeq
          val matchingImpl = impls.find(_.id == pluginConfig.id)

          matchingImpl match {
            case Some(plugin) =>
              logger.info(s"Loaded plugin '${plugin.id}' for module '${module.id}'")
              val ts = plugin.tasks(coreTasksApi, pluginConfig)
              logger.debug(s"Plugin '${plugin.id}' contributed ${ts.size} tasks")
              ts
            case None =>
              logger.warn(
                s"No DederPlugin implementation found for id='${pluginConfig.id}' " +
                s"in module '${module.id}'. Available implementations: ${impls.map(_.id).mkString(", ")}"
              )
              Seq.empty
          }
        }
      }
      Right(tasks)
    } finally {
      Thread.currentThread().setContextClassLoader(originalTCCL)
    }
  } catch {
    case e: Exception =>
      logger.warn(s"Plugin evaluation/loading failed: ${e.getMessage}", e)
      Left(s"Plugin evaluation/loading failed: ${e.getMessage}")
  }

  /** Full load pipeline: phase 1 -> resolve deps -> phase 2+3. */
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

        evaluateAndLoadPlugins(pklFile, pluginJarPaths)
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
