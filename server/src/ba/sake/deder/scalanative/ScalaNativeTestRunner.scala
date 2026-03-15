package ba.sake.deder.scalanative

import java.util.concurrent.ExecutorService
import scala.jdk.CollectionConverters.*
import com.typesafe.scalalogging.StrictLogging
import scala.scalanative.testinterface.adapter.TestAdapter
import ba.sake.deder.{ServerNotification, ServerNotificationsLogger}
import ba.sake.deder.testing.*

class ScalaNativeTestRunner(
    notifications: ServerNotificationsLogger,
    moduleId: String
) extends StrictLogging {

  def run(
      classesDir: os.Path,
      runtimeClasspath: Seq[os.Path],
      nativeBinaryPath: os.Path,
      testFrameworkNames: Seq[String],
      testOptions: DederTestOptions,
      executorService: ExecutorService
  ): DederTestResults = {
    val config = TestAdapter.Config()
      .withBinaryFile(nativeBinaryPath.toIO)
      .withLogger(new DederScalaNativeLogger(notifications, moduleId))

    val adapter = new TestAdapter(config)
    try {
      val loadedFrameworks = adapter
        .loadFrameworks(testFrameworkNames.map(n => List(n)).toList)
        .flatten

      if loadedFrameworks.isEmpty then
        notifications.add(ServerNotification.logWarning(
          s"No test frameworks found for Scala Native module '$moduleId'. Tried: ${testFrameworkNames.mkString(", ")}",
          Some(moduleId)
        ))
        return DederTestResults.empty

      val urls = (Seq(classesDir) ++ runtimeClasspath).filter(os.exists(_)).map(_.toNIO.toUri.toURL).toArray
      val classLoader = new java.net.URLClassLoader(urls, null)
      val dederLogger = new DederTestLogger(notifications, moduleId) {
        override def showStackTraces: Boolean = false
      }
      val testsPerFramework = try {
        val discovery = DederTestDiscovery(
          classLoader = classLoader,
          testClassesDir = classesDir,
          testClasspath = runtimeClasspath,
          frameworkClassNames = Seq.empty,
          logger = dederLogger
        )
        discovery.discoverTests(loadedFrameworks)
      } finally classLoader.close()

      val testRunner = DederTestRunner(executorService, testsPerFramework, getClass.getClassLoader, dederLogger)
      testRunner.run(testOptions)
    } finally {
      adapter.close()
    }
  }
}
