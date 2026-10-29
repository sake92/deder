package ba.sake.deder

import java.nio.file.{Files, StandardCopyOption}

private[deder] object CacheMetadata {
  def write(destination: os.Path, json: String): Unit = {
    os.makeDir.all(destination / os.up)
    val temporary = Files.createTempFile((destination / os.up).toNIO, "metadata-", ".tmp")
    try {
      os.write.over(os.Path(temporary), json)
      Files.move(temporary, destination.toNIO, StandardCopyOption.ATOMIC_MOVE)
    } finally {
      Files.deleteIfExists(temporary)
    }
  }
}
