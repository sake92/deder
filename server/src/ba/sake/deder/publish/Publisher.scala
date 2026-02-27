package ba.sake.deder.publish

import ba.sake.deder.{ServerNotification, ServerNotificationsLogger}

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.util.Base64
import java.nio.file.Path
import com.github.mizosoft.methanol.{Methanol, MultipartBodyPublisher}

class Publisher(notifications: ServerNotificationsLogger) {

  def publishLocalM2(pom: PomSettings, files: Seq[os.Path]): Unit = {
    // ~/.m2/repository/group/artifact/version
    val home = System.getProperty("user.home")
    val m2Repo = os.home / ".m2/repository" / pom.groupId.split('.') / pom.artifactId / pom.version
    notifications.add(ServerNotification.logInfo(s"Publishing to local M2 repository at ${m2Repo} ..."))
    os.makeDir.all(m2Repo)
    // copy all artifacts
    files.foreach { file =>
      val dest = m2Repo / file.last
      os.copy.over(file, dest)
    }
    notifications.add(
      ServerNotification.logInfo(
        s"Successfully published ${pom.groupId}:${pom.artifactId}:${pom.version} to local M2 repository"
      )
    )
  }

  // file by file, old style
  def publishSonatypeSnapshot(username: String, password: String, pom: PomSettings, files: Seq[os.Path]): Unit = {
    val baseUrl = "https://central.sonatype.com/repository/maven-snapshots"
    notifications.add(ServerNotification.logInfo(s"Publishing to Sonatype Snapshots repository at ${baseUrl}"))
    val client = HttpClient.newBuilder().build()
    val auth = Base64.getEncoder.encodeToString(s"$username:$password".getBytes("UTF-8"))
    val groupPath = pom.groupId.replace('.', '/')
    files.foreach { file =>
      val targetUrl = s"${baseUrl}/${groupPath}/${pom.artifactId}/${pom.version}/${file.last}"
      val request = HttpRequest
        .newBuilder()
        .uri(URI.create(targetUrl))
        .header("Authorization", s"Basic $auth")
        .PUT(HttpRequest.BodyPublishers.ofFile(file.toNIO))
        .build()
      notifications.add(ServerNotification.logInfo(s"Uploading ${file.last} ..."))
      val res = client.send(request, HttpResponse.BodyHandlers.ofString())
      if res.statusCode() < 200 || res.statusCode() >= 300 then {
        throw RuntimeException(s"Failed to upload ${file.last}: ${res.statusCode()} - ${res.body()}")
      }
    }
    notifications.add(
      ServerNotification.logInfo(
        s"Successfully published ${pom.groupId}:${pom.artifactId}:${pom.version} to Sonatype Snapshots repository"
      )
    )
  }

  // Direct Bundle ZIP Upload
  def publishSonatypeCentral(username: String, password: String, pom: PomSettings, filesZip: os.Path): Unit = {
    val client = Methanol.create
    val auth = Base64.getEncoder.encodeToString(s"${username}:${password}".getBytes("UTF-8"))
    notifications.add(ServerNotification.logInfo(s"Publishing to Sonatype Central repository ..."))
    val baseUrl = "https://central.sonatype.com/api/v1/publisher"
    val multipartBody = MultipartBodyPublisher
      .newBuilder()
      .filePart("bundle", filesZip.toNIO)
      .build()
    val request = HttpRequest
      .newBuilder()
      .uri(URI.create(s"${baseUrl}/upload?publishingType=AUTOMATIC"))
      .header("Authorization", s"Bearer $auth")
      .POST(multipartBody)
      .build()
    val res = client.send(request, HttpResponse.BodyHandlers.ofString())
    if res.statusCode() < 200 || res.statusCode() >= 300 then
      throw RuntimeException(s"${res.statusCode()} - ${res.body()}")
    notifications.add(
      ServerNotification.logInfo(
        s"Successfully published ${pom.groupId}:${pom.artifactId}:${pom.version} to Sonatype Central repository"
      )
    )
  }
}
