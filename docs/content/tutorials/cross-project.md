---
title: Cross Project Tutorial
description: Deder Cross Project Tutorial
---

# Cross Project Tutorial

Sometimes you want to create multiple modules based on:
- platforms (e.g., JVM, JS, Native)
- scala versions (e.g., 2.13, 3.0)
- or both

Since Deder builds are "just config", you can easily create multiple modules.  
Create a `deder.pkl` file in your project root:

```ts
amends "https://sake92.github.io/deder/config/DederProject.pkl"

local const scalaVersions = List("2.13.18", "3.7.4")

local const commonModules: List<ScalaModule> =
  scalaVersions
    .map((sv) ->
        new CreateCrossModules {
          _root = "common"
          _template = new {
            scalaVersion = sv
          }
          _jsTemplate = (_template.asJs()) {
            scalaJsVersion = "1.20.2"
          }
          _nativeTemplate = (_template.asNative()) {
            scalaNativeVersion = "0.5.10"
          }
        }
      .get
      .all
    )
    .flatten()

modules {
  ...commonModules
}
```

This might seem a bit verbose, but it is very flexible.  
The `CreateCrossModules` helper creates 3 modules for each scala version: one for JVM, one for JS, and one for Native.  
Then the `scalaVersions` list specifies which scala versions you want to support.
We loop through the `scalaVersions` list and create modules for each version, and then flatten the result to get a single list of modules.

Finally, we add all the created modules to the `modules` section:
```pkl
modules {
  ...commonModules
}
```

List all modules:
```shell
$ deder modules
common-js-2.13.18
common-js-3.7.4
common-jvm-2.13.18
common-jvm-3.7.4
common-jvm-test-2.13.18
common-jvm-test-3.7.4
common-native-2.13.18
common-native-3.7.4
```


---

You can even filter the `commonModules` list, if you want to remove some modules.  
For example, if Scala Native is not ready for Scala 3.7.4, you can filter it out like this:
```pkl
modules {
  ...commonModules.filter((m) -> !m.id.endsWith("-native-3.7.4"))
}
```


