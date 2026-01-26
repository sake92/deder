package ba.sake.deder.bsp

// adapted from
// https://github.com/build-server-protocol/build-server-protocol/blob/v2.2.0-M2/tests/src/test/scala/tests/MockClientSuite.scala

import java.nio.file.{Files, Path}
import java.util.Collections
import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.util.Properties
import com.google.common.collect.Lists
import ch.epfl.scala.bsp.testkit.client.TestClient
import ch.epfl.scala.bsp4j.*

class BspIntegrationSuite extends munit.FunSuite {

  // first compile can take a while
  override def munitTimeout: Duration = 2.minutes

  private val testResourceDir = os.Path(System.getenv("MILL_TEST_RESOURCE_DIR"))
  private val dederClientPath = System.getenv("DEDER_CLIENT_PATH")
  private val dederServerPath = System.getenv("DEDER_SERVER_PATH")

  private val testDirectory = Files.createTempDirectory("BspIntegrationSuite").toAbsolutePath
  os.copy(
    testResourceDir / "sample-projects/multi",
    os.Path(testDirectory),
    createFolders = true,
    replaceExisting = true
  )
  os.write.over(
    os.Path(testDirectory) / ".deder/server.properties",
    s"localPath=$dederServerPath\nlogLevel=DEBUG\n",
    createFolders = true
  )

  println(testDirectory.toAbsolutePath)

  private val initializeBuildParams =
    new InitializeBuildParams(
      "Mock-Client",
      "0.0",
      "2.0",
      testDirectory.toUri.toString,
      new BuildClientCapabilities(Collections.singletonList("scala"))
    )

  private val baseUri = testDirectory.toUri.toString
  private given ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  private val languageIds = List("scala", "java").asJava

  override def beforeAll(): Unit =
    executeDederCommand(os.Path(testDirectory), "bsp install")

  override def afterAll(): Unit =
    executeDederCommand(os.Path(testDirectory), "shutdown")

  private val client = TestClient(
    () => {
      val bspServerProcess =
        os.proc(dederClientPath, "bsp")
          .spawn(cwd = os.Path(testDirectory))
      val out: java.io.OutputStream = bspServerProcess.stdin
      val in: java.io.InputStream = bspServerProcess.stdout
      (out, in, () => bspServerProcess.destroy(shutdownGracePeriod = 0))
    },
    initializeBuildParams
  )

  private def executeDederCommand(projectPath: os.Path, command: String): os.CommandResult = {
    val shell = if Properties.isWin then Seq("cmd.exe", "/C") else Seq("bash", "-c")
    val cmd = shell ++ Seq(s"$dederClientPath $command")
    os.proc(cmd).call(cwd = projectPath)
  }

  private val coursierCacheDir = coursierapi.Cache.create().getLocation.toPath

  private def coursierCachedFile(dep: String): Path = {
    // file:///home/sake/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala3-library_3/3.7.1/scala3-library_3-3.7.1.jar
    val depPath = s"https/repo1.maven.org/maven2/${dep}"
    coursierCacheDir.resolve(depPath)
  }

  // targets / modules
  val commonTargetId = new BuildTargetIdentifier(s"${baseUri}#common")
  val commonTarget = new BuildTarget(
    commonTargetId,
    List(BuildTargetTag.LIBRARY).asJava,
    languageIds,
    Collections.emptyList(), {
      val capabilities = new BuildTargetCapabilities()
      capabilities.setCanCompile(true)
      capabilities.setCanRun(false)
      capabilities.setCanTest(false)
      capabilities.setCanDebug(false)
      capabilities
    }
  )
  val frontendTargetId = new BuildTargetIdentifier(s"${baseUri}#frontend")
  val frontendTarget = new BuildTarget(
    frontendTargetId,
    List(BuildTargetTag.LIBRARY).asJava,
    languageIds,
    Lists.newArrayList(commonTargetId), {
      val capabilities = new BuildTargetCapabilities()
      capabilities.setCanCompile(true)
      capabilities.setCanRun(false)
      capabilities.setCanTest(false)
      capabilities.setCanDebug(false)
      capabilities
    }
  )
  val backendTargetId = new BuildTargetIdentifier(s"${baseUri}#backend")
  val backendTarget = new BuildTarget(
    backendTargetId,
    List(BuildTargetTag.LIBRARY).asJava,
    languageIds,
    Lists.newArrayList(commonTargetId), {
      val capabilities = new BuildTargetCapabilities()
      capabilities.setCanCompile(true)
      capabilities.setCanRun(false)
      capabilities.setCanTest(false)
      capabilities.setCanDebug(false)
      capabilities
    }
  )
  val uberTargetId = new BuildTargetIdentifier(s"${baseUri}#uber")
  val uberTarget = new BuildTarget(
    uberTargetId,
    List(BuildTargetTag.APPLICATION).asJava,
    languageIds,
    Lists.newArrayList(frontendTargetId, backendTargetId), {
      val capabilities = new BuildTargetCapabilities()
      capabilities.setCanCompile(true)
      capabilities.setCanRun(true)
      capabilities.setCanTest(false)
      capabilities.setCanDebug(false)
      capabilities
    }
  )
  val uberTestTargetId = new BuildTargetIdentifier(s"${baseUri}#uber-test")
  val uberTestTarget = new BuildTarget(
    uberTestTargetId,
    List(BuildTargetTag.TEST).asJava,
    languageIds,
    Lists.newArrayList(uberTargetId), {
      val capabilities = new BuildTargetCapabilities()
      capabilities.setCanCompile(true)
      capabilities.setCanRun(false)
      capabilities.setCanTest(true)
      capabilities.setCanDebug(false)
      capabilities
    }
  )

  private val allTargets = List(commonTarget, frontendTarget, backendTarget, uberTarget, uberTestTarget)
  private val allTargetIds = allTargets.map(_.getId)
  allTargets.foreach { t =>
    val scalaBuildTarget =
      new ScalaBuildTarget("org.scala-lang", "3.7.1", "3", ScalaPlatform.JVM, List.empty.asJava)
    t.setData(scalaBuildTarget)
    t.setDataKind(BuildTargetDataKind.SCALA)
  }

  test("Initialize connection followed by its shutdown") {
    client.testInitializeAndShutdown()
  }


  test("Workspace Build Targets") {
    val targets = allTargets.asJava
    val workspaceBuildTargetsResult = new WorkspaceBuildTargetsResult(targets)
    client.testCompareWorkspaceTargetsResults(workspaceBuildTargetsResult)
  }

  test("Initial imports") {
    client.testResolveProject()
  }

  test("Test server capabilities") {
    client.testTargetCapabilities()
  }

  test("Running batch tests") {
    client.wrapTest(session => {
      client
        .testResolveProject(session, false, false)
        .map(_ => client.targetsCompileSuccessfully(session))
        .flatMap(_ => client.cleanCacheSuccessfully(session))
    })
  }

  test("Test Compile of all targets") {
    client.testTargetsCompileSuccessfully(true)
  }

  test("Clean cache") {
    client.testCleanCacheSuccessfully()
  }

  test("Run Targets") {
    client.testTargetsRunSuccessfully(
      Lists.newArrayList(uberTarget)
    )
  }

  test("Run Tests") {
    client.testTargetsTestSuccessfully(
      Lists.newArrayList(uberTestTarget)
    )
  }

  test("Run javacOptions") {
    def classDirectory(moduleId: String) =
      testDirectory.resolve(s".deder/out/${moduleId}/classes").toUri.toString
    def semanticdbDirectory(moduleId: String) =
        testDirectory.resolve(s".deder/out/${moduleId}/semanticdb").toString
    def javacOptions(moduleId: String) = List(
      "-processorpath",
      coursierCachedFile("com/sourcegraph/semanticdb-javac/0.11.1/semanticdb-javac-0.11.1.jar").toString,
      s"-Xplugin:semanticdb -sourceroot:${testDirectory} -targetroot:${semanticdbDirectory(moduleId)} -build-tool:sbt"
    ).asJava
    val javacOptionsItems = List(
      new JavacOptionsItem(
        commonTargetId,
        javacOptions("common"),
        List(
          classDirectory("common"),
          coursierCachedFile("org/scala-lang/scala3-library_3/3.7.1/scala3-library_3-3.7.1.jar").toUri.toString,
          coursierCachedFile("org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.jar").toUri.toString
        ).asJava,
        classDirectory("common")
      ),
      new JavacOptionsItem(
        frontendTargetId,
        javacOptions("frontend"),
        List(
          classDirectory("frontend"),
          classDirectory("common"),
          coursierCachedFile("org/scala-lang/scala3-library_3/3.7.1/scala3-library_3-3.7.1.jar").toUri.toString,
          coursierCachedFile("org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.jar").toUri.toString
        ).asJava,
        classDirectory("frontend")
      ),
      new JavacOptionsItem(
        backendTargetId,
        javacOptions("backend"),
        List(
          classDirectory("backend"),
          classDirectory("common"),
          coursierCachedFile("org/scala-lang/scala3-library_3/3.7.1/scala3-library_3-3.7.1.jar").toUri.toString,
          coursierCachedFile("org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.jar").toUri.toString
        ).asJava,
        classDirectory("backend")
      ),
      new JavacOptionsItem(
        uberTargetId,
        javacOptions("uber"),
        List(
          classDirectory("uber"),
          classDirectory("backend"),
          classDirectory("frontend"),
          classDirectory("common"),
          coursierCachedFile("org/scala-lang/scala3-library_3/3.7.1/scala3-library_3-3.7.1.jar").toUri.toString,
          coursierCachedFile("org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.jar").toUri.toString
        ).asJava,
        classDirectory("uber")
      ),
      new JavacOptionsItem(
        uberTestTargetId,
        javacOptions("uber-test"),
        List(
          classDirectory("uber-test"),
          classDirectory("uber"),
          classDirectory("backend"),
          classDirectory("frontend"),
          classDirectory("common"),
          coursierCachedFile("org/scala-lang/scala3-library_3/3.7.1/scala3-library_3-3.7.1.jar").toUri.toString,
          coursierCachedFile("org/scalameta/munit_3/1.2.1/munit_3-1.2.1.jar").toUri.toString,
          coursierCachedFile("org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.jar").toUri.toString,
          coursierCachedFile("org/scalameta/junit-interface/1.2.1/junit-interface-1.2.1.jar").toUri.toString,
          coursierCachedFile("org/scalameta/munit-diff_3/1.2.1/munit-diff_3-1.2.1.jar").toUri.toString,
          coursierCachedFile("junit/junit/4.13.2/junit-4.13.2.jar").toUri.toString,
          coursierCachedFile("org/scala-sbt/test-interface/1.0/test-interface-1.0.jar").toUri.toString,
          coursierCachedFile("org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar").toUri.toString
        ).asJava,
        classDirectory("uber-test")
      )
    ).asJava
    client.testJavacOptions(
      new JavacOptionsParams(allTargetIds.asJava),
      new JavacOptionsResult(javacOptionsItems)
    )
  }
/*
  test("Run scalacOptions") {
    def classDirectory(moduleId: String) =
      testDirectory.resolve(s".deder/out/${moduleId}/classes").toUri.toString
    def semanticdbDirectory(moduleId: String) =
        testDirectory.resolve(s".deder/out/${moduleId}/semanticdb").toString
    def options(moduleId: String) = List(
      "-Xsemanticdb",
      "-sourceroot",
      testDirectory.toString
    ).asJava

    val scalacOptionsItems = List(
      new ScalacOptionsItem(
        commonTargetId,
        options("common"),
        List(
          classDirectory("common"),
          coursierCachedFile("org/scala-lang/scala3-library_3/3.7.1/scala3-library_3-3.7.1.jar").toUri.toString,
          coursierCachedFile("org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.jar").toUri.toString
        ).asJava,
        classDirectory("common")
      ),
      new ScalacOptionsItem(
        frontendTargetId,
        options("frontend"),
        List(
          classDirectory("frontend"),
          classDirectory("common"),
          coursierCachedFile("org/scala-lang/scala3-library_3/3.7.1/scala3-library_3-3.7.1.jar").toUri.toString,
          coursierCachedFile("org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.jar").toUri.toString
        ).asJava,
        classDirectory("frontend")
      ),
      new ScalacOptionsItem(
        backendTargetId,
        options("backend"),
        List(
          classDirectory("backend"),
          classDirectory("common"),
          coursierCachedFile("org/scala-lang/scala3-library_3/3.7.1/scala3-library_3-3.7.1.jar").toUri.toString,
          coursierCachedFile("org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.jar").toUri.toString
        ).asJava,
        classDirectory("backend")
      ),
      new ScalacOptionsItem(
        uberTargetId,
        options("uber"),
        List(
          classDirectory("uber"),
          classDirectory("backend"),
          classDirectory("frontend"),
          classDirectory("common"),
          coursierCachedFile("org/scala-lang/scala3-library_3/3.7.1/scala3-library_3-3.7.1.jar").toUri.toString,
          coursierCachedFile("org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.jar").toUri.toString
        ).asJava,
        classDirectory("uber")
      ),
      new ScalacOptionsItem(
        uberTestTargetId,
        options("uber-test"),
        List(
          classDirectory("uber-test"),
          classDirectory("uber"),
          classDirectory("backend"),
          classDirectory("frontend"),
          classDirectory("common"),
          coursierCachedFile("org/scala-lang/scala3-library_3/3.7.1/scala3-library_3-3.7.1.jar").toUri.toString,
          coursierCachedFile("org/scalameta/munit_3/1.2.1/munit_3-1.2.1.jar").toUri.toString,
          coursierCachedFile("org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.jar").toUri.toString,
          coursierCachedFile("org/scalameta/junit-interface/1.2.1/junit-interface-1.2.1.jar").toUri.toString,
          coursierCachedFile("org/scalameta/munit-diff_3/1.2.1/munit-diff_3-1.2.1.jar").toUri.toString,
          coursierCachedFile("junit/junit/4.13.2/junit-4.13.2.jar").toUri.toString,
          coursierCachedFile("org/scala-sbt/test-interface/1.0/test-interface-1.0.jar").toUri.toString,
          coursierCachedFile("org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar").toUri.toString
        ).asJava,
        classDirectory("uber-test")
      )
    ).asJava
    client.testScalacOptions(
      new ScalacOptionsParams(allTargetIds.asJava),
      new ScalacOptionsResult(scalacOptionsItems)
    )
  }

  test("Run Scala Test Classes") {
    val classes1 = List("uber.MyTest").asJava
    val item1 = new ScalaTestClassesItem(uberTestTargetId, classes1)
    item1.setFramework("munit")
    val testClassesItems = List(item1).asJava
    val result = new ScalaTestClassesResult(testClassesItems)
    client.testScalaTestClasses(
      new ScalaTestClassesParams(Lists.newArrayList(uberTestTargetId)),
      result
    )
  }*/

  // TODO as soon as one fails, none other tests works :/
  /*
  test("Scala Test Classes with less items should fail") {
    val classes1 = List("uber.MyTest").asJava
    val testClassesItems = List(new ScalaTestClassesItem(uberTestTargetId, classes1)).asJava
    val result = new ScalaTestClassesResult(testClassesItems)
    Try(
      client.testScalaTestClasses(
        new ScalaTestClassesParams(Collections.emptyList()),
        result
      )
    ) match {
      case Failure(_) => assert(true)
      case Success(_) => fail("Test Classes should expect all item classes to be defined!")
    }
  }*/

  /*
  test("Run Scala Main Classes") {
    val classes1 = List(
      new ScalaMainClass("uber.Main", List().asJava, List().asJava)
    ).asJava
    val mainClassesItems = List(
      new ScalaMainClassesItem(uberTargetId, classes1)
    ).asJava
    val result = new ScalaMainClassesResult(mainClassesItems)
    client.testScalaMainClasses(
      new ScalaMainClassesParams(Lists.newArrayList(uberTargetId)),
      result
    )
  }*/


  // TODO as soon as one fails, none other tests works :/
  /*test("Scala Main Classes with less items should fail") {
    val classes1 = List(
      new ScalaMainClass("uber.Main", List().asJava, List().asJava)
    ).asJava
    val mainClassesItems = List(
      new ScalaMainClassesItem(uberTargetId, classes1)
    ).asJava
    val result = new ScalaMainClassesResult(mainClassesItems)
    Try(
      client.testScalaMainClasses(
        new ScalaMainClassesParams(Collections.emptyList()),
        result
      )
    ) match {
      case Failure(_) => assert(true)
      case Success(_) => fail("Test Classes should expect all item classes to be defined!")
    }
  }*/

  /*

  private def environmentItem(testing: Boolean) = {
    val classpath = List("scala-library.jar").asJava
    val jvmOptions = List("-Xms256m").asJava
    val environmentVariables = Map("A" -> "a", "TESTING" -> testing.toString).asJava
    val workdir = "/tmp"
    val item1 = new JvmEnvironmentItem(
      targetId1,
      classpath,
      jvmOptions,
      workdir,
      environmentVariables
    )
    val mainClass = new JvmMainClass("MainClass.java", List.empty[String].asJava)
    item1.setMainClasses(List(mainClass).asJava)
    List(item1).asJava
  }

  test("Jvm Run Environment") {
    client.testJvmRunEnvironment(
      new JvmRunEnvironmentParams(Collections.emptyList()),
      new JvmRunEnvironmentResult(environmentItem(testing = false))
    )
  }

  test("Jvm Test Environment") {
    client.testJvmTestEnvironment(
      new JvmTestEnvironmentParams(Collections.emptyList()),
      new JvmTestEnvironmentResult(environmentItem(testing = true))
    )
  }
   */
}
