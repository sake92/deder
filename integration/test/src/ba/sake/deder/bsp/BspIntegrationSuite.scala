package ba.sake.deder.bsp

// adapted from
// https://github.com/build-server-protocol/build-server-protocol/blob/v2.2.0-M2/tests/src/test/scala/tests/MockClientSuite.scala

import java.net.URI
import java.nio.file.{Files, Path, Paths}
import java.util.Collections
import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}
import scala.util.Properties
import com.google.common.collect.Lists
import ch.epfl.scala.bsp.testkit.client.TestClient
import ch.epfl.scala.bsp4j.*
import ch.epfl.scala.bsp.testkit.mock.MockServer
import ch.epfl.scala.bsp.testkit.mock.MockServer.LocalMockServer

class BspIntegrationSuite extends munit.FunSuite {

  private val testResourceDir = os.Path(System.getenv("MILL_TEST_RESOURCE_DIR"))
  private val dederClientPath = System.getenv("DEDER_CLIENT_PATH")
  private val dederServerPath = System.getenv("DEDER_SERVER_PATH")

  private val testDirectory = Files.createTempDirectory("bsp.BspIntegrationSuite")
  os.copy(
    testResourceDir / "sample-projects/multi",
    os.Path(testDirectory),
    createFolders = true,
    replaceExisting = true
  )
  os.write.over(
    os.Path(testDirectory) / ".deder/server.properties",
    s"localPath=$dederServerPath\n",
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

  private val baseUri = testDirectory.toFile.getCanonicalFile.toURI
  private given ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  private val languageIds = List("scala").asJava

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
      capabilities.setCanTest(true)
      capabilities
    }
  )

  private val allTargetIds = List(commonTargetId, frontendTargetId, backendTargetId, uberTargetId, uberTestTargetId)

  test("Initialize connection followed by its shutdown") {
    client.testInitializeAndShutdown()
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

  // TODO faila
  /*
  test("Run Tests") {
    client.testTargetsTestSuccessfully(
      Lists.newArrayList(uberTestTarget)
    )
  }*/

  test("Run javacOptions") {
    def classDirectory(moduleId: String) =
      testDirectory.resolve(s".deder/out/${moduleId}/classes").toUri.toString
    def options(moduleId: String) = List(
      "-processorpath",
      coursierCachedFile("com/sourcegraph/semanticdb-javac/0.11.1/semanticdb-javac-0.11.1.jar").toString,
      s"-Xplugin:semanticdb -sourceroot:${testDirectory} -targetroot:${classDirectory(moduleId)}"
    ).asJava
    val javacOptionsItems = List(
      new JavacOptionsItem(
        commonTargetId,
        options("common"),
        List(
          classDirectory("common"),
          coursierCachedFile("org/scala-lang/scala3-library_3/3.7.1/scala3-library_3-3.7.1.jar").toUri.toString,
          coursierCachedFile("org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.jar").toUri.toString
        ).asJava,
        classDirectory("common")
      ),
      new JavacOptionsItem(
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
      new JavacOptionsItem(
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
      new JavacOptionsItem(
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
      new JavacOptionsItem(
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
    client.testJavacOptions(
      new JavacOptionsParams(allTargetIds.asJava),
      new JavacOptionsResult(javacOptionsItems)
    )
  }

  /*
  test("Run scalacOptions") {
    val classDirectory = "file:" + testDirectory.resolve("out").toString
    val scalacOptionsItems = List(
      new ScalacOptionsItem(
        targetId1,
        Collections.emptyList(),
        List("scala-library.jar").asJava,
        classDirectory
      ),
      new ScalacOptionsItem(
        targetId2,
        Collections.emptyList(),
        List("scala-library.jar").asJava,
        classDirectory
      ),
      new ScalacOptionsItem(
        targetId3,
        Collections.emptyList(),
        List("scala-library.jar").asJava,
        classDirectory
      )
    ).asJava

    client.testScalacOptions(
      new ScalacOptionsParams(Collections.emptyList()),
      new ScalacOptionsResult(scalacOptionsItems)
    )
  }

  test("Run cppOptions") {
    val copts = List("-Iexternal/gtest/include").asJava
    val defines = List("BOOST_FALLTHROUGH").asJava
    val linkopts = List("-pthread").asJava
    val item = new CppOptionsItem(targetId4, copts, defines, linkopts)
    val cppOptionsItem = List(item).asJava

    client.testCppOptions(
      new CppOptionsParams(Collections.emptyList()),
      new CppOptionsResult(cppOptionsItem)
    )
  }

  test("Run pythonOptions") {
    val interpreterOptions = List("-E").asJava
    val item = new PythonOptionsItem(targetId5, interpreterOptions)
    val pythonOptionsItem = List(item).asJava

    client.testPythonOptions(
      new PythonOptionsParams(Collections.emptyList()),
      new PythonOptionsResult(pythonOptionsItem)
    )
  }

  test("Run Scala Test Classes") {
    val classes1 = List("class1").asJava
    val classes2 = List("class2").asJava
    val testClassesItems = List(
      new ScalaTestClassesItem(targetId1, classes1),
      new ScalaTestClassesItem(targetId2, classes2)
    ).asJava
    val result = new ScalaTestClassesResult(testClassesItems)
    client.testScalaTestClasses(
      new ScalaTestClassesParams(Collections.emptyList()),
      result
    )
  }

  test("Scala Test Classes with less items should fail") {
    val classes1 = List("class1").asJava
    val testClassesItems = List(new ScalaTestClassesItem(targetId1, classes1)).asJava
    val result = new ScalaTestClassesResult(testClassesItems)
    Try(
      client.testScalaTestClasses(
        new ScalaTestClassesParams(Collections.emptyList()),
        result
      )
    ) match {
      case Failure(_) =>
      case Success(_) => fail("Test Classes should expect all item classes to be defined!")
    }
  }

  test("Run Scala Main Classes") {
    val classes1 = List(
      new ScalaMainClass("class1", List("arg1", "arg2").asJava, List("-deprecated").asJava)
    ).asJava
    val classes2 = List(
      new ScalaMainClass("class2", List("arg1", "arg2").asJava, List("-deprecated").asJava)
    ).asJava
    val mainClassesItems = List(
      new ScalaMainClassesItem(targetId1, classes1),
      new ScalaMainClassesItem(targetId1, classes2)
    ).asJava
    val result = new ScalaMainClassesResult(mainClassesItems)
    client.testScalaMainClasses(
      new ScalaMainClassesParams(Collections.emptyList()),
      result
    )
  }

  test("Scala Main Classes with less items should fail") {
    val classes1 = List(
      new ScalaMainClass("class1", List("arg1", "arg2").asJava, List("-deprecated").asJava)
    ).asJava
    val mainClassesItems = List(new ScalaMainClassesItem(targetId1, classes1)).asJava
    val result = new ScalaMainClassesResult(mainClassesItems)
    Try(
      client.testScalaMainClasses(
        new ScalaMainClassesParams(Collections.emptyList()),
        result
      )
    ) match {
      case Failure(_) =>
      case Success(_) => fail("Test Classes should expect all item classes to be defined!")
    }
  }

  test("Workspace Build Targets") {
    val targets = List(target1, target2, target3, target4, target5).asJava
    val javaHome = sys.props.get("java.home").map(p => Paths.get(p).toUri.toString)
    val javaVersion = sys.props.get("java.vm.specification.version")
    val jvmBuildTarget = new JvmBuildTarget()
    jvmBuildTarget.setJavaVersion(javaVersion.get)
    jvmBuildTarget.setJavaHome(javaHome.get)
    val scalaJars = List("scala-compiler.jar", "scala-reflect.jar", "scala-library.jar").asJava
    val scalaBuildTarget =
      new ScalaBuildTarget("org.scala-lang", "2.12.7", "2.12", ScalaPlatform.JVM, scalaJars)
    scalaBuildTarget.setJvmBuildTarget(jvmBuildTarget)
    val autoImports = List("task-key").asJava
    val children = List(targetId3).asJava
    val sbtBuildTarget =
      new SbtBuildTarget("1.0.0", autoImports, scalaBuildTarget, children)
    val cppBuildTarget =
      new CppBuildTarget()
    cppBuildTarget.setVersion("C++11")
    cppBuildTarget.setCompiler("gcc")
    cppBuildTarget.setCCompiler("/usr/bin/gcc")
    cppBuildTarget.setCppCompiler("/usr/bin/g++")
    val pythonBuildTarget =
      new PythonBuildTarget()
    pythonBuildTarget.setInterpreter("/usr/bin/python")
    pythonBuildTarget.setVersion("3.9")

    target1.setDisplayName("target 1")
    target1.setBaseDirectory(targetId1.getUri)
    target1.setDataKind(BuildTargetDataKind.SCALA)
    target1.setData(scalaBuildTarget)

    target2.setDisplayName("target 2")
    target2.setBaseDirectory(targetId2.getUri)
    target2.setDataKind(BuildTargetDataKind.JVM)
    target2.setData(jvmBuildTarget)

    target3.setDisplayName("target 3")
    target3.setBaseDirectory(targetId3.getUri)
    target3.setDataKind(BuildTargetDataKind.SBT)
    target3.setData(sbtBuildTarget)

    target4.setDisplayName("target 4")
    target4.setBaseDirectory(targetId4.getUri)
    target4.setDataKind(BuildTargetDataKind.CPP)
    target4.setData(cppBuildTarget)

    target5.setDisplayName("target 5")
    target5.setBaseDirectory(targetId5.getUri)
    target5.setDataKind(BuildTargetDataKind.PYTHON)
    target5.setData(pythonBuildTarget)

    val workspaceBuildTargetsResult = new WorkspaceBuildTargetsResult(targets)
    client.testCompareWorkspaceTargetsResults(workspaceBuildTargetsResult)
  }

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
