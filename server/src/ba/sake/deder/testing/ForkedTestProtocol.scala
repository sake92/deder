package ba.sake.deder.testing

import ba.sake.tupson.JsonRW

sealed trait SerializableFingerprint derives JsonRW
case class SerializableSubclassFingerprint(superclassName: String, isModule: Boolean, requireNoArgConstructor: Boolean)
    extends SerializableFingerprint
case class SerializableAnnotatedFingerprint(annotationName: String, isModule: Boolean)
    extends SerializableFingerprint

object SerializableFingerprint {
  def from(fp: sbt.testing.Fingerprint): SerializableFingerprint = fp match {
    case sub: sbt.testing.SubclassFingerprint =>
      SerializableSubclassFingerprint(sub.superclassName(), sub.isModule(), sub.requireNoArgConstructor())
    case ann: sbt.testing.AnnotatedFingerprint =>
      SerializableAnnotatedFingerprint(ann.annotationName(), ann.isModule())
  }

  def toSbt(sfp: SerializableFingerprint): sbt.testing.Fingerprint = sfp match {
    case SerializableSubclassFingerprint(scName, scIsModule, scRequireNoArg) =>
      new sbt.testing.SubclassFingerprint {
        def superclassName(): String = scName
        def isModule(): Boolean = scIsModule
        def requireNoArgConstructor(): Boolean = scRequireNoArg
      }
    case SerializableAnnotatedFingerprint(annName, annIsModule) =>
      new sbt.testing.AnnotatedFingerprint {
        def annotationName(): String = annName
        def isModule(): Boolean = annIsModule
      }
  }
}

case class SerializableTest(className: String, fingerprint: SerializableFingerprint) derives JsonRW

case class ForkedTestArgs(
    runtimeClasspath: Seq[String],
    frameworks: Seq[String],
    tests: Seq[SerializableTest],
    testSelectors: Seq[String],
    workerThreads: Int,
    resultsFile: String
) derives JsonRW
