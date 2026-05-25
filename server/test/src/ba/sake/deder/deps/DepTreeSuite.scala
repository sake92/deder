package ba.sake.deder.deps

import munit.FunSuite
import ba.sake.deder.PlainTextWritable
import ba.sake.tupson.toJson

class DepTreeSuite extends FunSuite {

  test("PlainTextWritable[DepTree] formats single direct dependency") {
    val node = DepNode(
      org = "org.example",
      name = "mylib",
      version = "1.0.0",
      filePath = "/tmp/mylib.jar",
      fileSizeBytes = 1024 * 100,
      depth = 0,
      parents = Seq.empty
    )
    
    val tree = DepTree(
      module = "test-module",
      allDeps = Seq(node),
      rootDeps = Seq(node),
      conflicts = Seq.empty,
      totalSizeBytes = 1024 * 100,
      totalUniqueSizeBytes = 1024 * 100
    )
    
    val output = summon[PlainTextWritable[DepTree]].write(tree)
    assert(output.contains("org.example:mylib:1.0.0"))
    assert(output.contains("100KB"))
    assert(output.contains("Total size: 100KB"))
  }

  test("PlainTextWritable[DepTree] renders transitive deps with tree connectors") {
    val node1 = DepNode(
      org = "org.b",
      name = "lib-b",
      version = "2.0.0",
      filePath = "/tmp/b.jar",
      fileSizeBytes = 2048,
      depth = 0,
      parents = Seq.empty
    )
    
    val node2 = DepNode(
      org = "org.a",
      name = "lib-a",
      version = "1.0.0",
      filePath = "/tmp/a.jar",
      fileSizeBytes = 1024,
      depth = 1,
      parents = Seq("org.b:lib-b:2.0.0")
    )
    
    val tree = DepTree(
      module = "test",
      allDeps = Seq(node1, node2),
      rootDeps = Seq(node1),
      conflicts = Seq.empty,
      totalSizeBytes = 3072,
      totalUniqueSizeBytes = 3072
    )
    
    val output = summon[PlainTextWritable[DepTree]].write(tree)
    // tree connectors should be present
    assert(output.contains("└──") || output.contains("├──"))
    assert(output.contains("org.a:lib-a:1.0.0"))
    assert(output.contains("org.b:lib-b:2.0.0"))
  }

  test("PlainTextWritable[DepTree] highlights version conflicts") {
    val conflict = DepConflict(
      coordinate = "org.junit:junit",
      requestedVersions = Map("4.13" -> Seq.empty, "4.12" -> Seq.empty),
      resolvedVersion = "4.13",
      isConflict = true
    )
    
    val node = DepNode(
      org = "org.junit",
      name = "junit",
      version = "4.13",
      filePath = "/tmp/junit.jar",
      fileSizeBytes = 500000,
      depth = 0,
      parents = Seq.empty
    )
    
    val tree = DepTree(
      module = "test",
      allDeps = Seq(node),
      rootDeps = Seq(node),
      conflicts = Seq(conflict),
      totalSizeBytes = 500000,
      totalUniqueSizeBytes = 500000
    )
    
    val output = summon[PlainTextWritable[DepTree]].write(tree)
    assert(output.contains("⚠️"))
    assert(output.contains("Version Conflicts"))
    assert(output.contains("4.13") && output.contains("4.12"))
  }

  test("JsonRW[DepTree].toJson produces valid JSON") {
    val node = DepNode(
      org = "org.example",
      name = "lib",
      version = "1.0.0",
      filePath = "/tmp/lib.jar",
      fileSizeBytes = 1024,
      depth = 0,
      parents = Seq.empty
    )
    
    val tree = DepTree(
      module = "test",
      allDeps = Seq(node),
      rootDeps = Seq(node),
      conflicts = Seq.empty,
      totalSizeBytes = 1024,
      totalUniqueSizeBytes = 1024
    )
    
    import ba.sake.tupson.JsonRW
    val json = summon[JsonRW[DepTree]].write(tree).toJson(spaces = 2, sort = true)
    assert(json.contains("module"))
    assert(json.contains("allDeps"))
    assert(!json.isBlank)
  }

  test("DepNode.coordinate formats correctly") {
    val node = DepNode(
      org = "org.example",
      name = "mylib",
      version = "1.2.3",
      filePath = "/tmp/test.jar",
      fileSizeBytes = 100,
      depth = 0,
      parents = Seq.empty
    )
    
    assertEquals(node.coordinate, "org.example:mylib:1.2.3")
  }

  test("DepTree.conflictCount counts conflicts") {
    val conflicts = Seq(
      DepConflict("org.a:lib", Map.empty, "1.0", isConflict = true),
      DepConflict("org.b:lib", Map.empty, "2.0", isConflict = false),
      DepConflict("org.c:lib", Map.empty, "3.0", isConflict = true)
    )
    
    val tree = DepTree(
      module = "test",
      allDeps = Seq.empty,
      rootDeps = Seq.empty,
      conflicts = conflicts,
      totalSizeBytes = 0,
      totalUniqueSizeBytes = 0
    )
    
    assertEquals(tree.conflictCount, 2)
  }
}
