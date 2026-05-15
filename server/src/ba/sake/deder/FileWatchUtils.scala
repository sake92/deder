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

  /** Reads a .gitignore file at the given path and returns parsed patterns.
    * Strips comments (lines starting with #) and empty lines.
    * Preserves `!` prefix for negation support.
    * Returns empty Seq if the file does not exist. */
  def readGitignorePatterns(file: os.Path): Seq[String] =
    if os.exists(file) && os.isFile(file) then
      os.read.lines(file)
        .map(_.trim)
        .filter(l => l.nonEmpty && !l.startsWith("#"))
    else
      Seq.empty

  /** Checks whether `relativePath` matches any pattern in the list.
    * Patterns are evaluated in order — the LAST matching pattern wins,
    * enabling proper `!` negation semantics.
    *
    * Supports:
    *   - `*` — matches any characters except /
    *   - `**` — matches zero or more directories
    *   - `?` — matches any single character except /
    *   - Leading `/` — anchors to root
    *   - Trailing `/` — matches directories only
    *   - `!` prefix — negation (un-ignores a path)
    *
    * @param relativePath relative path from project root, with `/` separators
    * @param isDir whether the path is a directory
    * @param patterns parsed gitignore patterns (from readGitignorePatterns)
    */
  def isIgnoredByGitignore(relativePath: String, isDir: Boolean, patterns: Seq[String]): Boolean =
    var ignored = false
    for pattern <- patterns do
      if pattern.startsWith("!") then
        val p = pattern.stripPrefix("!")
        if globMatch(p, relativePath, isDir) then ignored = false
      else
        if globMatch(pattern, relativePath, isDir) then ignored = true
    ignored

  /** Matches a single gitignore pattern against a relative path. */
  private def globMatch(pattern: String, path: String, isDir: Boolean): Boolean =
    var p = pattern

    // Trailing / means match directories only
    if p.endsWith("/") then
      if !isDir then return false
      p = p.stripSuffix("/")

    // Normalize path: append / for directories so prefix matching works
    val normalizedPath = if isDir then path + "/" else path

    if p.contains("/") then
      // Pattern has path separator — match against full relative path
      val pClean = if p.startsWith("/") then p.stripPrefix("/") else p
      if pClean.contains("**") || pClean.contains("*") || pClean.contains("?") then
        // Strip trailing / from normalized dir paths for regex matching
        val regexPath = if normalizedPath.endsWith("/") then normalizedPath.stripSuffix("/") else normalizedPath
        globToRegex(pClean).matches(regexPath)
      else
        // Prefix match with path-boundary check so "build/output" matches
        // "build/output/" but NOT "build/output2.class"
        normalizedPath.startsWith(pClean)
        && (normalizedPath.length == pClean.length || normalizedPath.charAt(pClean.length) == '/')
    else
      // No separator — match against filename
      val filename = normalizedPath.split("/").last
      simpleGlobMatch(p, filename)

  /** Converts a glob pattern containing ** to a Regex. */
  private def globToRegex(pattern: String): scala.util.matching.Regex =
    val sb = new StringBuilder
    sb.append("^")
    var i = 0
    while i < pattern.length do
      pattern.charAt(i) match
        case '*' =>
          if i + 1 < pattern.length && pattern.charAt(i + 1) == '*' then
            // ** followed by / should match zero or more directories
            if i + 2 < pattern.length && pattern.charAt(i + 2) == '/' then
              sb.append("(.*/)?")
              i += 2 // skip second * and the following /
              // skip the / (the i += 1 at end of loop will advance past it)
            else
              sb.append(".*")
              i += 1
          else
            sb.append("[^/]*")
        case '?' => sb.append("[^/]")
        case '.' => sb.append("\\.")
        case c   => sb.append(c)
      i += 1
    sb.append("$")
    sb.toString.r

  /** Simple glob match for filename-only patterns (no / in pattern). */
  private def simpleGlobMatch(pattern: String, str: String): Boolean =
    val regex = pattern
      .replace(".", "\\.")
      .replace("*", ".*")
      .replace("?", ".")
    str.matches(regex)
