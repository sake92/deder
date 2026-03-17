package ba.sake.deder.testing

import java.lang.annotation.Annotation
import java.lang.reflect.Modifier
import sbt.testing.*
import ba.sake.deder.*
import ba.sake.tupson.JsonRW
import org.typelevel.jawn.ast.{JString, JValue}

enum JsonableFingerprint derives JsonRW {
  case Subclass(superclassName: String, isModule: Boolean, requireNoArgConstructor: Boolean)
  case Annotated(annotationName: String, isModule: Boolean)

  def toSbtFingerprint: Fingerprint = this match {
    case Subclass(scName, issModule, reqNoArgConstructor) =>
      new SubclassFingerprint {
        def superclassName(): String = scName
        def isModule: Boolean = issModule
        def requireNoArgConstructor(): Boolean = reqNoArgConstructor
      }
    case Annotated(annName, issModule) =>
      new AnnotatedFingerprint {
        def annotationName(): String = annName
        def isModule: Boolean = issModule
      }
  }
}

object JsonableFingerprint {
  def fromSbt(fp: Fingerprint): JsonableFingerprint = fp match {
    case sub: SubclassFingerprint =>
      Subclass(sub.superclassName(), sub.isModule, sub.requireNoArgConstructor())
    case ann: AnnotatedFingerprint =>
      Annotated(ann.annotationName(), ann.isModule)
  }
}

case class DiscoveredFrameworkTest(className: String, fingerprint: JsonableFingerprint) derives JsonRW

case class DiscoveredFrameworkTests(
    frameworkName: String,
    frameworkClassName: String,
    testClasses: Seq[DiscoveredFrameworkTest]
) derives JsonRW

class DederTestDiscovery(
    classLoader: ClassLoader,
    testClassesDir: os.Path,
    testClasspath: Seq[os.Path],
    frameworkClassNames: Seq[String],
    logger: DederTestLogger
) {

  def discover(): Seq[DiscoveredFrameworkTests] =
    discoverTests(discoverFrameworks()).map { case (framework, tests) =>
      DiscoveredFrameworkTests(
        framework.name(),
        framework.getClass.getName,
        tests.map(t => DiscoveredFrameworkTest(t._1, JsonableFingerprint.fromSbt(t._2)))
      )
    }

  private def discoverTests(frameworks: Seq[Framework]): Seq[(Framework, Seq[(String, Fingerprint)])] =
    frameworks.map { framework =>
      val testClasses = discoverTestsForFramework(framework)
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

  private def discoverTestsForFramework(framework: Framework): Seq[(String, Fingerprint)] = {
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
    if os.exists(testClassesDir) then
      os.walk(testClassesDir)
        .filter(_.last.endsWith(".class"))
        .map(_.subRelativeTo(testClassesDir))
        .map(_.segments.mkString(".").stripSuffix(".class"))
    else Seq.empty
  }

  private def matchesFingerprint(
      className: String,
      fingerprint: Fingerprint,
      classLoader: ClassLoader
  ): Boolean = {
    try {
      val cls = classLoader.loadClass(className)
      if (cls.isInterface || Modifier.isAbstract(cls.getModifiers)) {
        return false
      }
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
