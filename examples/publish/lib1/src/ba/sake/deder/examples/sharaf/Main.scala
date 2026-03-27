package ba.sake.deder.examples.sharaf

import ba.sake.sharaf.*
import ba.sake.sharaf.undertow.UndertowSharafServer

@main def serverMain(): Unit = {
  val routes = Routes { case GET -> Path("hello", name) =>
    Response.withBody(s"Merhaba $name")
  }


  UndertowSharafServer("localhost", 8181, routes).start()

  println(s"Server started at http://localhost:8181")
}