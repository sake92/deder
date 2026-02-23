---
title: Scala.js Tutorial
description: Deder Scala.js Tutorial
---

# Scala.js Tutorial

Create a `deder.pkl` file in your project root:

```pkl
amends "https://sake92.github.io/deder/config/DederProject.pkl"

local const frontend: ScalaJsModule = new {
  id = "frontend"
  scalaVersion = "3.7.1"
  scalaJsVersion = "1.20.2"
  moduleKind = "es-module"
  deps {
    "org.scala-js::scalajs-dom::2.2.0"
  }
}

modules {
  frontend
}
```

Run in watch mode:
```bash
deder exec -m frontend -t fastLinkJs -w
```

The output will be in `.deder/out/frontend/fastLinkJs/main.js`.  
You can include this in an HTML page to run your Scala.js code in the browser.

For a more elaborate example, you can check out the [Example with Vite + Sharaf backend](https://github.com/sake92/deder/tree/main/examples/scalajs)
