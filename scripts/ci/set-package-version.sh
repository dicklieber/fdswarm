#!/usr/bin/env bash

set -euo pipefail

version=""
if [[ "${GITHUB_REF_TYPE:-}" == "tag" ]]; then
  version="${GITHUB_REF_NAME#v}"
elif [[ -n "${DISPATCH_VERSION:-}" ]]; then
  version="${DISPATCH_VERSION#v}"
fi

if [[ -z "$version" ]]; then
  echo "No release version requested."
  exit 0
fi

if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Release version must be numeric major.minor.patch, got: $version" >&2
  exit 1
fi

echo "FDSWARM_VERSION=$version"
if [[ -n "${GITHUB_ENV:-}" ]]; then
  echo "FDSWARM_VERSION=$version" >> "$GITHUB_ENV"
fi
