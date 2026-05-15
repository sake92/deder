package ba.sake.deder.importing

/** Template default scalacOptions, transcribed from `DederTpolecat.pkl` and `DederTypelevel.pkl`.
  *
  * These are literal constants (not loaded from Pkl at runtime) so the renderer
  * can diff against them without requiring Pkl modulepath resolution on the classpath.
  * When the template `.pkl` files are updated, these constants must be synced manually.
  */
object TemplateOptionsReader {

  // ---- tpolecat options (from DederTpolecat.pkl) ----

  val tpolecat2_12: Set[String] = Set(
    "-encoding", "utf-8",
    "-deprecation", "-feature", "-unchecked",
    "-language:existentials", "-language:experimental.macros", "-language:higherKinds", "-language:implicitConversions",
    "-Xlint:adapted-args", "-Xlint:by-name-right-associative", "-Xlint:constant", "-Xlint:delayedinit-select",
    "-Xlint:deprecation", "-Xlint:doc-detached", "-Xlint:inaccessible", "-Xlint:infer-any",
    "-Xlint:missing-interpolator", "-Xlint:nullary-override", "-Xlint:nullary-unit", "-Xlint:option-implicit",
    "-Xlint:package-object-classes", "-Xlint:poly-implicit-overload", "-Xlint:private-shadow", "-Xlint:stars-align",
    "-Xlint:type-parameter-shadow", "-Xlint:unsound-match",
    "-Yno-adapted-args", "-Ypartial-unification", "-Ywarn-dead-code", "-Ywarn-extra-implicit",
    "-Ywarn-nullary-override", "-Ywarn-nullary-unit", "-Ywarn-numeric-widen", "-Ywarn-unused",
    "-Ywarn-unused:implicits", "-Ywarn-unused:imports", "-Ywarn-unused:locals", "-Ywarn-unused:params",
    "-Ywarn-unused:patvars", "-Ywarn-unused:privates", "-Ywarn-unused:nowarn",
  )

  val tpolecat2_13: Set[String] = Set(
    "-encoding", "utf-8",
    "-feature", "-unchecked",
    "-Wdead-code", "-Wextra-implicit", "-Wnumeric-widen", "-Wunused", "-Wvalue-discard",
    "-Wunused:implicits", "-Wunused:imports", "-Wunused:locals", "-Wunused:params", "-Wunused:patvars", "-Wunused:privates",
    "-Xlint:deprecation", "-Xlint:inaccessible", "-Xlint:infer-any", "-Xlint:missing-interpolator",
    "-Xlint:nullary-override", "-Xlint:private-shadow", "-Xlint:stars-align", "-Xlint:type-parameter-shadow",
    "-Xsource:3",
  )

  val tpolecat3: Set[String] = Set(
    "-encoding", "utf-8",
    "-deprecation", "-feature", "-unchecked",
    "-Wunused:implicits", "-Wunused:imports", "-Wunused:locals", "-Wunused:params", "-Wunused:privates",
    "-Xlint:deprecation", "-Xlint:inaccessible", "-Xlint:infer-any", "-Xlint:missing-interpolator",
    "-Xlint:private-shadow", "-Xlint:stars-align", "-Xlint:type-parameter-shadow",
  )

  // ---- typelevel options (from DederTypelevel.pkl) ----

  val typelevel2_12: Set[String] = Set(
    "-deprecation", "-encoding", "utf-8", "-feature", "-unchecked",
    "-Xlint", "-Yno-adapted-args", "-Ywarn-dead-code", "-Ywarn-unused-import",
    "-Xlint:_,-unused", "-Ywarn-unused:_,-nowarn,-privates",
    "-Ypartial-unification", "-language:_", "-Xsource:3",
  )

  val typelevel2_13: Set[String] = Set(
    "-deprecation", "-encoding", "utf-8", "-feature", "-unchecked",
    "-Wdead-code", "-Wextra-implicit", "-Wnumeric-widen", "-Wunused", "-Wvalue-discard",
    "-Xlint:_,-implicit-recursion,-recurse-with-default,-unused,-byname-implicit",
    "-Wconf:cat=scala3-migration:s", "-language:_", "-Xsource:3",
  )

  val typelevel3: Set[String] = Set(
    "-deprecation", "-encoding", "utf-8", "-feature", "-unchecked",
    "-Wunused:implicits", "-Wunused:imports", "-Wunused:locals",
    "-Wvalue-discard", "-Xlint:deprecation", "-Xlint:inaccessible",
    "-language:postfixOps", "-Xsource:3",
  )

  // ---- public API ----

  /** Returns the default tpolecat scalacOptions for the given Scala version. */
  def tpolecatScalacOptions(scalaVersion: String): Set[String] =
    scalaVersion match {
      case v if v.startsWith("3")    => tpolecat3
      case v if v.startsWith("2.13") => tpolecat2_13
      case v if v.startsWith("2.12") => tpolecat2_12
      case _                          => tpolecat2_13 // default: latest 2.x
    }

  /** Returns the default typelevel scalacOptions for the given Scala version. */
  def typelevelScalacOptions(scalaVersion: String): Set[String] =
    scalaVersion match {
      case v if v.startsWith("3")    => typelevel3
      case v if v.startsWith("2.13") => typelevel2_13
      case v if v.startsWith("2.12") => typelevel2_12
      case _                          => typelevel2_13 // default: latest 2.x
    }
}
