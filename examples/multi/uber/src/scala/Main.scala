package uber

object Main extends App {
  println(s"Hello from final module!")
  println(frontend.Frontend.value)
  println(backend.Backend.value)
}
