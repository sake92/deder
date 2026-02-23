---
title: Quickstart Guide
description: Deder Quickstart Guide
---

# Quickstart Guide

## Create a Build File

Create a `deder.pkl` file in your project root:

```pkl
amends "https://sake92.github.io/deder/config/DederProject.pkl"

local const myModules = new CreateScalaModules {
  _root = "my-module"
  _template = new {
    scalaVersion = "3.7.4"
    mainClass = "mymodule.Main"
  }
  _testTemplate = (_template.asTest()) {
    deps {
      "org.scalameta::munit:1.2.1"
    }
  }
}.get

modules {
  ...myModules
}
```

## Run Your First Task

```bash
# Compile all modules
deder exec -t compile

# default task is compile, no need to specify it
deder exec
```

## Explore Your Build

```bash
# List all modules
deder modules

# List all available tasks
deder tasks

# See the execution plan for a specific task
deder plan -m my-module -t compile
```

## Run More Tasks

```bash
# Run the main class of my-module
deder exec -t run -m my-module

# Watch mode - recompile on changes
deder exec -t compile -m my-module --watch

# Run tests
deder exec -t test -m my-test-module
```