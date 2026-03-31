package ba.sake.deder.testing

import java.util.concurrent.Executors
import sbt.testing.{Framework, Fingerprint, SubclassFingerprint, AnnotatedFingerprint}
import ba.sake.tupson.{*, given}

object ForkedTestMain {
  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      System.err.println("Usage: ForkedTestMain <args-file-path>")
      System.exit(2)
    }
    val argsFilePath = args(0)
    val forkedArgs = os.read(os.Path(argsFilePath)).parseJson[ForkedTestArgs]

    // forked JVM already has the full classpath on -cp; use the system classloader directly.
    val classLoader = ClassLoader.getSystemClassLoader

    val frameworks: Seq[Framework] = forkedArgs.discoveredTests.flatMap { dt =>
      try {
        val cls = classLoader.loadClass(dt.frameworkClassName)
        Some(cls.getDeclaredConstructor().newInstance().asInstanceOf[Framework])
      } catch {
        case e: Exception =>
          None
      }
    }

    val executorService = Executors.newFixedThreadPool(forkedArgs.workerThreads)
    val logger = new ForkedTestLogger(moduleId = "fork")

    try {
      val testRunner = new DederTestRunner(
        executorService = executorService,
        discoveredTests = forkedArgs.discoveredTests,
        frameworkOverrides = Map.empty,
        classLoader = classLoader,
        logger = logger
      )
      val results = testRunner.run(DederTestOptions(forkedArgs.testSelectors))
      os.write.over(os.Path(forkedArgs.resultsFile), results.toJson, createFolders = true)
      System.exit(if results.success then 0 else 1)
    } finally {
      executorService.shutdown()
    }
  }

}
