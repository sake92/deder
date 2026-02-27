---
title: Publishing Tutorial
description: Deder Publishing Tutorial
---

# Publishing Tutorial

Make a minimal `deder.pkl` file in your project root:

```pkl
amends "https://sake92.github.io/deder/config/DederProject.pkl"

local const mylibrary = new ScalaModule {
  scalaVersion = "3.7.1"
  id = "mylibrary"
  deps {
    "ba.sake::sharaf-undertow:0.13.0"
  }
  pomSettings {
    groupId = "com.example"
    artifactId = "deder-example-library"
    version = "0.0.1-SNAPSHOT"
    name = "Deder Publish Example"
    description = "Deder example library"
    url = "https://github.com/myuser/deder-example-library"
    licenses {
      new PomLicense {
        name = "MIT License"
        url = "http://www.opensource.org/licenses/mit-license.php"
      }
    }
    developers {
      new PomDeveloper {
        id = "myuser"
        name = "My User"
        email = "myuser@example.com"
      }
    }
    scm {
      url = "git@github.com:myuser/deder-example-library.git"
    }
  }
}

modules {
  mylibrary
}
```

All this information is necessary for publishing to Maven repositories.  
See the official [Sonatype Central Repository documentation](https://central.sonatype.org/publish/requirements/#required-pom-metadata/) for more details.


## Publish to Local Maven Repository

To publish to your local `.m2` repository, run the following command:
```bash
deder exec -t publishLocal
```

> For `publishLocal` purposes, only `groupId`, `artifactId`, `version`, `name` are needed.

After running this command, you can find the published artifact in your local `.m2` repository, located at `~/.m2/repository/com/example/deder-example-library_3/0.0.1-SNAPSHOT/`.  
Then you can use it in other projects by adding the dependency:
```scala
// deder
"com.example::deder-example-library:0.0.1-SNAPSHOT"
// sbt
libraryDependencies += "com.example" %% "deder-example-library" % "0.0.1-SNAPSHOT"
// mill
mvn"com.example::deder-example-library:0.0.1-SNAPSHOT"
```


## Publish to Sonatype Maven Central

To publish to Sonatype Maven Central, you need to set the following environment variables:
```bash
export DEDER_PGP_PASSPHRASE="..."
export DEDER_PGP_SECRET="..."
export DEDER_SONATYPE_PASSWORD="..."
export DEDER_SONATYPE_USERNAME="..."
```

If this is your first time publishing to Sonatype, you need to create an account and set up your PGP keys.  
You can follow the  [sbt-ci-release documentation](https://github.com/sbt/sbt-ci-release?tab=readme-ov-file#sonatype/) for detailed instructions on how to do it.  

Then you can run the following command to publish your library:
```bash
deder exec -t publish
```

For a full example, you can check out the [publish example](https://github.com/sake92/deder/tree/main/examples/publish).
