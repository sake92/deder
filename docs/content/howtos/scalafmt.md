---
layout: howto.html
title: Format Scala code with Scalafmt
---

# {{ page.title }}

Create `.scalafmt.conf` in the project root. For example, for Scala 3:

```hocon
version = "3.10.7"
runner.dialect = scala3
maxColumn = 120
```

Use the appropriate [dialect](https://scalameta.org/scalafmt/docs/configuration.html#scala-dialects) for your Scala version.
Deder provides the `fmt` and `fmtCheck` Maven applications for Scala modules; you do not need to declare them in `mvnApps`.

Format a module's configured source directories:

```shell
deder exec -t runMvnApp -m mymodule fmt
```

To check formatting without changing files, for example in CI, run:

```shell
deder exec -t runMvnApp -m mymodule fmtCheck
```

The check fails if files need formatting. See the [Scalafmt configuration reference](https://scalameta.org/scalafmt/docs/configuration.html) for more options.
