#!/usr/bin/env bash

set -euo pipefail

job="${1:-package-assembly}"
shift || true

command -v act >/dev/null ||
  {
    echo "act is required. Install it from https://github.com/nektos/act." >&2
    exit 1
  }

act workflow_dispatch \
  --workflows .github/workflows/ci.yaml \
  --job "$job" \
  --platform ubuntu-latest=catthehacker/ubuntu:act-latest \
  --container-architecture linux/amd64 \
  "$@"
