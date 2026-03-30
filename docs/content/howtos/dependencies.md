---
layout: howto.html
title: Dependencies
---

# {{page.title}}

Dependencies are written using Coursier's Dependency syntax.  
See https://github.com/coursier/dependency for more details.

## How to add a Java library dependency?

Similar to gradle syntax, you use colons to separate `groupId`, `artifactId` and `version`:
```
deps {
  "org.immutables:value-annotations:2.12.0"
}
```


## How to add a Scala library dependency?

Here you need to use double-colon syntax for first separator:
```
deps {
  "ba.sake::sharaf-undertow:0.17.0"
}
```

If this is a scala 3 module, then it will resolve to `"ba.sake:sharaf-undertow_3:0.17.0"`.  
Notice how it adds the scala binary version suffix `_3`, and goes back to single-colon.  

> This can be used if you want to add a scala 2 library to a scala 3 project!
> E.g. `"com.example:my_scala2_lib_2.13:0.0.1"`


## How to add a ScalaJS/ScalaNative library dependency?

Here you need to use double-colon syntax in BOTH places:
```
deps {
  "org.scala-js::scalajs-dom::2.2.0"
}
```

Besides scala binary version suffix `_3`, it will also prepend the *platform suffix*,
it will resolve to `"org.scala-js:scalajs-dom_sjs1_3:2.2.0"`.  
Notice the `_sjs1` suffix, this means it is a ScalaJS version 1 dependency.

> Similar is done for ScalaNative but the suffix is something like `_native0.5`


## How to add a Scalac plugin dependency?


Here you need to use triple-colon syntax as first separator:
```
scalacPluginDeps {
  "org.wartremover:::wartremover:3.5.0"
}
```


If this is a scala `3.7.2` module, then it will resolve to `"org.wartremover:wartremover_3.7.2:3.5.0"`.  

Notice how it adds the *full scala version* suffix `_3.7.2`, not just the binary version.  
This is because compiler plugins depend on compiler internals, so they have to use a specific version.

