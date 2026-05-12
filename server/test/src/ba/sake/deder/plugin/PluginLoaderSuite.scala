package ba.sake.deder.plugin

import java.io.StringWriter
import java.net.URLClassLoader
import java.nio.file.Files
import javax.tools.{DiagnosticCollector, JavaFileObject, ToolProvider}
import scala.jdk.CollectionConverters.*
import ba.sake.deder.*
import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.*
import ba.sake.deder.deps.{Dependency, DependencyResolverApi}
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

class PluginLoaderSuite extends munit.FunSuite {

  private val emptyModules = java.util.List.of[DederProject.DederModule]()
  private val emptyPlugins = java.util.List.of[DederProject.DederPlugin]()
  private val emptyRepos = java.util.List.of[MavenRepository]()

  private val noopCoreTasksApi = new CoreTasksApi {
    def sourcesTask = null
    def sourceFilesTask = null
    def resourcesTask = null
    def classesTask = null
    def allClassesDirsTask = null
    def compileTask = null
    def allDependenciesTask = null
    def compileClasspathTask = null
  }

  private val noopDependencyResolver = new DependencyResolverApi {
    def fetchFiles(
        dependencies: Seq[Dependency],
        notifications: Option[ServerNotificationsLogger]
    ): Seq[os.Path] = Seq.empty
    def fetchFile(dependency: Dependency): os.Path = os.pwd / "unused.jar"
    def resolveTransitiveCoordinates(
        dependencies: Seq[Dependency],
        notifications: Option[ServerNotificationsLogger]
    ): Seq[(String, String, String)] = Seq.empty
  }

  test("extract plugin deps from a project with one plugin") {
    val plugin = DederPlugin("hello", java.util.List.of("ba.sake::deder-hello-plugin:0.1.0"))
    val config = DederProject(emptyModules, java.util.List.of(plugin), emptyRepos, true)

    val deps = PluginLoader.extractDeps(config)
    assertEquals(deps, Seq("ba.sake::deder-hello-plugin:0.1.0"))
  }

  test("extract plugin deps from multiple modules and plugins") {
    val p1 = DederPlugin("a", java.util.List.of("org:a:1.0"))
    val p2 = DederPlugin("b", java.util.List.of("org:b:2.0"))
    val config = DederProject(emptyModules, java.util.List.of(p1, p2), emptyRepos, true)

    val deps = PluginLoader.extractDeps(config)
    assertEquals(deps, Seq("org:a:1.0", "org:b:2.0"))
  }

  test("empty plugins list returns empty deps") {
    val config = DederProject(emptyModules, emptyPlugins, emptyRepos, true)

    val deps = PluginLoader.extractDeps(config)
    assertEquals(deps, Seq.empty)
  }

  test("plugin evaluator resolves modulepath resources from the plugin classloader before the app classloader") {
    withScratchDir("plugin-modulepath-test") { pluginDir =>
      os.write.over(
        pluginDir / "HelloPlugin.pkl",
        """module plugin.test
          |
          |class HelloPluginConfig {
          |  greeting: String = "Hello, Deder!"
          |}
          |
          |config: HelloPluginConfig = new {}
          |""".stripMargin
      )

      val pluginClassLoader = new URLClassLoader(Array(pluginDir.toIO.toURI.toURL), getClass.getClassLoader)
      try {
        val greeting = PluginConfigEvaluators
          .evaluateModulePathConfig(
            pluginClassLoader,
            modulePath = "HelloPlugin.pkl",
            configText =
              """config {
                |  greeting = "Hello from test!"
                |}
                |""".stripMargin
          )
          .get("config")
          .get("greeting")
          .as(classOf[String])

        assertEquals(greeting, "Hello from test!")
      } finally pluginClassLoader.close()
    }
  }

  test("loadPlugins exposes per-plugin results with distinct classloaders") {
    withScratchDir("per-plugin-shape") { scratchDir =>
      val pluginA = writePluginFixture(scratchDir, packageName = "fixtures.a", className = "PluginAImpl", pluginId = "plugin-a")
      val pluginB = writePluginFixture(scratchDir, packageName = "fixtures.b", className = "PluginBImpl", pluginId = "plugin-b")

      val loadResult = newPluginLoader()
        .loadPlugins(Seq("plugin-a" -> "{}", "plugin-b" -> "{}"), Seq(pluginA, pluginB))
        .fold(err => fail(s"Expected plugins to load successfully, but got: $err"), identity)

      val loadedPlugins = extractLoadedPlugins(loadResult)
      val classLoaders = loadedPlugins.map(classLoaderOf)
      try {
        assertEquals(loadedPlugins.map(pluginIdOf).toSet, Set("plugin-a", "plugin-b"))
        assertEquals(loadedPlugins.size, 2)
        assert(!(classLoaders.head eq classLoaders(1)), "Each plugin should have its own URLClassLoader instance")
      } finally closeClassLoaders(classLoaders)
    }
  }

  test("loadPlugins keeps modulepath resource resolution bound to each plugin's own classloader") {
    withScratchDir("per-plugin-resources") { scratchDir =>
      val pluginA = writePluginFixture(
        scratchDir,
        packageName = "fixtures.resources.a",
        className = "PluginAImpl",
        pluginId = "plugin-a",
        expectedGreeting = Some("Hello from plugin A!")
      )
      val pluginB = writePluginFixture(
        scratchDir,
        packageName = "fixtures.resources.b",
        className = "PluginBImpl",
        pluginId = "plugin-b",
        expectedGreeting = Some("Hello from plugin B!")
      )

      newPluginLoader()
        .loadPlugins(Seq("plugin-a" -> "{}", "plugin-b" -> "{}"), Seq(pluginA, pluginB))
        .fold(
          err =>
            fail(
              s"Expected each plugin to resolve HelloPlugin.pkl from its own classloader, but loadPlugins failed: $err"
            ),
          _ => ()
        )
    }
  }

  test("loadPlugins drops temporary classloaders when a configured plugin ID is missing") {
    withScratchDir("missing-plugin-id") { scratchDir =>
      val unrelatedPlugin =
        writePluginFixture(scratchDir, packageName = "fixtures.missing", className = "AvailablePluginImpl", pluginId = "available")

      val (loadResult, warningEvents) = capturePluginLoaderEvents {
        newPluginLoader()
          .loadPlugins(Seq("missing-plugin" -> "{}"), Seq(unrelatedPlugin))
          .fold(err => fail(s"Expected missing plugin id to return no tasks, but got: $err"), identity)
      }

      assertEquals(loadResult.tasks, Seq.empty)
      assert(
        warningEvents.exists(event =>
          event.getLevel.levelStr == "WARN" &&
            event.getFormattedMessage.contains("No DederPluginApi implementation found for id='missing-plugin'")
        ),
        clues(warningEvents.map(_.getFormattedMessage))
      )
      val retainedClassLoaders = extractRetainedClassLoaders(loadResult)
      try {
        assertEquals(
          retainedClassLoaders,
          Seq.empty,
          "A missing plugin should not keep a temporary classloader alive in the load result"
        )
      } finally closeClassLoaders(retainedClassLoaders)
    }
  }

  test("PluginLoadResult does not expose the legacy single-loader accessor") {
    val pluginClassLoader = new URLClassLoader(Array.empty, getClass.getClassLoader)
    val loadResult = PluginLoader.PluginLoadResult(
      Seq(PluginLoader.LoadedPlugin("plugin-a", Seq.empty, pluginClassLoader))
    )

    try {
      assert(
        !loadResult.getClass.getMethods.exists(method => method.getName == "classLoader" && method.getParameterCount == 0),
        clues(loadResult.getClass.getMethods.map(_.getName).sorted.mkString(", "))
      )
    } finally pluginClassLoader.close()
  }

  private def newPluginLoader(): PluginLoader =
    new PluginLoader(noopCoreTasksApi, noopDependencyResolver)

  private def capturePluginLoaderEvents[T](f: => T): (T, Seq[ILoggingEvent]) = {
    val logger = LoggerFactory.getLogger(classOf[PluginLoader]).asInstanceOf[Logger]
    val appender = new ListAppender[ILoggingEvent]()
    appender.start()
    logger.addAppender(appender)
    try (f, appender.list.asScala.toSeq)
    finally {
      logger.detachAppender(appender)
      appender.stop()
    }
  }

  private def withScratchDir(prefix: String)(f: os.Path => Unit): Unit = {
    val dir = os.Path(Files.createTempDirectory(s"plugin-loader-suite-$prefix-"))
    try f(dir)
    finally if os.exists(dir) then os.remove.all(dir)
  }

  private def writePluginFixture(
      scratchDir: os.Path,
      packageName: String,
      className: String,
      pluginId: String,
      expectedGreeting: Option[String] = None
  ): os.Path = {
    val pluginDir = scratchDir / pluginId
    val packageDir = packageName.split('.').foldLeft(pluginDir)(_ / _)
    os.makeDir.all(packageDir)
    os.makeDir.all(pluginDir / "META-INF" / "services")

    val body = expectedGreeting match {
      case Some(greeting) =>
        s"""org.pkl.config.java.Config config =
           |  ba.sake.deder.PluginConfigEvaluators$$.MODULE$$.evaluateModulePathConfig(
           |    getClass().getClassLoader(),
           |    "HelloPlugin.pkl",
           |    ""
           |  );
           |String actualGreeting = config.get("config").get("greeting").as(String.class);
           |if (!"$greeting".equals(actualGreeting)) {
           |  throw new IllegalStateException("Expected plugin resource greeting '$greeting' but got '" + actualGreeting + "'");
           |}
           |return scala.collection.immutable.List$$.MODULE$$.empty();
           |""".stripMargin
      case None =>
        """return scala.collection.immutable.List$.MODULE$.empty();
          |""".stripMargin
    }

    os.write.over(
      packageDir / s"$className.java",
      s"""package $packageName;
         |
         |import ba.sake.deder.AbstractTask;
         |import ba.sake.deder.CoreTasksApi;
         |import ba.sake.deder.DederPluginApi;
         |
         |public final class $className implements DederPluginApi {
         |  @Override
         |  public String id() {
         |    return "$pluginId";
         |  }
         |
         |  @Override
         |  public scala.collection.immutable.Seq<AbstractTask<?>> tasks(CoreTasksApi coreTasks, String configText) {
         |    $body
         |  }
         |}
         |""".stripMargin
    )
    os.write.over(
      pluginDir / "META-INF" / "services" / "ba.sake.deder.DederPluginApi",
      s"$packageName.$className\n"
    )
    expectedGreeting.foreach { greeting =>
      os.write.over(
        pluginDir / "HelloPlugin.pkl",
        s"""module plugin.test
           |
           |class HelloPluginConfig {
           |  greeting: String = "$greeting"
           |}
           |
           |config: HelloPluginConfig = new {}
           |""".stripMargin
      )
    }
    compileJava(packageDir / s"$className.java", pluginDir)
    pluginDir
  }

  private def compileJava(sourceFile: os.Path, outputDir: os.Path): Unit = {
    val compiler = ToolProvider.getSystemJavaCompiler()
    assert(compiler != null, "Tests require a JDK with javax.tools.JavaCompiler available")
    val diagnostics = new DiagnosticCollector[JavaFileObject]()
    val compilerOutput = new StringWriter()
    val fileManager = compiler.getStandardFileManager(diagnostics, null, null)
    try {
      val compilationUnits = fileManager.getJavaFileObjectsFromPaths(java.util.List.of(sourceFile.toNIO))
      val success = compiler
        .getTask(
          compilerOutput,
          fileManager,
          diagnostics,
          java.util.List.of(
            "-proc:none",
            "-classpath",
            System.getProperty("java.class.path"),
            "-d",
            outputDir.toString
          ),
          null,
          compilationUnits
        )
        .call()

      val renderedDiagnostics = diagnostics.getDiagnostics.asScala.map { diagnostic =>
        val sourceName = Option(diagnostic.getSource).map(_.getName).getOrElse("<no source>")
        s"${diagnostic.getKind} $sourceName:${diagnostic.getLineNumber}:${diagnostic.getColumnNumber} ${diagnostic.getMessage(null)}"
      }
      val failureDetails =
        Seq(
          s"Failed to compile plugin fixture ${sourceFile.last}",
          s"diagnostics:\n${if renderedDiagnostics.nonEmpty then renderedDiagnostics.mkString("\n") else "<none>"}",
          s"compiler stdout/stderr:\n${Option(compilerOutput.toString).filter(_.nonEmpty).getOrElse("<none>")}"
        ).mkString("\n\n")

      assert(success, failureDetails)
    } finally fileManager.close()
  }

  private def extractLoadedPlugins(loadResult: Any): Seq[Any] = {
    // The red-phase contract here is intentionally shape-based: each per-plugin result should expose pluginId, tasks, and classLoader.
    val namedCandidates =
      Seq("loadedPlugins", "pluginResults", "perPluginResults", "singlePluginResults")
        .flatMap(accessorValue(loadResult, _).map(toSeq))
        .find(seq => seq.nonEmpty && seq.forall(looksLikeLoadedPlugin))

    namedCandidates
      .orElse {
        loadResult.getClass.getMethods.iterator
          .filter(method => method.getParameterCount == 0 && method.getDeclaringClass != classOf[Object])
          .flatMap { method =>
            scala.util.Try(method.invoke(loadResult)).toOption.map(toSeq)
          }
          .find(seq => seq.nonEmpty && seq.forall(looksLikeLoadedPlugin))
      }
      .getOrElse(
        fail("Expected PluginLoadResult to expose a per-plugin result collection with pluginId/tasks/classLoader")
      )
  }

  private def closeClassLoaders(classLoaders: Seq[URLClassLoader]): Unit =
    classLoaders.distinct.foreach(_.close())

  private def extractRetainedClassLoaders(loadResult: Any): Seq[URLClassLoader] =
    Seq("classLoaders", "loaders")
      .flatMap(accessorValue(loadResult, _).toSeq)
      .headOption
      .map(toSeq(_).collect { case loader: URLClassLoader => loader })
      .getOrElse(Seq.empty)

  private def looksLikeLoadedPlugin(value: Any): Boolean =
    accessorValue(value, "pluginId").exists(_.isInstanceOf[String]) &&
      accessorValue(value, "tasks").isDefined &&
      accessorValue(value, "classLoader").exists(_.isInstanceOf[URLClassLoader])

  private def pluginIdOf(value: Any): String =
    accessorValue(value, "pluginId").map(_.asInstanceOf[String]).getOrElse(fail("Missing pluginId"))

  private def classLoaderOf(value: Any): URLClassLoader =
    accessorValue(value, "classLoader").map(_.asInstanceOf[URLClassLoader]).getOrElse(fail("Missing classLoader"))

  private def accessorValue(value: Any, name: String): Option[Any] =
    value.getClass.getMethods.find(method => method.getName == name && method.getParameterCount == 0).flatMap { method =>
      scala.util.Try(method.invoke(value)).toOption
    }.orElse {
      value.getClass.getDeclaredFields.find(_.getName == name).flatMap { field =>
        field.setAccessible(true)
        scala.util.Try(field.get(value)).toOption
      }
    }

  private def toSeq(value: Any): Seq[Any] = value match {
    case seq: scala.collection.Iterable[?] => seq.toSeq
    case iterable: java.lang.Iterable[?] => iterable.asScala.toSeq
    case array: Array[?] => array.toSeq
    case opt: Option[?] => opt.toSeq
    case _ => Seq.empty
  }
}
