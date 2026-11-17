package compatibility

object Compatibility {
  def supportedJdk: Boolean =
    Runtime.version().feature() >= 21
}
