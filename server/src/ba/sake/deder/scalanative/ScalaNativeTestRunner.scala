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
      discoveredTests: Seq[DiscoveredFrameworkTests],
      nativeBinaryPath: os.Path,
      testOptions: DederTestOptions,
      executorService: ExecutorService
  ): DederTestResults = {
    val config = TestAdapter.Config()
      .withBinaryFile(nativeBinaryPath.toIO)
      .withLogger(new DederScalaNativeLogger(notifications, moduleId))

    val adapter = new TestAdapter(config)
    try {
      val nativeFwNames = discoveredTests.map(_.frameworkClassName).distinct
      val loadedFrameworks = nativeFwNames
        .zip(adapter.loadFrameworks(nativeFwNames.map(n => List(n)).toList))
        .collect { case (name, Some(f)) => name -> f }
        .toMap
      
      if loadedFrameworks.isEmpty then
        notifications.add(ServerNotification.logWarning(
          s"No test frameworks found for Scala Native module '$moduleId'. Tried: ${nativeFwNames.mkString(", ")}",
          Some(moduleId)
        ))
        return DederTestResults.empty

      val dederLogger = new DederTestLogger(notifications, moduleId) {
        override def showStackTraces: Boolean = false
      }

      val testRunner = DederTestRunner(executorService, discoveredTests, loadedFrameworks, getClass.getClassLoader, dederLogger)
      testRunner.run(testOptions)
    } finally {
      adapter.close()
    }
  }
}
