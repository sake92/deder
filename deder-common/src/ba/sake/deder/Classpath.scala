package ba.sake.deder

/** An ordered classpath. Concatenation via [[++]] removes duplicate entries keeping the LAST
  * occurrence, which preserves deder's existing classpath-shadowing order (the `reverse.distinct.reverse`
  * idiom that used to be copy-pasted across the compile/run classpath tasks).
  */
final case class Classpath(entries: Seq[os.Path]) {
  def ++(other: Classpath): Classpath =
    Classpath((entries ++ other.entries).reverse.distinct.reverse)
}

object Classpath {
  val empty: Classpath = Classpath(Seq.empty[os.Path])
}
