---
layout: tutorial.html
title: Publishing Tutorial
description: Deder Publishing Tutorial
---

# Publishing Tutorial

Make a minimal `deder.pkl` file in your project root:

```pkl
amends "https://sake92.github.io/deder/config/early-access/DederProject.pkl"

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
      url = "https://github.com/myuser/mylib"
      connection = "scm:git:git://github.com/myuser/mylib.git"
    }
  }
}

modules {
  mylibrary
}
```

All this information is necessary for publishing to Maven Central.
See the official [Sonatype Central Repository documentation](https://central.sonatype.org/publish/requirements/#required-pom-metadata/) for more details.

> You could publish to another repo by setting `publishTo`

### Git driven versioning

You can also use git driven versioning to automatically set the version based on your git tags.
To do this, you need to **remove the version** from `pomSettings`.
Deder will automatically set the version based on the latest semver-based git tag (with optional `v` prefix).

- `1.0.0` -> `1.0.0`
- `v1.0.0` -> `1.0.0`
- `1.0.0` with uncommitted changes -> `1.0.1-SNAPSHOT`
- `1.0.0` with a few committed changes -> `1.0.1-SNAPSHOT`

## Publish to Local Maven Repository

To publish to your local `.m2` repository, run the following command:
```shell
deder exec -t publishLocal
```

> For `publishLocal` purposes, only `groupId`, `artifactId`, `version`, `name` are needed.

After running this command, you can find the published artifact in your local `.m2` repository, located at `~/.m2/repository/com/example/deder-example-library_3/0.0.1-SNAPSHOT/`.
Then you can use it in other projects by adding the dependency:
```pkl
deps {
  "com.example::deder-example-library:0.0.1-SNAPSHOT"
}
```

## Publish to Sonatype Maven Central

To publish to Sonatype Maven Central, credentials can come from env vars following the pattern
`DEDER_<REPO_ID>_<FIELD>` (uppercased, `-` replaced with `_`).
With `id = "sonatype"` (default one), set these:
```shell
export DEDER_SONATYPE_USERNAME="..."
export DEDER_SONATYPE_PASSWORD="..."
export DEDER_SONATYPE_PGP_SECRET="..."
export DEDER_SONATYPE_PGP_PASSPHRASE="..."
```

If this is your first time publishing to Sonatype, you need to create an account and set up your PGP keys.
You can follow the  [sbt-ci-release documentation](https://github.com/sbt/sbt-ci-release?tab=readme-ov-file#sonatype/) for detailed instructions on how to do it.

Then you can run the following command to publish your library:
```shell
deder exec -t publish
```

For a full example, you can check out the [publish example](https://github.com/sake92/deder/tree/main/examples/publish).
