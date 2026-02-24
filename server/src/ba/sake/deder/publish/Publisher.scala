package ba.sake.deder.publish

object Publisher {

  def publishLocalM2(groupId: String, artifactId: String, version: String, files: Seq[os.Path]): Unit = {
    // ~/.m2/repository/group/artifact/version
    val home = System.getProperty("user.home")
    val m2Repo = os.home / ".m2/repository" / groupId.split('.') / artifactId / version
    os.makeDir.all(m2Repo)

    // copy all artifacts
    files.foreach { file =>
      val dest = m2Repo / file.last
      os.copy.over(file, dest)
    }
  }
}
