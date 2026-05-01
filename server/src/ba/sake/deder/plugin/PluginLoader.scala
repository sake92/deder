package ba.sake.deder.plugin

import java.net.URLClassLoader
import scala.jdk.CollectionConverters.*
import com.typesafe.scalalogging.StrictLogging
import ba.sake.deder.*
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.{DederModule, Plugin}
import ba.sake.deder.deps.{Dependency, DependencyResolverApi}

class PluginLoader(
    coreTasksApi: CoreTasksApi,
    dependencyResolver: DependencyResolverApi,
    projectRootDir: os.Path
) extends StrictLogging {

  /** Extract all plugin deps from the project config. */
  def extractDeps(project: DederProject): Seq[String] =
    for {
      module <- project.modules.asScala.toSeq
      plugin <- Option(module.plugins).toSeq.flatMap(_.asScala)
      dep <- Option(plugin.deps).toSeq.flatMap(_.asScala)
    } yield dep

  /** Phase 1: Evaluate deder.pkl minimally (no plugin JARs) to extract plugin deps. */
  def evaluatePhase1(pklFile: os.Path): Either[String, Seq[String]] = try {
    val evaluator = org.pkl.config.java.ConfigEvaluator.preconfigured
    val moduleSource = org.pkl.core.ModuleSource.file(pklFile.toIO)
    val project = evaluator.evaluate(moduleSource).as(classOf[DederProject])
    Right(extractDeps(project))
  } catch {
    case e: Exception =>
      Left(s"Phase 1 evaluation failed: ${e.getMessage}")
  }

  /** Phase 2: Full evaluation with plugin JARs on classpath. */
  def evaluatePhase2(pklFile: os.Path, pluginJarPaths: Seq[os.Path]): Either[String, DederProject] = try {
    val sysLoader = ClassLoader.getSystemClassLoader().asInstanceOf[URLClassLoader]
    val addUrlMethod = classOf[URLClassLoader].getDeclaredMethod("addURL", classOf[java.net.URL])
    addUrlMethod.setAccessible(true)
    val alreadyAdded = sysLoader.getURLs().map(_.toURI()).toSet
    pluginJarPaths.foreach { jar =>
      val jarUrl = jar.toIO.toURI.toURL
      if (!alreadyAdded.contains(jarUrl.toURI)) {
        addUrlMethod.invoke(sysLoader, jarUrl)
        logger.debug(s"Added plugin JAR to classpath: $jar")
      }
    }

    val pluginClassLoader = new URLClassLoader(
      pluginJarPaths.map(_.toIO.toURI.toURL).toArray,
      getClass.getClassLoader
    )

    val builder = org.pkl.config.java.ConfigEvaluatorBuilder.preconfigured()
    builder.getEvaluatorBuilder()
      .addModuleKeyFactory(org.pkl.core.module.ModuleKeyFactories.classPath(pluginClassLoader))

    val evaluator = builder.build()
    val moduleSource = org.pkl.core.ModuleSource.file(pklFile.toIO)
    val project = evaluator.evaluate(moduleSource).as(classOf[DederProject])
    Right(project)
  } catch {
    case e: Exception =>
      Left(s"Phase 2 evaluation failed: ${e.getMessage}")
  }

  /** Phase 3: Load all plugin implementations via ServiceLoader and collect their tasks. */
  def loadPlugins(
      project: DederProject,
      pluginJarPaths: Seq[os.Path]
  ): Seq[AbstractTask[?]] = {
    val pluginClassLoader = new URLClassLoader(
      pluginJarPaths.map(_.toIO.toURI.toURL).toArray,
      getClass.getClassLoader
    )

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
  }

  /** Full load pipeline: phase 1 -> resolve deps -> phase 2 -> load plugins. */
  def load(pklFile: os.Path): Either[String, Seq[AbstractTask[?]]] = {
    evaluatePhase1(pklFile) match {
      case Left(err) => Left(err)
      case Right(allDeps) if allDeps.isEmpty =>
        Right(Seq.empty)
      case Right(allDeps) =>
        logger.info(s"Discovered plugin dependencies: ${allDeps.mkString(", ")}")

        val dependencies = allDeps.map(Dependency.make(_, "", None))
        val pluginJarPaths = try {
          dependencyResolver.fetchFiles(dependencies, None)
        } catch {
          case e: Exception =>
            return Left(s"Failed to resolve plugin dependencies: ${e.getMessage}")
        }

        logger.debug(s"Resolved plugin JARs: ${pluginJarPaths.map(_.last).mkString(", ")}")

        evaluatePhase2(pklFile, pluginJarPaths) match {
          case Left(err) => Left(err)
          case Right(project) =>
            Right(loadPlugins(project, pluginJarPaths))
        }
    }
  }
}

object PluginLoader {
  /** Convenience: extract deps from project config (used in tests and by PluginLoader). */
  def extractDeps(project: DederProject): Seq[String] = {
    import scala.jdk.CollectionConverters.*
    for {
      module <- project.modules.asScala.toSeq
      plugin <- Option(module.plugins).toSeq.flatMap(_.asScala)
      dep <- Option(plugin.deps).toSeq.flatMap(_.asScala)
    } yield dep
  }
}
