---
title: Files layout
---

# Files layout

The `.deder` directory is created in the project root when you run `deder` for the first time.  
It generally has a flat structure, each module gets its own subdirectory in `out` folder.

```shell
.deder/
├── logs
│   ├── client
│   │   ├── cli-client_2026-02-27-12-54-32_186830.log
│   └── server.log
├── out
│   ├── mylibrary     # module name
│   │   ├── assembly
│   │    ....         # assembly task files
│   │   ├── classes
│   │   │   └── ba
│   │   │   └── ...   # class files etc.
├── server.jar        # the server JAR file
├── server.properties # server config file, can be edited and commited to git
├── server.lock       # server lock file, only one is allowed to run at a time
├── server-cli.sock   # unix domain socket for CLI clients
└── server-bsp.sock   # unix domain socket for BSP clients

```

