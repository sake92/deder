
## General design
- [PKL](https://pkl-lang.org/) for defining a build
- client-server architecture:
  - CLI client that talks JSON-RPC via Unix socket 
  - BSP client via BSP protocol
  - web client??

## Build Server

- one for the whole project
- concurrency is handled by locking on task level

### Concurrency

- flatten the tasks graph
- force order of taking the locks
- if module A depends on B, then it has to lock B too !!!
    - because if it needs to B.compile for example..
    - first take ALL NEEDDED LOCKS!? only THEN do the tasks..
- use lockInterruptibly()
- try interrupt() if deadlock occurs, maybe


----

## CLI

https://clig.dev/
- nice stuff about output, Disable color !!!
- interactivity
- --json support
- autocomplete

https://medium.com/@jdxcode/12-factor-cli-apps-dd3c227a0e46

### CLI Client
- The client process runs interactive subprocesses
- same as if you run `mvn exec ..`
- doesnt overload the server memory
- no terminal piping/teeing/whatever is needed
- process signals work normally

- install globally for now (or forever)
- wrapper scripts are not *that useful*, just more indirection to trip us...
- maintain client backcompat as much as possible
- throw if minimum client version is not satisfied or something



## Caching / minimality
- if inputs hash hasnt changed, dont do it
- Blake3 algo for file hashing


---

## BSP client
- one server per client (intellij/metals)
