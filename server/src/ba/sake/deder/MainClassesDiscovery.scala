package ba.sake.deder

import java.io.File
import scala.jdk.CollectionConverters._
import scala.util.Using
import io.github.classgraph.ClassGraph

// stolen from mill
// https://github.com/com-lihaoyi/mill/blob/1.1.0-RC3/libs/javalib/classgraph-worker/src/mill/javalib/classgraph/impl/ClassgraphWorkerImpl.scala
object MainClassesDiscovery {

  def discover(classpath: Seq[os.Path]): Seq[String] = {

    val cp = classpath.map(_.toNIO.toString()).mkString(File.pathSeparator)
    println(s"Scanning for mainclasses: ${cp}")

    val mainClasses = Using.resource(
      new ClassGraph()
        .overrideClasspath(cp)
        .enableMethodInfo()
        .scan()
    ) { scan =>
      scan
        .getAllClasses()
        .filter { classInfo =>
          val mainMethods = classInfo.getMethodInfo().filter { m =>
            m.getName() == "main" && m.isPublic() && m.isStatic() && {
              val ps = m.getParameterInfo()
              ps.length == 1 &&
              ps(0).getTypeSignatureOrTypeDescriptor().toString() == "java.lang.String[]"
            }
          }
          !mainMethods.isEmpty()
        }
        .getNames()
    }

    println(s"Found main classes: ${mainClasses}")
    mainClasses.asScala.toList
  }

}
