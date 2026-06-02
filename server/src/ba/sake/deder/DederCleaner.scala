package ba.sake.deder

object DederCleaner {

  /** Walk dir tree and return total bytes. Returns 0 if dir doesn't exist. */
  def scanSize(dir: os.Path): Long =
    if os.exists(dir) then
      os.walk(dir).filter(os.isFile).map(_.size).sum
    else 0L

  /** Delete the directory. Returns bytes freed. Throws on failure. */
  def cleanDir(dir: os.Path): Long =
    val size = scanSize(dir)
    os.remove.all(dir, ignoreErrors = false)
    size

  /** Format bytes as human-readable string (e.g. "8.1 MB", "432 KB", "0 B"). */
  def humanReadable(bytes: Long): String =
    bytes match
      case b if b >= 1_000_000_000L => f"${b.toDouble / 1_000_000_000L}%.1f GB"
      case b if b >= 1_000_000L      => f"${b.toDouble / 1_000_000L}%.1f MB"
      case b if b >= 1_000L          => f"${b.toDouble / 1_000L}%.0f KB"
      case b                         => s"$b B"
}
