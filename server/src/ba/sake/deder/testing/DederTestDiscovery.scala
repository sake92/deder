package ba.sake.deder.testing

import java.lang.annotation.Annotation
import java.lang.reflect.Modifier
import sbt.testing.*
import ba.sake.deder.*
import ba.sake.tupson.JsonRW


case class DiscoveredFrameworkTests(framework: String, testClasses: Seq[String]) derives JsonRW

class DederTestDiscovery(
    classLoader: ClassLoader,
    testClassesDir: os.Path,
    testClasspath: Seq[os.Path],
    frameworkClassNames: Seq[String],
    logger: DederTestLogger
) {

  def discover(): Seq[(Framework, Seq[(String, Fingerprint)])] =
    discoverFrameworks().map { framework =>
      val testClasses = discoverTests(framework)
      (framework, testClasses)
    }

  private def discoverFrameworks(): Seq[Framework] =
    val frameworks = frameworkClassNames.flatMap { className =>
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
    if (frameworks.isEmpty) {
      logger.warn(
        "No test frameworks found on the classpath. Tried to load the following frameworks: " +
          frameworkClassNames.mkString(", ")
      )
    } else {
      logger.debug(s"Discovered test frameworks: ${frameworks.map(_.name()).mkString(", ")}")
    }
    frameworks
  end discoverFrameworks

  private def discoverTests(framework: Framework): Seq[(String, Fingerprint)] = {
    if framework.name() == "Jupiter" then {
      discoverJupiterTests.map(_ -> framework.fingerprints.head)
    } else {
      val fingerprints = framework.fingerprints()
      val testClasses = findClassFiles()
      testClasses.flatMap { className =>
        fingerprints.collectFirst {
          case fp if matchesFingerprint(className, fp, classLoader) =>
            (className, fp)
        }
      }
    }
  }

  private def findClassFiles(): Seq[String] = {
    os.walk(testClassesDir)
      .filter(_.last.endsWith(".class"))
      .map(_.subRelativeTo(testClassesDir))
      .map(_.segments.mkString(".").stripSuffix(".class"))
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
          superCls.isAssignableFrom(cls) && sub.isModule == isModule(cls)
        case f: AnnotatedFingerprint =>
          val annotationCls = classLoader.loadClass(f.annotationName()).asInstanceOf[Class[Annotation]]
          f.isModule == isModule(cls) && (
            cls.isAnnotationPresent(annotationCls) ||
              cls.getDeclaredMethods.exists(_.isAnnotationPresent(annotationCls)) ||
              cls.getMethods.exists(m => m.isAnnotationPresent(annotationCls) && Modifier.isPublic(m.getModifiers))
          )
      }
    } catch {
      case e: Exception =>
        logger.debug(s"Failed to match fingerprint for class $className: ${e.getMessage}")
        false
    }
  }

  private def isModule(cls: Class[?]): Boolean = {
    cls.getName.endsWith("$")
  }

  private def discoverJupiterTests = {
    DiscoverJunit5Tests.discover(classLoader, testClassesDir, testClasspath)
  }
}
