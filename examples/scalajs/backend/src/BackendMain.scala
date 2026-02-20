import ba.sake.sharaf.{*, given}
import ba.sake.sharaf.undertow.UndertowSharafServer

@main def backendMain: Unit = {

  val routes = Routes {
    case GET -> Path("hello", name) =>
      Response.withBody(s"Hello $name")
  }

  UndertowSharafServer("localhost", 8181, routes).start()

  println(s"Server started at http://localhost:8181")
}