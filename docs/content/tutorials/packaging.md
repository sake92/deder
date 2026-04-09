---
layout: tutorial.html
title: Packaging Tutorial
description: Deder Packaging Tutorial
---


# {{page.title}}

Create this `deder.pkl` file:

```pkl
amends "https://sake92.github.io/deder/config/early-access/DederProject.pkl"

local const myappModules = new CreateScalaModules {
  root = "myapp"
  template = new {
    scalaVersion = "3.7.1"
    deps {
      "com.lihaoyi::pprint:0.9.5"
    }
    mainClass = "myapp.Main"
  }
  testTemplate = (template.asTest()) {
    deps {
      "org.scalameta::munit:1.2.1"
    }
  }
}.get

modules {
  ...myappModules.all
}
```

then add `myapp/src/myapp/Main.scala`:
```
package myapp

@main def Main() =
  println("Hello world!")
```

then run it just to verify it works: `deder exec -t run -m myapp`.  
You should see `Hello world!` printed in console.

## Packaging a JAR

If we try to run the `jar` task we will get an error:
```shell
deder exec -t jar -m myapp
[info] Executing 'jar' task on module: myapp
[error] POM settings are not set for myapp
```

Let's add just the minimal `pomSettings` so we can build it, add this inside `template` block:
```
pomSettings {
  groupId = "com.example"
  artifactId = "myapp"
  name = "myapp"
}
```

then run this:
```shell
deder exec -t jar -m myapp --json
# will print {"myapp":"/<your_path>/myapp/.deder/out/myapp/jar/myapp.jar"}
```

then if you try to run it you will get an error (expected):
```shell
(base) sake@pop-os:~/projects/sake92/tmp/deder_tmp/myapp$ java -jar /home/sake/projects/sake92/tmp/deder_tmp/myapp/.deder/out/myapp/jar/myapp.jar
Error: Unable to initialize main class myapp.Main
Caused by: java.lang.NoClassDefFoundError: scala/util/CommandLineParser$ParseError
```

This is expected because the `jar` task only bundles *your module classes* and nothing else!  
It does not go recursively through your module deps and add those JARs..

The `jar` task is used as part of publishing process. 
A `pom.xml` file will be distributed together with it, so that users of your jar (usually a library) know which dependencies it needs.
Then the build tool will download those and add to the classpath (you don't need to worry about this too much)


##  Packaging an assemby JAR (uber JAR)

If you build an *assembly JAR*, you will be able to run it:
```shell
deder exec -t assembly -m myapp --json
# will print {"myapp":"/<your_path>/myapp/.deder/out/myapp/assembly/out.jar"}

java -jar .deder/out/myapp/assembly/out.jar
# Hello world!
```

This is because the `assembly` task does a lot more than just packaging your code.  
It will also:
- resolve the dependencies and add them to final JAR
- add you module classes to final JAR
- add a `META-INF/MANIFETS.MF` file with `Main-Class` entry, so you dont have to specify the main class on CLI




