---
layout: reference.html
title: Project layout
---

# Project layout

Deder supports several standard directory layouts for organizing source files.
The layout is controlled by the `layout` parameter on `CreateScalaModules`, `CreateJavaModules`, or `CreateCrossModules`.

## Quick reference

| Layout             | Main Scala source        | Test Scala source        | Resources              | Test module root |
|--------------------|--------------------------|--------------------------|------------------------|------------------|
| `default`          | `{root}/src`             | `{root}/test/src`        | `{root}/resources`      | `{root}/test`    |
| `maven`            | `{root}/src/main/scala`  | `{root}/src/test/scala`  | `{root}/src/main/resources` | `{root}`    |
| `sbt`              | `{root}/src/main/scala`  | `{root}/src/test/scala`  | `{root}/src/main/resources` | `{root}`    |
| `sbt-cross-full`   | shared + `jvm/.../scala` | shared + `jvm/test/...`  | `{root}/shared/src/main/resources` | `{root}` |
| `sbt-cross-pure`   | shared + `.jvm/.../scala`| shared + `.jvm/test/...` | `{root}/src/main/resources`         | `{root}` |
| `sbt-cross-dummy`  | `jvm/src/main/scala`     | `jvm/src/test/scala`     | `{root}/src/main/resources`         | `{root}` |

For Java modules, replace `scala` with `java` and note that `maven`/`sbt` layouts do **not** add `src/main/scala` as an extra source.

## Flat-style (`default`)

The default layout uses a flat directory structure — main sources go directly under `src/` at the module root, and the test module lives in a `test/` subdirectory.

```
my-module/
├── src/
│   └── mymodule/Main.scala
├── resources/
│   └── application.conf
└── test/
    ├── src/
    │   └── mymodule/MainTest.scala
    └── resources/
        └── application.conf
```

```pkl
local const myModules = new CreateScalaModules {
  root = "my-module"
  layout = "default"  // this is the default, can be omitted
  template = new { ... }
}
```

Key conventions:

- Main sources: `{root}/src/`
- Main resources: `{root}/resources/`
- Test module root: `{root}/test/` (suffix `/test` is appended automatically)
- Test sources: `{root}/test/src/`
- Test resources: `{root}/test/resources/`

## Maven / SBT style (`maven`, `sbt`)

Both layouts follow the `src/main/...` and `src/test/...` convention used by Maven, Gradle, and SBT.
The directory structure is identical; the difference is whether version-specific source directories are added (see [below](#version-specific-source-directories)).

```
my-module/
├── src/
│   ├── main/
│   │   ├── scala/
│   │   │   └── mymodule/Main.scala
│   │   ├── java/                         ← only for Scala modules
│   │   │   └── mymodule/Helper.java
│   │   └── resources/
│   │       └── application.conf
│   └── test/
│       ├── scala/
│       │   └── mymodule/MainTest.scala
│       ├── java/                         ← only for Scala modules
│       │   └── mymodule/HelperTest.java
│       └── resources/
│           └── application.conf
```

```pkl
// Scala module — also picks up src/main/java as an extra source
local const myModules = new CreateScalaModules {
  root = "my-module"
  layout = "maven"  // or "sbt" for SBT-style version dirs
  template = new { ... }
}
```

Key differences from `default`:

- Main and test modules share the same root (no `/test` suffix on the test module)
- For Scala modules, `src/main/java` is automatically added as an extra source directory (Java modules only get `src/main/java`)
- Resources live under `src/main/resources` and `src/test/resources`

## Version-specific source directories

Scala modules can use version-specific source directories to share code across Scala versions.
Deder has two naming styles, controlled by the layout's `versionStyle`.

> **Note:** Version-specific directories are only added for Scala-family modules (Scala, Scala.js, Scala Native). Java modules do not get version-specific source dirs.

### Deder style (`versionStyle = "deder"`)

Used by `default` and `maven` layouts. For Scala version `3.7.4`:

```
src-3/           # major version
src-3.7/         # minor version
src-3.7.4/       # patch version
```

Example with `maven` layout:

```
my-module/
└── src/
    └── main/
        └── scala/
            ├── scala-3/                  ← version-specific (deder style)
            ├── scala-3.7/
            └── scala-3.7.4/
```

### SBT style (`versionStyle = "sbt"`)

Used by all `sbt*` layouts. For Scala version `3.7.4`:

```
src/main/scala-3/
src/main/scala-3.7/
src/main/scala-3.7.4/
src/main/scala-3.7+/      # binary version and higher
src/main/scala-2.13+/     # cross-compilation (always added for Scala 3)
```

The SBT style adds two extra "binary version" directories:

- `X.Y+` — matches the current binary version and any higher
- `2.13+` — for Scala 3.x targets, this enables sharing code with `2.13` cross-builds

## Cross-platform layouts

For projects targeting multiple platforms (JVM, JS, Native), `CreateCrossModules` supports three layout styles via the `layout` parameter.

### `sbt-cross-full`

Shared sources go under a `shared/` prefix, platform-specific sources under `jvm/`, `js/`, `native/`:

```
my-module/
├── shared/
│   ├── src/
│   │   ├── main/
│   │   │   ├── scala/                   ← shared across all platforms
│   │   │   ├── scala-3/                 ← version-specific (sbt style)
│   │   │   └── scala-3.7/
│   │   └── test/
│   │       └── scala/                   ← shared test sources
│   └── resources/                       ← shared resources
├── jvm/
│   └── src/
│       ├── main/
│       │   └── scala/                   ← JVM-specific
│       └── test/
│           └── scala/                   ← JVM-specific test
├── js/
│   └── src/
│       └── main/
│           └── scala/                   ← JS-specific
└── native/
    └── src/
        └── main/
            └── scala/                   ← Native-specific
```

### `sbt-cross-pure`

Like `full`, but shared sources stay at the normal location (no `shared/` prefix).
Platform sources go under dot-prefixed hidden directories:

```
my-module/
├── src/
│   ├── main/
│   │   ├── scala/                       ← shared (no prefix)
│   │   ├── scala-3/                     ← version-specific (sbt style)
│   │   └── resources/
│   └── test/
│       └── scala/                       ← shared test sources
├── .jvm/
│   └── src/
│       └── main/
│           └── scala/                   ← JVM-specific
├── .js/
│   └── src/
│       └── main/
│           └── scala/                   ← JS-specific
└── .native/
    └── src/
        └── main/
            └── scala/                   ← Native-specific
```

### `sbt-cross-dummy`

No shared sources at all. Each platform carries its own complete source tree:

```
my-module/
├── jvm/
│   └── src/
│       ├── main/
│       │   └── scala/                   ← JVM only
│       └── test/
│           └── scala/
├── js/
│   └── src/
│       └── main/
│           └── scala/                   ← JS only
└── native/
    └── src/
        └── main/
            └── scala/                   ← Native only
```

## Configuration

Set the `layout` parameter on the creation helper class:

```pkl
// Flat-style (default)
new CreateScalaModules {
  root = "myapp"
  layout = "default"
  template = new { ... }
}

// Maven-style
new CreateScalaModules {
  root = "myapp"
  layout = "maven"
  template = new { ... }
}

// SBT-style with version-specific dirs and 2.13+ cross-build support
new CreateScalaModules {
  root = "myapp"
  layout = "sbt"
  template = new { ... }
}

// Cross-platform with full shared layout
new CreateCrossModules {
  root = "myapp"
  layout = "sbt-cross-full"
  template = new { ... }
}
```

The `layout` parameter is optional and defaults to `"default"`.

For fine-grained control, you can also define modules manually (without the creation helpers) and specify `sources` and `resources` explicitly:

```pkl
modules {
  new JavaModule {
    id = "mylib"
    root = "mylib"
    sources { "src/main/java"; "src/main/generated" }
    resources { "src/main/resources" }
    type = "java"
  }
}
```
