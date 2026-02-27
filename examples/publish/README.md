
Publish to local .m2 repository:
```shell
deder exec -t publishLocal
```

Publish to Sonatype Maven Central:
```shell
export DEDER_PGP_PASSPHRASE="..."
export DEDER_PGP_SECRET="..."
export DEDER_SONATYPE_PASSWORD="..."
export DEDER_SONATYPE_USERNAME="..."

deder exec -t publish
```
