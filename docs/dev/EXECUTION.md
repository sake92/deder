

## How does it work?

### Config parse
The config is parsed by server into an object.

### Modules graph
A graph of modules is formed from the config.
E.g A depends on B etc.  
Of course, it has to be a DAG, no cycles.

### Tasks graph
Every module type has a *set of tasks* defined for it.  
These tasks can depend on each other, forming another DAG (e.g. `run` depends on `compile`).

Tasks can also be *transitive*, meaning they invoke depending module tasks *with same name* transitively.  
A simple example of a transitive task is a `compile` task.  
If you say `deder compile -m A` where A depends on B, then B must be compiled before it.

In this stage, we have a complete DAG of tasks.

### Plan graph, stages
Say we have 2 tasks: `compile` and `run`.  
When we invoke `deder compile -m A` we need to execute `B.compile` and then `A.compile`.

So we need a *subgraph of tasks*.  
Additionaly, we must not execute the same task more than once (minimality).  

How can we build this subgraph?
We start from `A.compile`, and work our way backwards, collecting all depending tasks *recursively*.

At the end we will get a `List[List[Task]]`, where the outer `List` is a list of "stages".  
The first stage contains a list of tasks which have *no dependencies*.  
The second stage tasks depend on some tasks from first stage, and so on.


### Plan execution

We execute the first stage and collect the tasks results.  
Then we *feed those results to the next stage* and so on, until we reach our given goal task.

> Notice that each stage can leverage concurrent execution, since the task in one stage are independent.

We need to be careful about error handling, we usually want to exit early and print a nice error message.



