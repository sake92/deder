---
layout: reference.html
title: Deder Architecture
---

# Deder Architecture


## General design
- [PKL](https://pkl-lang.org/) for defining a build
- client-server architecture:
    - CLI client that talks JSON-RPC via Unix socket
    - BSP client via BSP protocol

![](/images/deder_architecture.svg)

## BSP server diagnostics

The BSP server publishes compile diagnostics to the IDE as a complete, per-module picture:

- After every compile (live or cache hit), every **current** source file of the module is
  published with `reset=true` — clean files get an empty list, so stale IDE markers are cleared.
- Files that **left** the module (renamed/deleted) receive an explicit empty `reset=true`
  publish, so the client drops their old diagnostics. Without this, an IDE keeps showing
  errors under the old file name after a rename.
- `sourceFiles`/`resources` result hashes bind file **paths** to content hashes, so a rename
  with unchanged content still invalidates the `compile` cache and triggers a recompile with
  the new source set.
- Zinc diagnostics are filtered to the module's current source set, so problems left over in
  Zinc's analysis for removed files are never re-published.


