---
layout: reference.html
title: Custom Repositories
---

# {{page.title}}

By default, Deder resolves dependencies from Maven Central plus the local
Ivy2 cache. To pull artifacts from an internal Nexus/Artifactory, a public
mirror, JitPack, Sonatype snapshots, or an on-disk Maven layout, declare
them in your `deder.pkl`:

```pkl
repositories {
  new MavenRepository { url = "https://nexus.mycompany.com/repository/maven-releases/" }
  new MavenRepository { url = "https://jitpack.io" }
}
```

## Resolution order

Repositories are tried in this order:

1. Your declared `repositories`, in the order they appear in `deder.pkl`.
2. Maven Central.
3. The local Ivy2 cache (`~/.ivy2/local`).

"Custom first" means lookups for internal artifacts resolve against your
Nexus before Deder hits Maven Central — faster on a LAN, no 404 noise, no
leaking internal `groupId`s to the public repo.

## Disabling default repositories

For air-gapped or strict-internal-only setups, turn off the defaults:

```pkl
repositories {
  new MavenRepository { url = "https://nexus.mycompany.com/repository/maven-public/" }
}

includeDefaultRepos = false
```

Deder will refuse to load the project if `includeDefaultRepos = false` and
`repositories` is empty.

## Local directories

Use a `file://` URL with a Maven-layout directory:

```pkl
repositories {
  new MavenRepository { url = "file:///abs/path/to/local-repo" }
}
```

## Credentials

Deder does not store or read credentials. For authenticated repositories,
configure Coursier's own credential mechanisms — Deder will pick them up
transparently:

- `~/.config/coursier/credentials.properties`
- The `COURSIER_CREDENTIALS` environment variable

See
[Coursier's credentials documentation](https://get-coursier.io/docs/other-credentials)
for syntax.
