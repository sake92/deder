package ba.sake.deder

import ba.sake.deder.config.ConfigParser
import ba.sake.deder.deps.DependencyResolver
import ba.sake.deder.zinc.ZincCompiler
import coursier.parse.DependencyParser
import scala.jdk.CollectionConverters.*

@main def serverMain(moduleId: String, taskName: String): Unit = {

  if getMajorJavaVersion() < 21 then abort("Must use JDK >= 21")

  val projectRoot = os.pwd / "examples/multi"
  System.setProperty("DEDER_PROJECT_ROOT_DIR", projectRoot.toString)

  val cliServer = DederCliServer((projectRoot / ".deder/cli.sock").toNIO)
  // cliServer.start()

  // TODO check unique module ids
  val configParser = ConfigParser()
  val configFile = DederGlobals.projectRootDir / "deder.pkl"
  val projectConfig = configParser.parse(configFile)
  val allModules = projectConfig.modules.asScala.toSeq

  val compilerBridgeJar = DependencyResolver.fetchOne(
    DependencyParser.dependency(s"org.scala-sbt:compiler-bridge_2.13:1.11.0", "2.13").toOption.get
  )
  val zincCompiler = ZincCompiler(compilerBridgeJar)
  val tasksRegistry = TasksRegistry(zincCompiler)

  val tasksResolver = TasksResolver(projectConfig, tasksRegistry)
  val executionPlanner = ExecutionPlanner(tasksResolver.tasksGraph, tasksResolver.tasksPerModule)
  val tasksExecSubgraph = executionPlanner.execSubgraph(moduleId, taskName)
  val tasksExecStages = executionPlanner.execStages(moduleId, taskName)
  val tasksExecutor = TasksExecutor(projectConfig, tasksResolver.tasksGraph)

  println("Modules graph:")
  println(GraphUtils.generateDOT(tasksResolver.modulesGraph, v => v.id, v => Map("label" -> v.id)))
  println("Tasks graph:")
  println(GraphUtils.generateDOT(tasksResolver.tasksGraph, v => v.id, v => Map("label" -> v.id)))
  println("Planned exec subgraph:")
  println(GraphUtils.generateDOT(tasksExecSubgraph, v => v.id, v => Map("label" -> v.id)))
  println("Exec stages:")
  println(tasksExecStages.map(_.map(_.id)).mkString("\n"))

  println("#" * 50)
  tasksExecutor.execute(tasksExecStages)
}

def abort(message: String) =
  throw new RuntimeException(message)

def getMajorJavaVersion(): Int = {
  // Java 9+ versions are just the major number (e.g., "17")
  // Java 8- versions start with "1." (e.g., "1.8.0_202")
  val parts = scala.util.Properties.javaVersion.split('.')
  if (parts.length > 1 && parts(0) == "1") {
    // For Java 8 and earlier (e.g., "1.8.x"), the major version is the second part ("8")
    parts(1).toIntOption.getOrElse(0)
  } else {
    // For Java 9+ (e.g., "9", "11", "17.0.1"), the major version is the first part
    parts(0).toIntOption.getOrElse(0)
  }
}
