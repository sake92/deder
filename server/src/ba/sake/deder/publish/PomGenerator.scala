package ba.sake.deder.publish

import java.io.StringWriter
import scala.jdk.CollectionConverters.*
import org.apache.maven.model.*
import org.apache.maven.model.io.xpp3.MavenXpp3Writer

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
      model.setDependencies(dependencies.asJava)
    }

    val writer = new MavenXpp3Writer()
    val stringWriter = new StringWriter()
    writer.write(stringWriter, model)
    stringWriter.toString
  }

  def createDependency(g: String, a: String, v: String, scope: String = "compile"): Dependency = {
    val d = new Dependency()
    d.setGroupId(g)
    d.setArtifactId(a)
    d.setVersion(v)
    d.setScope(scope)
    d
  }
}