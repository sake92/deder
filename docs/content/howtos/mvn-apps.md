---
layout: howto.html
title: Invoking Maven applications
---

# {{page.title}}

Deder provides a convenient way to invoke JARs from Maven as part of your build.  
This can be useful for checking formatting, generating code etc.

```pkl
mvnApps = new Mapping {
  ["myapp"] = new MvnApp {
    deps { "com.example:myapp:1.0.0" }
    mainClass = "com.example.myapp.Main"
    args {
      // e.g. pass in some arguments to the main class
      "someparameter"
      ...m.sources.toList().map((src) -> "\(m.root)/\(src)")
    }
  }
}
```

then you can invoke the app from your build like this:

```shell
deder exec -t runMvnApp myapp -- some_additional_args
```

Note that `some_additional_args` will be appended to the `args` defined in the `MvnApp`.

See [examples/mvnApp](https://github.com/sake92/deder/blob/main/examples/mvnApp/deder.pkl) for an even more type-safe example.


