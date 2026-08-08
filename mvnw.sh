#!/usr/bin/env bash
# Runs Maven inside Docker so the repo needs no local JDK.
# Usage: ./mvnw.sh <maven args>          e.g. ./mvnw.sh -q -DskipTests package
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
docker volume create learnnexus-m2 >/dev/null
exec docker run --rm \
  -v "$ROOT/backend":/app \
  -v learnnexus-m2:/root/.m2 \
  -w /app \
  --network learnnexus_default \
  -e MAVEN_OPTS="-XX:TieredStopAtLevel=1" \
  maven:3.9-eclipse-temurin-21 mvn "$@"
