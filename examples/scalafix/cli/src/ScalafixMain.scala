

object ScalafixMain {
  def main(args: Array[String]): Unit = {
    
    println("Hello from Scala!")
  }
  def myMethod: Map[Int, String] = 1.to(10).map(i => i -> i.toString).toMap
}
