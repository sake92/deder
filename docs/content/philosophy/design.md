---
title: Deder Design
---

# Deder Design


## File watching
Deder instantiates a file watcher in the project root.

If `deder.pkl` changes, the config is reloaded automatically, no need to do anything.  

If any source files change, the affected watched tasks are re-executed.  
E.g. if you do `deder -t compile --watch`, then the `compile` task is re-executed whenever source files change.


## Modules graph
A graph of modules is formed from the config.
E.g `A` depends on `B` etc.  
Of course, it has to be a DAG, no cycles are allowed.


## Tasks graph
Every module type has a *set of tasks* defined for it.  
These tasks can depend on each other, forming another DAG (e.g. `run` depends on `compile`).

Tasks can be *transitive*, meaning they invoke depending module tasks *with same name* transitively.  
An example of a transitive task is the `compile` task.  
If you say `deder -t compile -m A` where A depends on B, then B must be compiled before it.


## Plan graph and execution stages
Say we have 2 tasks: `compile` and `run`.  
When we invoke `deder -t run -m A` we need to execute `A.compile` and then `A.run`.

So we need a *subgraph of tasks*.  
Additionally, we must not execute the same task more than once, *minimality*.  

How can we build this subgraph?
We start from `A.run`, and work our way backwards, collecting all depending tasks *recursively*.

Then we go the other way, starting from the tasks with no dependencies, and build "stages" of tasks.  
At the end we will get a `List[List[Task]]`, where the outer `List` is a list of "stages".  
The first stage contains a list of tasks which have *no dependencies*.  
The second stage tasks depend on some tasks from first stage, and so on.


## Plan execution

We execute the first stage and collect the tasks results.  
Then we *feed those results to the next stage* and so on, until we reach our given goal task.

> Notice that each stage can leverage concurrent execution, since the task in one stage are independent.

We need to be careful about error handling, we usually want to exit early and print a nice error message.

## Concurrent execution and locking

A "task-instance" is a task on a specific module, e.g. `A.compile` or `B.run`.

Locking is done at task-instance level before starting execution.  
All plan subgraph task-instances are locked first, and only then the execution starts.

The task-instances are *sorted by their ids*, so that **global ordering** is achieved.
This makes sure there are **no deadlocks**.



## Caching / minimality
- if inputs hash hasnt changed, dont do it
- Blake3 algo for file hashing
- use relative paths as much as possible


## BSP
- one server per BSP client (intellij/metals)
