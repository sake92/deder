package ba.sake.deder.publish

import java.io.StringWriter
import scala.jdk.CollectionConverters.*
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import ba.sake.deder.deps.Dependency

object PomGenerator {

  def generate(
      groupId: String,
      artifactId: String,
      version: String,
      dependencies: Seq[Dependency] = Seq.empty
  ): String = {
    val model = new Model()
    model.setModelVersion("4.0.0")
    model.setGroupId(groupId)
    model.setArtifactId(artifactId)
    model.setVersion(version)

    if (dependencies.nonEmpty) {
      val mavenDependencies = dependencies.map { dep =>
        val coursierDep = dep.applied
        val d = new org.apache.maven.model.Dependency()
        d.setGroupId(coursierDep.module.organization)
        d.setArtifactId(coursierDep.module.name)
        d.setVersion(coursierDep.version)
        d.setScope(coursierDep.userParamsMap.getOrElse("scope", Seq.empty).headOption.flatten.getOrElse("compile"))
        d
      }
      model.setDependencies(mavenDependencies.asJava)
    }

    val writer = new MavenXpp3Writer()
    val stringWriter = new StringWriter()
    writer.write(stringWriter, model)
    stringWriter.toString
  }
}
