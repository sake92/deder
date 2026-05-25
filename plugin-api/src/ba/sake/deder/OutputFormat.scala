package ba.sake.deder

/** DenseJson is a machine-friendly format does not use pretty-printing or newlines, and is optimized for compactness
  * and ease of parsing.
  */
enum OutputFormat:
  case PlainText, Json, DenseJson, Dot, Mermaid
