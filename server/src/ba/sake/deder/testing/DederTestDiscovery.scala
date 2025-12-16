package ba.sake.deder.testing

import sbt.testing.{Task as SbtTestTask, *}
import java.io.File
import java.net.URLClassLoader
import scala.collection.mutable
import ba.sake.deder.*

class DederTestDiscovery(testClassesDir: File, logger: DederTestLogger) {

  private val frameworkClassNames: Seq[String] = Seq(
    "org.scalatest.tools.Framework",
    "org.specs2.runner.Specs2Framework",
    "munit.Framework",
    "utest.runner.Framework",
    "zio.test.sbt.ZTestFramework"
  )

  def discoverFrameworks(classLoader: ClassLoader): Seq[Framework] = {
    frameworkClassNames.flatMap { className =>
      try {
        val cls = classLoader.loadClass(className)
        Some(cls.getDeclaredConstructor().newInstance().asInstanceOf[Framework])
      } catch {
        case _: ClassNotFoundException => None
        case e: Exception =>
          logger.warn(s"Failed to load framework $className: ${e.getMessage}")
          None
      }
    }
  }

  def discoverTests(
      framework: Framework,
      classLoader: ClassLoader
  ): Seq[(String, Fingerprint)] = {
    val fingerprints = framework.fingerprints()
    val testClasses = findClassFiles()
    testClasses.flatMap { className =>
      fingerprints.collectFirst {
        case fp if matchesFingerprint(className, fp, classLoader) =>
          (className, fp)
      }
    }
  }

  private def findClassFiles(): Seq[String] = {
    val osDir = os.Path(testClassesDir)
    os.walk(osDir).filter(_.last.endsWith(".class")).map(_.relativeTo(osDir).baseName)
  }

  private def matchesFingerprint(
      className: String,
      fingerprint: Fingerprint,
      classLoader: ClassLoader
  ): Boolean = {
    try {
      val cls = classLoader.loadClass(className)
      fingerprint match {
        case sub: SubclassFingerprint =>
          val superCls = classLoader.loadClass(sub.superclassName())
          superCls.isAssignableFrom(cls) &&
          sub.isModule == isModule(cls)
        case ann: AnnotatedFingerprint =>
          val annCls = classLoader.loadClass(ann.annotationName())
          cls.isAnnotationPresent(annCls.asInstanceOf[Class[java.lang.annotation.Annotation]]) &&
          ann.isModule == isModule(cls)
      }
    } catch {
      case _: Exception =>
        logger.debug(s"Failed to match fingerprint for class $className")
        false
    }
  }

  private def isModule(cls: Class[?]): Boolean = {
    cls.getName.endsWith("$")
  }
}
