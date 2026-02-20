import org.scalajs.dom
import org.scalajs.dom.Element
import scala.concurrent.ExecutionContext.Implicits.global

@main def helloScalaJs: Unit = {
  val fetchFuture = dom.fetch("http://localhost:8181/hello/Scala.js").toFuture
  fetchFuture.foreach { response =>
    if (response.ok) {
      response.text().toFuture.foreach { text =>
        val appElement = dom.document.getElementById("app")
        appElement.textContent = text
      }
    } else {
      println(s"Failed to fetch: ${response.statusText}")
    }
  }
}
