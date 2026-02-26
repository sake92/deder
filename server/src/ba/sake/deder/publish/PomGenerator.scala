package ba.sake.deder.publish

import java.io.StringWriter
import scala.jdk.CollectionConverters.*
import org.apache.maven.model.{Model, Scm}
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import ba.sake.deder.deps.Dependency
import ba.sake.deder.config.DederProject.PomSettings as PklPomSettings

object PomGenerator {

  def generate(
      groupId: String,
      artifactId: String,
      version: String,
      dependencies: Seq[Dependency],
      pomSettings: PklPomSettings
  ): String = {
    val model = new Model()
    model.setModelVersion("4.0.0")
    model.setGroupId(groupId)
    model.setArtifactId(artifactId)
    model.setVersion(version)
    model.setDescription(pomSettings.description)
    model.setUrl(pomSettings.url)
    model.setLicenses(pomSettings.licenses.asScala.map { l =>
      val license = new org.apache.maven.model.License()
      license.setName(l.name)
      license.setUrl(l.url)
      license
    }.asJava)
    model.setDevelopers(pomSettings.developers.asScala.map { d =>
      val dev = new org.apache.maven.model.Developer()
      dev.setId(d.id)
      dev.setName(d.name)
      dev.setEmail(d.email)
      dev
    }.asJava)
    if (pomSettings.scm != null) {
      val scm = new Scm()
      scm.setUrl(pomSettings.scm.url)
      scm.setConnection(pomSettings.scm.connection)
      scm.setDeveloperConnection(pomSettings.scm.developerConnection)
      model.setScm(scm)
    }
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
