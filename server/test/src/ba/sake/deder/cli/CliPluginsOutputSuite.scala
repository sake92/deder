package ba.sake.deder.cli

import ba.sake.deder.{LoadedPluginInfo, PlainTextWritable}

class CliPluginsOutputSuite extends munit.FunSuite {

  test("shows sorted task names and preserves plugin order") {
    val output = PluginsOutput(
      Seq(
        LoadedPluginInfo("second-plugin", Seq("zTask", "aTask")),
        LoadedPluginInfo("first-plugin", Seq("onlyTask")),
        LoadedPluginInfo("broken-plugin", Seq.empty, Some("failed to load"))
      )
    )

    val rendered = summon[PlainTextWritable[PluginsOutput]].write(output)

    assertEquals(
      rendered,
      """second-plugin (aTask, zTask)
        |first-plugin (onlyTask)
        |broken-plugin (error: failed to load)""".stripMargin
    )
  }

  test("renders an empty plugin list clearly") {
    val rendered = summon[PlainTextWritable[PluginsOutput]].write(PluginsOutput(Seq.empty))
    assertEquals(rendered, "No plugins configured.")
  }
}
