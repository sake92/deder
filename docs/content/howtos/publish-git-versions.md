---
layout: howto.html
title: Publish with Git-driven versioning
---

# Publish with Git-driven versioning

Resolve the Git version once and pass it to every CI job that builds or publishes artifacts. This prevents a snapshot workflow from switching to a stable release version when a tag is pushed before its publish job checks out the repository.

This example publishes snapshots from `main` and releases from tags using GitHub Actions and Sonatype. It assumes your publishable modules share one version, have publishing and POM metadata configured, and use a shared module template. See the [publishing tutorial](/tutorials/publishing.html) for the initial setup.

Use `-SNAPSHOT` versions to test changes and integrations during development, and publish stable versions when they are ready for release. This also helps avoid unnecessary stable releases: [Maven Central publishing limits](https://central.sonatype.org/publish/maven-central-publishing-limits/) track release count, file count, and release size. Check Sonatype's current guidance and your organization's usage for the limits that apply.

## Configure the shared module template

Set the version and repository on the template used by every publishable module. When the environment variable is absent, Deder derives the version from Git as usual.

```pkl
local const publishRepo = if (read?("env:DEDER_PUBLISH_SNAPSHOT") == "true")
  new SonatypeSnapshotRepo { id = "sonatype" }
else
  new SonatypeCentralRepo { id = "sonatype" }

// Add these settings to your existing shared module template.
// Keep the other required POM settings, such as groupId and artifactId.
publishTo = publishRepo
pomSettings {
  version = read?("env:RELEASE_VERSION")
}
```

## Resolve and pass the version

In the first job, check out full Git history and tags, resolve the version from a publishable module, and expose it as a job output. Set the environment variables for subsequent steps, then restart Deder so its server inherits them. The publish job receives the same values before starting Deder.

Replace `mylib` with a publishable module ID and adjust the publish selector for your project. Configure the four Sonatype secrets shown below in your repository. Add your existing compile and test steps after the version step; they will use the locked version.

```yaml
name: Publish
on:
  push:
    branches: [main]
    tags: ['*']

jobs:
  build:
    runs-on: ubuntu-latest
    outputs:
      version: ${{ steps.version.outputs.version }}
      snapshot: ${{ steps.version.outputs.snapshot }}
    steps:
      - uses: actions/checkout@v5
        with:
          fetch-depth: 0
      - uses: actions/setup-java@v3
        with:
          distribution: temurin
          java-version: 21
      - uses: Homebrew/actions/setup-homebrew@main
      - run: brew install sake92/tap/deder
      - name: Resolve version
        id: version
        shell: bash
        run: |
          set -euo pipefail
          result=$(deder exec -t version -m mylib --format=json)
          version=$(jq -er '
            if (.failures | length) > 0 then error("Version task failed") else . end
            | [.results[]] | unique
            | if length == 1 and (.[0] | type == "string") then .[0]
              else error("Expected one version") end
          ' <<< "$result")
          # Restrict this example to stable X.Y.Z tags and Deder snapshots.
          [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?$ ]]
          snapshot=false
          [[ "$version" == *-SNAPSHOT ]] && snapshot=true
          if [[ "$GITHUB_REF_TYPE" == tag ]]; then
            tag_version="${GITHUB_REF_NAME#v}"
            [[ "$snapshot" == false && "$version" == "$tag_version" ]]
          fi
          echo "version=$version" >> "$GITHUB_OUTPUT"
          echo "snapshot=$snapshot" >> "$GITHUB_OUTPUT"
          echo "RELEASE_VERSION=$version" >> "$GITHUB_ENV"
          echo "DEDER_PUBLISH_SNAPSHOT=$snapshot" >> "$GITHUB_ENV"
          deder shutdown
      - run: deder exec -t compile
      - run: deder exec -t test

  publish:
    needs: build
    if: github.ref_type == 'tag' || needs.build.outputs.snapshot == 'true'
    runs-on: ubuntu-latest
    env:
      RELEASE_VERSION: ${{ needs.build.outputs.version }}
      DEDER_PUBLISH_SNAPSHOT: ${{ needs.build.outputs.snapshot }}
      DEDER_SONATYPE_USERNAME: ${{ secrets.SONATYPE_USERNAME }}
      DEDER_SONATYPE_PASSWORD: ${{ secrets.SONATYPE_PASSWORD }}
      DEDER_SONATYPE_PGP_SECRET: ${{ secrets.PGP_SECRET }}
      DEDER_SONATYPE_PGP_PASSPHRASE: ${{ secrets.PGP_PASSPHRASE }}
    steps:
      - uses: actions/checkout@v5
        with:
          fetch-depth: 0
      - uses: actions/setup-java@v3
        with:
          distribution: temurin
          java-version: 21
      - uses: Homebrew/actions/setup-homebrew@main
      - run: brew install sake92/tap/deder
      - run: deder exec -t publish -m mylib
```

For example, after tag `0.1.0`, a new commit on `main` resolves to `0.1.1-SNAPSHOT`. If that version is resolved before the same commit is tagged `0.1.1`, the main workflow still publishes `0.1.1-SNAPSHOT`, and the tag workflow publishes `0.1.1`. If the tag is already present at the first checkout, the main workflow resolves `0.1.1` and skips its snapshot publication; the tag workflow publishes the release. A later commit on `main` resolves to `0.1.2-SNAPSHOT`.

The lock preserves the version resolved by the first job, not the tag state at push time. A rerun can therefore resolve a different version if tags have changed. Also, separate main workflows can publish the same snapshot version out of order; add CI coordination if your project requires the snapshot to always represent the newest commit.
