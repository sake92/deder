package ba.sake.deder.publish

import scala.util.Try
import com.typesafe.scalalogging.StrictLogging

/** Derives a semver version from the nearest git tag.
  *
  * Inspired by https://github.com/mdomke/git-semver
  *
  * Algorithm:
  *   1. Run `git describe --tags --dirty --match "v[0-9]*.*.*" --match "[0-9]*.*.*"` from the project root
  *   2. If exactly on a tag `vX.Y.Z` with clean working tree, return `X.Y.Z`
  *   3. If dirty or commits ahead, return `X.Y.(Z+1)-SNAPSHOT`
  *   4. If no matching tag or git not available, return `0.1.0-SNAPSHOT`
  */
object GitSemVer extends StrictLogging {

  val DefaultVersion = "0.1.0-SNAPSHOT"

  // git describe output when commits ahead: v1.2.3-5-gabcdef or v1.2.3-5-gabcdef-dirty
  private val DescribeAheadRegex = """^v?(\d+)\.(\d+)\.(\d+)-(\d+)-g[0-9a-f]+(-dirty)?$""".r

  // exact tag but dirty: v1.2.3-dirty
  private val ExactDirtyRegex = """^v?(\d+)\.(\d+)\.(\d+)-dirty$""".r

  // matches: v1.2.3 or 1.2.3 (with optional v prefix)
  private val SemverTagRegex = """^v?(\d+)\.(\d+)\.(\d+)(.*)$""".r

  /** Detect the version from git tags in the given directory.
    *
    * @param cwd
    *   the directory to run git in (typically the project root)
    * @return
    *   the resolved semver version string
    */
  def detectVersion(cwd: os.Path): String =
    gitDescribe(cwd) match {
      case Some(described) => parseDescribeOutput(described)
      case None            => DefaultVersion
    }

  private[publish] def gitDescribe(cwd: os.Path): Option[String] =
    Try {
      val result = os.proc("git", "describe", "--tags", "--dirty", "--match", "v[0-9]*.*.*", "--match", "[0-9]*.*.*")
        .call(cwd = cwd, stderr = os.Pipe, check = false)
      Option.when(result.exitCode == 0)(result.out.text().trim)
    }.recover { case e: Exception =>
      logger.debug(s"Git not available or failed: ${e.getMessage}")
      None
    }.getOrElse(None)

  private[publish] def parseDescribeOutput(described: String): String =
    described match {
      case DescribeAheadRegex(major, minor, patch, _, _) =>
        // N commits ahead of tag (clean or dirty) => bump patch, SNAPSHOT
        s"$major.$minor.${patch.toInt + 1}-SNAPSHOT"
      case ExactDirtyRegex(major, minor, patch) =>
        // exactly on tag but dirty working tree => bump patch, SNAPSHOT
        s"$major.$minor.${patch.toInt + 1}-SNAPSHOT"
      case SemverTagRegex(major, minor, patch, suffix) if suffix.isEmpty =>
        // exactly on a semver tag, clean working tree
        s"$major.$minor.$patch"
      case SemverTagRegex(major, minor, patch, suffix) =>
        // tag with pre-release suffix like v1.2.3-RC1
        s"$major.$minor.$patch$suffix"
      case _ =>
        // non-semver tag or unexpected format
        DefaultVersion
    }
}

