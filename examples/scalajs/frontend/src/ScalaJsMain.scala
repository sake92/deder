import org.scalajs.dom
import org.scalajs.dom.Element

@main def helloScalaJs: Unit = {
  dom.document.getElementById("app").textContent = "Hello, Scala.js!"
}
