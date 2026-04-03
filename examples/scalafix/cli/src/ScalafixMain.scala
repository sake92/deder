// try this to trigger the RemoveUnused rule:
// import java.util.ArrayList

object ScalafixMain {
  def main(args: Array[String]): Unit = {
    println("Hello from Scala!")
  }

  // try removing return type ": Map[Int, String]" to trigger the ExplicitResultTypes rule:
  def myMethod = 1.to(10).map(i => i -> i.toString).toMap
}
