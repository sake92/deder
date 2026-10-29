@main def main(): Unit =
  val stream = getClass.getResourceAsStream("/plugin-generated-resource.txt")
  if stream == null then throw IllegalStateException("plugin-generated-resource.txt is missing")
  try print(String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
  finally stream.close()
