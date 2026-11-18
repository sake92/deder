---
layout: howto.html
title: Rewrite Scala code with Scalafix
---

# {{ page.title }}

Create `.scalafix.conf` in the project root and select the rules to run. For example:

```hocon
rules = [RemoveUnused]
```

`RemoveUnused` is a semantic rule. Enable SemanticDB and unused warnings in the Scala module's configuration; for Scala 3.3.4 or later, for example:

```pkl
semanticdbEnabled = true
scalacOptions {
  "-Wunused:all"
}
```

Set these on each module where the rule runs, or in a shared module template. SemanticDB is enabled by default outside CI, but disabled when the `CI` environment variable is set, so set it explicitly for semantic rules in CI. For other Scala versions and warning settings, see the [RemoveUnused documentation](https://scalacenter.github.io/scalafix/docs/rules/RemoveUnused.html).

Apply the configured rules to a module:

```shell
deder exec -t fix -m mymodule
```

To check for rewrites without changing files, for example in CI, run:

```shell
deder exec -t fixCheck -m mymodule
```

The check fails if rewrites are needed. Both tasks compile the module before running Scalafix. See the [Scalafix rules](https://scalacenter.github.io/scalafix/docs/rules/overview.html) for other available rules and their requirements.
