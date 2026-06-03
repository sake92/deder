package lib
import lib.Platform
object Lib {
  def platformName: String = Platform.name
  def add(a: Int, b: Int): Int = a + b
}
