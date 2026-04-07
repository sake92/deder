#!/bin/bash

set -e

cd repo

cp ../deder.pkl .

mkdir .deder
echo "version=v0.1.15" > .deder/server.properties

# sbt is old
echo "sbt.version=1.12.8" > project/build.properties

# scala is old
sed -i 's/scalaVersion := "2.13.6",/scalaVersion := "2.13.11",/g' build.sbt

# now this would also work fine:
deder import --from sbt

