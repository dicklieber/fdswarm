#!/usr/bin/env bash

set -euo pipefail

assembly_jar="${FDSWARM_ASSEMBLY_JAR:-out/fdswarm/assembly.dest/fdswarm.jar}"

echo "Packaging macOS DMG"
echo "PWD: $(pwd)"
echo "FDSWARM_VERSION: ${FDSWARM_VERSION:-}"
echo "FDSWARM_BUILD: ${FDSWARM_BUILD:-}"
echo "FDSWARM_ASSEMBLY_JAR: $assembly_jar"

test -f "$assembly_jar"
ls -lh "$assembly_jar"
./scripts/ci/verify-docs-in-jar.sh "$assembly_jar"

chmod +x ./mill
FDSWARM_ASSEMBLY_JAR="$assembly_jar" ./mill --no-server fdswarm.macDmg

find "${MILL_OUTPUT_DIR:-out}/fdswarm/macDmg.dest" -name '*.dmg' -print
