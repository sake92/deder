package ba.sake.deder

object FileWatchUtils:

  /** Directory names that should never trigger rebuilds (dev tools + legacy build tools).
    * `target/` for sbt/maven, `out/` for mill, plus IDE/dotfile directories. */
  val ignoredDirNames: Set[String] = Set(
    ".git", ".github", ".idea", ".vscode", ".metals", ".bsp", ".scala-build",
    "target", "out"
  )

  /** Deder subdirectories under .deder/ that should be excluded from OS-level watching.
    * These are noisy build-output directories. .deder/ itself is NOT in this list
    * so that .deder/server.properties changes are still detected. */
  val ignoredDederSubdirs: Set[String] = Set("out", "logs")

  /** Excluded directory names for BSP buildTargetOutputPaths reporting.
    * Single source of truth — used by DederBspServer. */
  val bspExcludedDirNames: Seq[String] = Seq(".deder", ".bsp", ".metals", ".idea", ".vscode")

  /** True if path is under .deder/ (any file or directory under .deder/). */
  def isDederArtifact(path: os.Path, projectRoot: os.Path): Boolean =
    path.relativeTo(projectRoot).segments.toSeq.headOption.contains(".deder")

  /** True if the path's first segment relative to project root is a known dev/build-tool directory.
    * Uses root-relative prefix matching (not segment containment) to avoid false positives
    * on nested directories named "target" or "out" inside source trees. */
  def isDevArtifact(path: os.Path, projectRoot: os.Path): Boolean =
    val firstSegment = path.relativeTo(projectRoot).segments.toSeq.headOption.getOrElse("")
    ignoredDirNames.contains(firstSegment)
