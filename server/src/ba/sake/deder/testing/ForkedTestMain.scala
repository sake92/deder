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

    // The forked JVM already has the full classpath on -cp; use the system classloader directly.
    val classLoader = ClassLoader.getSystemClassLoader

    val frameworks: Seq[Framework] = forkedArgs.frameworks.flatMap { className =>
      try {
        val cls = classLoader.loadClass(className)
        Some(cls.getDeclaredConstructor().newInstance().asInstanceOf[Framework])
      } catch {
        case e: Exception =>
          System.err.println(s"[fork] Failed to load framework $className: ${e.getMessage}")
          None
      }
    }

    val frameworkTests: Seq[(Framework, Seq[(String, Fingerprint)])] = frameworks.map { framework =>
      val frameworkFingerprints = framework.fingerprints()
      val matchedTests: Seq[(String, Fingerprint)] = forkedArgs.tests.flatMap { serTest =>
        frameworkFingerprints.collectFirst {
          case fp if fingerprintMatches(serTest.fingerprint, fp) =>
            (serTest.className, SerializableFingerprint.toSbt(serTest.fingerprint))
        }
      }
      (framework, matchedTests)
    }

    val executorService = Executors.newFixedThreadPool(forkedArgs.workerThreads)
    val logger = new ForkedTestLogger(moduleId = "fork")

    try {
      val testRunner = new DederTestRunner(
        executorService = executorService,
        tests = frameworkTests,
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

  private def fingerprintMatches(
      serialized: SerializableFingerprint,
      fp: Fingerprint
  ): Boolean = (serialized, fp) match {
    case (s: SerializableSubclassFingerprint, f: SubclassFingerprint) =>
      s.superclassName == f.superclassName() && s.isModule == f.isModule()
    case (s: SerializableAnnotatedFingerprint, f: AnnotatedFingerprint) =>
      s.annotationName == f.annotationName() && s.isModule == f.isModule()
    case _ => false
  }
}
