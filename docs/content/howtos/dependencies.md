---
layout: howto.html
title: Dependencies
---

# {{page.title}}

Dependencies are written using Coursier's Dependency syntax.
See https://github.com/coursier/dependency for more details.

## How to add a Java library dependency?

Similar to Gradle syntax, you use colons to separate `groupId`, `artifactId`, and `version`:
```pkl
deps {
  "org.immutables:value-annotations:2.12.0"
}
```


## How to add a Scala library dependency?

Here you need to use double-colon syntax for first separator:
```pkl
deps {
  "ba.sake::sharaf-undertow:0.17.0"
}
```

If this is a Scala 3 module, then it resolves to `"ba.sake:sharaf-undertow_3:0.17.0"`.
Notice how it adds the Scala binary version suffix `_3`, and goes back to single-colon.

> This can be used if you want to add a Scala 2 library to a Scala 3 project.
> E.g. `"com.example:my_scala2_lib_2.13:0.0.1"`


## How to add a Scala.js library dependency?

For Scala.js modules, use double-colon syntax in BOTH places:
```pkl
deps {
  "org.scala-js::scalajs-dom::2.2.0"
}
```

Besides the Scala binary version suffix `_3`, it will also prepend the *platform suffix*,
it will resolve to `"org.scala-js:scalajs-dom_sjs1_3:2.2.0"`.
Notice the `_sjs1` suffix, which means it is a Scala.js 1 dependency.

## How to add a Scala Native library dependency?

Scala Native also uses double-colon syntax in both places:

```pkl
deps {
  "com.armanbilge::epollcat::0.1.4"
}
```

It resolves with a native platform suffix (for example `_native0.5`).


## How to add a Scalac plugin dependency?


Here you need to use triple-colon syntax as first separator:
```pkl
scalacPluginDeps {
  "org.wartremover:::wartremover:3.5.0"
}
```


If this is a Scala `3.7.2` module, then it resolves to `"org.wartremover:wartremover_3.7.2:3.5.0"`.

Notice how it adds the *full Scala version* suffix `_3.7.2`, not just the binary version.
This is because compiler plugins depend on compiler internals, so they have to use a specific version.

## Advanced declarations

Deder passes dependency strings to Coursier's parser (`DependencyParser.parse`), so you can use advanced declarations like:

```pkl
deps {
  // Exclude a transitive dependency:
  "org.other:lib:2.0,exclude=org.example%%transitive-dep"

  // Override artifact download URL (direct file URL):
  "com.lihaoyi:os-lib_3:0.11.6,url=https://repo1.maven.org/maven2/com/lihaoyi/os-lib_3/0.11.6/os-lib_3-0.11.6.jar"

  // Keep only the direct dependency (no transitives):
  "com.lihaoyi:os-lib_3:0.11.6,intransitive"
}
```

## Using dependencies from `publishLocal`

`deder exec -t publishLocal` publishes to `~/.m2/repository` by default (or to `publishLocalTo` if configured).
Deder checks the local Maven repo by default, so you can depend on the published artifact immediately.

- See [Publishing Tutorial](/tutorials/publishing.html#publish-to-local-maven-repository)
- See [Custom Repositories](/reference/repositories.html#resolution-order)
