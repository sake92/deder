---
layout: reference.html
title: Files layout
---

# Files layout

The `.deder` directory is created in the project root when you run `deder` for the first time.  
It generally has a flat structure, each module gets its own subdirectory in `out` folder.

```shell
.deder/
├── logs
│   ├── client
│   │   ├── cli-client_2026-02-27-12-54-32_186830.log
│   └── server.log
├── out
│   ├── mylibrary     # module name
│   │   ├── assembly
│   │    ....         # assembly task files
│   │   ├── classes
│   │   │   └── ba
│   │   │   └── ...   # class files etc.
├── server.jar        # the server JAR file
├── test-runner.jar   # the test-runner JAR file
├── server.properties # server config file, can be edited and commited to git
├── server.lock       # server lock file, only one is allowed to run at a time
├── server-cli.sock   # unix domain socket for CLI clients
├── server-bsp.sock   # unix domain socket for BSP clients
└── server.current.version  # cached version tag for this project

```

## Global Cache

Deder maintains a global artifact cache to avoid redundant downloads across multiple projects.
The cache is stored in your home directory:

```shell
~/.deder/
└── cache/
    └── artifacts/
        ├── v0.5.1/
        │   ├── deder-server.jar
        │   ├── deder-server.jar.sha256
        │   ├── deder-test-runner.jar
        │   └── deder-test-runner.jar.sha256
        ├── v0.5.0/
        │   ├── deder-server.jar
        │   ├── deder-server.jar.sha256
        │   ├── deder-test-runner.jar
        │   └── deder-test-runner.jar.sha256
        └── cache.lock    # lock file for concurrent access
```

Key features:
- **Shared across all projects** - Multiple projects use the same cached artifacts
- **Checksum verified** - Each download is verified for integrity
- **All versions retained** - Old versions are kept for fast switching
- **Concurrent-safe** - Multiple deder processes can access safely

To clear the global cache:
```shell
rm -rf ~/.deder/cache/artifacts/
```

Note: `early-access` version and local builds (via `localPath` property) are never cached.

