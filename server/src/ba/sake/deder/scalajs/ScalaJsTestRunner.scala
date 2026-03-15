package ba.sake.deder.scalajs

import java.io.InputStream
import java.util.concurrent.ExecutorService
import scala.jdk.CollectionConverters.*
import scala.util.Using
import com.typesafe.scalalogging.StrictLogging
import org.scalajs.jsenv.{Input, JSComRun, JSEnv, JSRun, RunConfig}
import org.scalajs.jsenv.nodejs.NodeJSEnv
import org.scalajs.testing.adapter.TestAdapter
import ba.sake.deder.{ServerNotification, ServerNotificationsLogger}
import ba.sake.deder.config.DederProject.ScalaJsModuleKind
import ba.sake.deder.testing.*

class ScalaJsTestRunner(
    notifications: ServerNotificationsLogger,
    moduleId: String
) extends StrictLogging {

  def run(
      classesDir: os.Path,
      runtimeClasspath: Seq[os.Path],
      linkedJsDir: os.Path,
      moduleKind: ScalaJsModuleKind,
      testFrameworkNames: Seq[String],
      testOptions: DederTestOptions,
      executorService: ExecutorService
  ): DederTestResults = {
    ensureNodeAvailable()

    val inputs = createInputs(linkedJsDir, moduleKind)
    val jsEnv = forwardProcessOutput(
      new NodeJSEnv(NodeJSEnv.Config().withArgs(List("--enable-source-maps")))
    )
    val config = TestAdapter.Config()
      .withLogger(new DederScalaJsLogger(notifications, moduleId))

    val adapter = new TestAdapter(jsEnv, inputs, config)
    try {
      // Load frameworks via the JS runtime bridge
      val loadedFrameworks = adapter
        .loadFrameworks(testFrameworkNames.map(n => List(n)).toList)
        .flatten

      if loadedFrameworks.isEmpty then
        notifications.add(ServerNotification.logWarning(
          s"No test frameworks found for Scala.js module '$moduleId'. Tried: ${testFrameworkNames.mkString(", ")}",
          Some(moduleId)
        ))
        return DederTestResults.empty

      // Discover tests by scanning .class files and matching against framework fingerprints
      val testsPerFramework = loadedFrameworks.flatMap { framework =>
        val fingerprints = framework.fingerprints()
        val testClasses = findClassFiles(classesDir)
        val matched = testClasses.flatMap { className =>
          fingerprints.collectFirst {
            case fp if matchesFingerprint(className, fp, runtimeClasspath, classesDir) =>
              (className, fp)
          }
        }
        if matched.nonEmpty then Some((framework, matched)) else None
      }

      val dederLogger = new DederTestLogger(notifications, moduleId) {
        override def showStackTraces: Boolean = false
      }
      val testRunner = DederTestRunner(executorService, testsPerFramework, getClass.getClassLoader, dederLogger)
      testRunner.run(testOptions)
    } finally {
      adapter.close()
    }
  }

  private def ensureNodeAvailable(): Unit = {
    try {
      val res = os.proc("node", "--version").call(check = false, timeout = 10000)
      if res.exitCode != 0 then
        throw RuntimeException(
          "Node.js is required for running Scala.js tests but 'node' returned a non-zero exit code. " +
            "Please install Node.js and ensure 'node' is on your PATH."
        )
    } catch {
      case _: java.io.IOException =>
        throw RuntimeException(
          "Node.js is required for running Scala.js tests but 'node' was not found on PATH. " +
            "Please install Node.js: https://nodejs.org/"
        )
    }
  }

  private def createInputs(linkedJsDir: os.Path, moduleKind: ScalaJsModuleKind): Seq[Input] = {
    val jsFile = (linkedJsDir / "main.js").toNIO
    moduleKind match {
      case ScalaJsModuleKind.NO_MODULE       => Seq(Input.Script(jsFile))
      case ScalaJsModuleKind.ES_MODULE       => Seq(Input.ESModule(jsFile))
      case ScalaJsModuleKind.COMMONJS_MODULE => Seq(Input.CommonJSModule(jsFile))
    }
  }

  private def findClassFiles(classesDir: os.Path): Seq[String] = {
    if os.exists(classesDir) then
      os.walk(classesDir)
        .filter(_.last.endsWith(".class"))
        .map(_.subRelativeTo(classesDir))
        .map(_.segments.mkString(".").stripSuffix(".class"))
    else Seq.empty
  }

  private def matchesFingerprint(
      className: String,
      fingerprint: sbt.testing.Fingerprint,
      runtimeClasspath: Seq[os.Path],
      classesDir: os.Path
  ): Boolean = {
    import java.lang.annotation.Annotation
    import java.lang.reflect.Modifier
    try {
      // Use an isolated classloader with the compiled .class files for fingerprint matching
      val urls = (Seq(classesDir) ++ runtimeClasspath).filter(os.exists(_)).map(_.toNIO.toUri.toURL).toArray
      val classLoader = new java.net.URLClassLoader(urls, null)
      try {
        val cls = classLoader.loadClass(className)
        if cls.isInterface || Modifier.isAbstract(cls.getModifiers) then return false
        fingerprint match {
          case sub: sbt.testing.SubclassFingerprint =>
            val superCls = classLoader.loadClass(sub.superclassName())
            superCls.isAssignableFrom(cls) && sub.isModule == cls.getName.endsWith("$")
          case f: sbt.testing.AnnotatedFingerprint =>
            val annotationCls = classLoader.loadClass(f.annotationName()).asInstanceOf[Class[Annotation]]
            f.isModule == cls.getName.endsWith("$") && (
              cls.isAnnotationPresent(annotationCls) ||
                cls.getDeclaredMethods.exists(_.isAnnotationPresent(annotationCls)) ||
                cls.getMethods.exists(m => m.isAnnotationPresent(annotationCls) && Modifier.isPublic(m.getModifiers))
            )
        }
      } finally classLoader.close()
    } catch {
      case _: Exception => false
    }
  }

  private def forwardProcessOutput(delegate: JSEnv): JSEnv = new JSEnv {
    override val name: String = delegate.name

    override def start(input: Seq[Input], runConfig: RunConfig): JSRun =
      delegate.start(input, withForwardedOutput(runConfig))

    override def startWithCom(
        input: Seq[Input],
        runConfig: RunConfig,
        onMessage: String => Unit
    ): JSComRun =
      delegate.startWithCom(input, withForwardedOutput(runConfig), onMessage)
  }

  private def withForwardedOutput(runConfig: RunConfig): RunConfig =
    runConfig.withOnOutputStream { (stdout, stderr) =>
      stdout.foreach(stream => startForwarding(stream, message => notifications.add(ServerNotification.logInfo(message, Some(moduleId)))))
      stderr.foreach(stream => startForwarding(stream, message => notifications.add(ServerNotification.logError(message, Some(moduleId)))))
    }

  private def startForwarding(stream: InputStream, log: String => Unit): Unit = {
    val thread = new Thread(
      () => drainStream(stream, log),
      s"deder-scalajs-test-output-$moduleId"
    )
    thread.setDaemon(true)
    thread.start()
  }

  private def drainStream(stream: InputStream, log: String => Unit): Unit =
    Using.resource(stream) { in =>
      val line = new StringBuilder
      var nextByte = in.read()
      while nextByte != -1 do
        nextByte.toChar match {
          case '\n' =>
            flushLine(line, log)
          case '\r' =>
          case ch =>
            line.append(ch)
        }
        nextByte = in.read()
      flushLine(line, log)
    }

  private def flushLine(line: StringBuilder, log: String => Unit): Unit =
    if line.nonEmpty then
      log(line.result())
      line.clear()
}
