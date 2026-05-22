package ba.sake.deder

/** Sealed trait for output format dispatch in RequestContext and render(). */
sealed trait OutputFormat

enum ExecOutputFormat extends OutputFormat:
  case PlainText, Json

enum GraphOutputFormat extends OutputFormat:
  case PlainText, Json, Dot, Mermaid
