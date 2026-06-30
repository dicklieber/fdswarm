#!/usr/bin/env bash
set -euo pipefail

./mill fdswarm.assembly

FDSWARM_JAR="out/fdswarm/assembly.dest/fdswarm.jar"
if [[ ! -f "$FDSWARM_JAR" ]]; then
  echo "Could not find fdswarm assembly JAR in out/fdswarm/assembly.dest" >&2
  exit 1
fi

PORT=8080 java -jar "$FDSWARM_JAR" "$@"
