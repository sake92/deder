# Getting Started with Deder

## What is Deder?

Deder is a config-based, concurrent-first client-server build tool designed for JVM projects (Java and Scala).  
It uses [Pkl](https://pkl-lang.org/) for configuration, providing type safety, IDE support, and powerful abstraction capabilities.

## Key Concepts

### Projects
A **project** is the root of your git repository where your build configuration lives.

### Modules
**Modules** are subprojects within your repository (like `common`, `frontend`, `backend`, etc.). 
They can depend on each other, forming a directed acyclic graph (DAG).

### Tasks
**Tasks** are operations that can be performed on modules (like `compile`, `run`, `test`). 
Each module type has a predefined set of tasks available.

## Installation

1. Download `deder-client.jar` from the [early release](https://github.com/sake92/deder/releases/tag/early-access)
2. Rename it to just `deder`:
```bash
mv deder-client.jar deder
```
3. Make it executable:
```bash
chmod +x deder
```
4. Put it in your `PATH`:
```bash
sudo mv deder /usr/local/bin/
```

## Your First Build

### 1. Create a Configuration File

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

### 2. Run Your First Command

```bash
# Compile all modules
deder exec -t compile

# Or just use the default (which is compile)
deder exec
```

### 3. Explore Your Build

```bash
# List all modules
deder modules

# List all available tasks
deder tasks

# See the execution plan for a specific task
deder plan -m my-module -t compile
```

## Basic Commands

### Build Commands
```bash
# Compile all modules (default)
deder exec

# Compile specific module
deder exec -t compile -m my-module

# Run a module
deder exec -t run -m my-module

# Watch mode - recompile on changes
deder exec -t compile -m my-module --watch

# Run tests
deder exec -t test -m my-test-module
```

### Misc
```bash
# Print version
deder version

# Shutdown the build server
deder shutdown
```
