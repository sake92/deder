---
title: Scala Native Tutorial
description: Deder Scala Native Tutorial
---

# Scala Native Tutorial

Create a `deder.pkl` file in your project root:

```pkl
amends "https://sake92.github.io/deder/config/DederProject.pkl"

local const cli: ScalaNativeModule = new {
  id = "cli"
  scalaVersion = "3.7.4"
  scalaNativeVersion = "0.5.10"
}

modules {
  cli
}
```

Link it to native:
```shell
deder exec -t nativeLink
```

and run it:
```shell
./.deder/out/cli/nativeLink/cli
```

