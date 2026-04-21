---
layout: howto.html
title: Environment Variables and JVM options
---

# {{page.title}}

## How to pass environment variables to run/test

You need to use the `forkEnv` property in module config:
```pkl
forkEnv {
  ["MY_VAR"] = "true"
}
```

Then you can read this env var when running your app with `deder exec -t run`,
or when you run tests with `deder exec -t test`.


## How to pass JVM options to run/test

You need to use the `jvmOptions` property in module config:
```pkl
jvmOptions {
  "-Xmx128m"
  "-XX:+PrintCompilation"
}
```

