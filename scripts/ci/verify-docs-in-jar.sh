#!/usr/bin/env bash

set -euo pipefail

jar_path="${1:-out/fdswarm/assembly.dest/fdswarm.jar}"
docs_index="FDSwarmDocs/index.html"

echo "Verifying docs in JAR"
echo "JAR: $jar_path"

test -f "$jar_path"
jar tf "$jar_path" | grep -q "$docs_index" ||
  {
    echo "Error: $jar_path does not contain $docs_index" >&2
    exit 1
  }

echo "Found $docs_index"

docs_html="$(unzip -p "$jar_path" "$docs_index" 2>/dev/null || true)"
if [[ -z "$docs_html" ]]; then
  echo "Error: could not read $docs_index from $jar_path" >&2
  exit 1
fi

require_nav_item() {
  local item="$1"

  if [[ "$docs_html" != *"$item"* ]]; then
    echo "Error: $docs_index is missing expected nav item: $item" >&2
    exit 1
  fi
}

require_nav_item 'href="install.html">Install</a>'
require_nav_item 'nav-header">User</li>'
require_nav_item 'href="user/fdswarm.html">FDSwarm</a>'
require_nav_item 'nav-header">Developer</li>'
require_nav_item 'href="developer/scaladoc.html">Scaladoc API Reference</a>'

install_line="$(printf '%s\n' "$docs_html" | grep -n 'href="install.html">Install</a>' | head -n 1 | cut -d: -f1)"
user_line="$(printf '%s\n' "$docs_html" | grep -n 'nav-header">User</li>' | head -n 1 | cut -d: -f1)"
developer_line="$(printf '%s\n' "$docs_html" | grep -n 'nav-header">Developer</li>' | head -n 1 | cut -d: -f1)"

if (( install_line >= user_line || user_line >= developer_line )); then
  echo "Error: $docs_index has stale docs navigation order; expected Install, User, Developer" >&2
  exit 1
fi

echo "Docs navigation matches directory.conf"
