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

  /** True if the path is anywhere under `.deder/`.
    * Covers the entire `.deder/` tree: build output, logs, lock files, sockets, etc.
    * Callers must check `server.properties` separately if needed (it's under `.deder/` too). */
  def isDederArtifact(path: os.Path, projectRoot: os.Path): Boolean =
    path.startsWith(projectRoot) &&
    path.relativeTo(projectRoot).segments.headOption.contains(".deder")

  /** True if the path's first segment relative to project root is a known dev/build-tool directory.
    * Uses root-relative prefix matching (not segment containment) to avoid false positives
    * on nested directories named "target" or "out" inside source trees.
    * Returns false for paths outside `projectRoot` (instead of throwing). */
  def isDevArtifact(path: os.Path, projectRoot: os.Path): Boolean =
    path.startsWith(projectRoot) &&
    ignoredDirNames.contains(path.relativeTo(projectRoot).segments.headOption.getOrElse(""))
