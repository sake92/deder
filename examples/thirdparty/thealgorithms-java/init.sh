#!/bin/bash

set -e

cd repo

cp ../deder.pkl .

mkdir .deder
echo "version=v0.1.15" > .deder/server.properties
