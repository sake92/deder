package ba.sake.deder.testing

import java.lang.annotation.Annotation
import java.lang.reflect.Modifier
import sbt.testing.*
import ba.sake.tupson.JsonRW

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
