package ba.sake.deder.plugin

import ba.sake.deder.{DederPluginApi, PluginInitParams, AbstractTask}

class PluginLoaderSuite extends munit.FunSuite {

  private def mkLoadedPlugin(id: String, hash: String): LoadedPlugin = {
    val plugin = new DederPluginApi {
      def id: String = id
      def init(params: PluginInitParams): Either[String, Seq[AbstractTask[?]]] = Right(Seq.empty)
      override def close(): Unit = ()
    }
    LoadedPlugin(plugin, s"config-$id", Seq.empty, hash, Seq.empty, null)
  }

  test("partitionPlugins keeps all plugins when all hashes match") {
    val existing = Seq(
      mkLoadedPlugin("p1", "hash1"),
      mkLoadedPlugin("p2", "hash2"),
    )
    val newHashes = Map("p1" -> "hash1", "p2" -> "hash2")
    val (toKeep, toUnload) = partitionPlugins(existing, newHashes)
    assertEquals(toKeep.map(_.plugin.id), Seq("p1", "p2"))
    assertEquals(toUnload, Seq.empty)
  }

  test("partitionPlugins unloads plugin removed from new config") {
    val existing = Seq(
      mkLoadedPlugin("p1", "hash1"),
      mkLoadedPlugin("p2", "hash2"),
    )
    val newHashes = Map("p1" -> "hash1")
    val (toKeep, toUnload) = partitionPlugins(existing, newHashes)
    assertEquals(toKeep.map(_.plugin.id), Seq("p1"))
    assertEquals(toUnload.map(_.plugin.id), Seq("p2"))
  }

  test("partitionPlugins unloads plugin with changed hash") {
    val existing = Seq(mkLoadedPlugin("p1", "old-hash"))
    val newHashes = Map("p1" -> "new-hash")
    val (toKeep, toUnload) = partitionPlugins(existing, newHashes)
    assertEquals(toKeep, Seq.empty)
    assertEquals(toUnload.map(_.plugin.id), Seq("p1"))
  }

  test("partitionPlugins handles empty existing plugins") {
    val (toKeep, toUnload) = partitionPlugins(Seq.empty, Map("p1" -> "hash1"))
    assertEquals(toKeep, Seq.empty)
    assertEquals(toUnload, Seq.empty)
  }

  test("partitionPlugins handles empty new config") {
    val existing = Seq(mkLoadedPlugin("p1", "hash1"))
    val (toKeep, toUnload) = partitionPlugins(existing, Map.empty)
    assertEquals(toKeep, Seq.empty)
    assertEquals(toUnload.map(_.plugin.id), Seq("p1"))
  }
}
