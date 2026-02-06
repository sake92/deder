
# Quickstart Guide

## Create a Configuration File

Create a `deder.pkl` file in your project root:

```pkl
amends "https://sake92.github.io/deder/config/DederProject.pkl"

local const myModule = new ScalaModule {
  id = "my-module"
  scalaVersion = "3.7.4"
  deps {
    "com.lihaoyi::pprint:0.9.5"
  }
}

modules {
  myModule
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

## Run Some More Tasks

```bash
# Run the main class of my-module
deder exec -t run -m my-module

# Watch mode - recompile on changes
deder exec -t compile -m my-module --watch

# Run tests
deder exec -t test -m my-test-module
```