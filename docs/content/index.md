---
title: Deder Docs
description: Deder Build Tool Docs
pagination:
  enabled: false
---

# Deder

Deder is a config-based, concurrent-first, client-server build tool for JVM projects (Java and Scala).  
It uses [Pkl](https://pkl-lang.org/) for configuration, providing type safety, IDE support, and powerful abstraction capabilities.

## Key Concepts

**Project** is the root directory where your build configuration lives (usually the git repo root).

**Modules** are subprojects within your project (like `common`, `frontend`, `backend`, etc.).
They can depend on each other, forming a directed acyclic graph (DAG).

**Tasks** are operations that can be performed on modules (like `compile`, `run`, `test`).
Each module type has a predefined set of tasks available.  
They also form a directed acyclic graph (DAG).

## Site Map
- [Tutorials](/tutorials)
  {% for tut in site.data.project.tutorials %}- [{{ tut.label }}]({{ tut.url}})
  {% endfor %}
- [How Tos](/howtos)
    {% for tut in site.data.project.howtos %}- [{{ tut.label }}]({{ tut.url}})
    {% endfor %}
- [Reference](/reference)
  {% for tut in site.data.project.references %}- [{{ tut.label }}]({{ tut.url}})
  {% endfor %}
- [Philosophy](/philosophy)
  {% for tut in site.data.project.philosophies %}- [{{ tut.label }}]({{ tut.url}})
  {% endfor %}


## IDE setup

Run `deder bsp install` and then open with VSCode or IntelliJ (open as a BSP project).  


## Tips & Tricks

Generate an SVG of the dependency graph for a specific task:
```shell
deder plan -m common -t compileClasspath --dot | dot -Tsvg > bla.svg
```



