package ba.sake.deder.config

/** Server-side model for the result of attempting to load `deder.pkl`.
  *
  * Used to deduplicate BSP notifications: only fan-out when the effective
  * diagnostic state (fingerprint) actually changes.
  */
sealed trait ConfigDiagnostic:
  def fingerprint: String

/** Config was parsed successfully.
  *
  * @param visibleModuleIds BSP-visible module IDs computed from the loaded config.
  */
case class ValidConfig(visibleModuleIds: Set[String]) extends ConfigDiagnostic:
  val fingerprint: String = visibleModuleIds.toList.sorted.mkString(",")

/** Config could not be parsed.
  *
  * @param message  Human-readable error summary (first ~300 chars of the Pkl message).
  * @param fileUri  URI of the file where the error occurred, if extractable.
  * @param startLine 1-based line number, or 1 if unknown.
  * @param startCol  0-based column, or 0 if unknown.
  */
case class InvalidConfig(
    message: String,
    fileUri: Option[String],
    startLine: Int,
    startCol: Int
) extends ConfigDiagnostic:
  val fingerprint: String = s"${fileUri.getOrElse("")}|$startLine|${message.take(300)}"
