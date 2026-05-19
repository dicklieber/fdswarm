#!/usr/bin/env bash

set -euo pipefail

jar_version="${FDSWARM_VERSION:-0.0.0-dev}"
if [[ -n "${FDSWARM_BUILD:-}" ]]; then
  jar_version="${jar_version}-build${FDSWARM_BUILD}"
fi

src="out/fdswarm/assembly.dest/fdswarm.jar"
dest="out/fdswarm/assembly.dest/FdSwarm-${jar_version}-assembly.jar"

test -f "$src"
cp "$src" "$dest"
echo "Named release JAR: $dest"
