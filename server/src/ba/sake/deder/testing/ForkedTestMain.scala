package ba.sake.deder.testing

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

    val logger = new ForkedTestLogger(moduleId = "fork")

    val testRunner = new DederTestRunner(
      testParallelism = forkedArgs.testParallelism,
      discoveredTests = forkedArgs.discoveredTests,
      frameworkOverrides = Map.empty,
      classLoader = classLoader,
      logger = logger
    )
    val results = testRunner.run(DederTestOptions(forkedArgs.testSelectors))
    os.write.over(os.Path(forkedArgs.resultsFile), results.toJson, createFolders = true)
    System.exit(if results.success then 0 else 1)
  }
}
