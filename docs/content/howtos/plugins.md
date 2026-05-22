---
layout: howto.html
title: Plugins
---

# {{page.title}}

> **Plugin support is still evolving.** The API surface and configuration schema may change between releases.
> Watch release notes for breaking changes.

Plugins let you add custom tasks to Deder modules.

Each plugin is a JVM library that the server loads at startup via the Java ServiceLoader mechanism.  
And it reloads them as soon as you change `deder.pkl` build, no need for restarting the server manually!

Check out some official plugins in [deder-plugins repo](https://github.com/sake92/deder-plugins).

## Core moving parts

| Component | Role |
|-----------|------|
| `plugin-api/` | The public API your plugin depends on. Exposes `DederPluginApi`, `TaskBuilder`, `AbstractTask`, `CoreTasksApi`, and `PluginConfigEvaluators`. |
| `config/DederProject.pkl` | The base Pkl schema. If your plugin needs typed config, extend `DederPlugin` in your own `.pkl` file. |
| Generated config bindings | Java classes generated from your plugin's Pkl schema via `pkl-codegen-java`. Regenerate after any schema change. |

## Implementing `DederPluginApi`

```scala
import ba.sake.deder.*

class MyPluginImpl extends DederPluginApi:
  def id: String = "my-plugin"

  def tasks(params: PluginTasksParams): Either[String, Seq[AbstractTask[?]]] =
    val myTask = TaskBuilder
      .make[String]("myTask") // T must have JsonRW and Hashable instances
      .build { ctx =>
        // do something, return a result
        "done"
      }
    Right(Seq(myTask))
```

Register the implementation via `META-INF/services/ba.sake.deder.DederPluginApi` (standard ServiceLoader).

If your plugin owns resources (thread pools, connections, etc.), override `onClose()` to release them when the server shuts down.

## Typed plugin config

### 1. Define the Pkl schema (bundled in the plugin JAR)

```pkl
// MyPluginModule.pkl
module com.example.MyPluginModule

import "https://sake92.github.io/deder/config/early-access/DederProject.pkl" as P

class MyPlugin extends P.DederPlugin {
  id = "my-plugin"
  deps { "com.example::my-deder-plugin:1.0.0" }
  config: MyPluginConfig = new {}
}

class MyPluginConfig {
  greeting: String = "Hello"
}

// used for parsing on the plugin side
config: MyPluginConfig = new {}
```

### 2. Generate Java bindings

After writing or modifying the `.pkl` file, regenerate the Java config classes using `pkl-codegen-java`:

```shell
# In your own plugin repository — use pkl-codegen-java directly:
pkl-codegen-java \
  --output-dir my-plugin/src \
  my-plugin/resources/MyPluginModule.pkl
```

If you are developing inside a Deder checkout, the convenience wrapper does the same thing:

```shell
./scripts/gen-plugin-bindings.sh my-plugin
```

This reads `my-plugin/resources/*.pkl`, writes generated Java sources into `my-plugin/src/`, and refreshes any generated or bundled resources under `my-plugin/resources/`.

### 3. Read config in `tasks` function

```scala
import ba.sake.deder.*

class MyPluginImpl extends DederPluginApi:
  def id: String = "my-plugin"

  def tasks(params: PluginTasksParams): Either[String, Seq[AbstractTask[?]]] =
    val pluginModule = PluginConfigEvaluators.evaluate(
      getClass.getClassLoader,
      modulePath = "MyPluginModule.pkl",
      configText = params.configText,
      clazz = classOf[MyPluginModule]
    )
    val greeting = pluginModule.config.greeting

    val myTask = TaskBuilder
      .make[String]("myTask")
      .build { ctx =>
        ctx.notifications.add(ServerNotification.logInfo(greeting))
        greeting
      }
    Right(Seq(myTask))
```

### 4. Use typed config in a `deder.pkl` (consumer project)

Import your plugin's Pkl module and instantiate the typed plugin class directly:

```pkl
amends "https://sake92.github.io/deder/config/early-access/DederProject.pkl"

import "https://example.github.io/myuser/my-deder-plugin/MyPluginModule.pkl" as MPM

plugins {
  new MPM.MyPlugin {
    config = new {
      greeting = "Hello from typed config!"
    }
  }
}
```

## Local development workflow

1. **If you changed the Pkl schema** — regenerate Java config bindings.
   In your own plugin repository use `pkl-codegen-java` directly (see [§ Generate Java bindings](#2-generate-java-bindings) above).
   Inside a Deder checkout the wrapper also works:
   ```shell
   ./scripts/gen-plugin-bindings.sh my-plugin
   ```
2. **Build your plugin** and publish it to your local Maven repository.
3. **Point your test project** at the local version via `deps` in `MyPluginModule.pkl`.
