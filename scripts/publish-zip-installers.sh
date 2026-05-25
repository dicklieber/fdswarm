#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"
runtimes_dir="$repo_dir/fdswarm-runtimes"
work_dir="$repo_dir/release/zip-installers"
latest_tag=""
keep_work=false

platforms=(
  "windows-x64:java.exe"
  "windows-arm64:java.exe"
  "macos-aarch64:java"
  "linux-x64:java"
  "linux-aarch64:java"
)

usage() {
  cat <<'EOF'
Usage:
  scripts/publish-zip-installers.sh [options]

Downloads the fdswarm.jar asset from the latest GitHub release, extracts the
release version from its manifest, builds all ZIP installers from local
fdswarm-runtimes, and uploads the ZIPs to the matching GitHub release.

Options:
  --tag TAG           Download fdswarm.jar from this release instead of latest.
  --runtimes-dir DIR  Runtime directory. Default: fdswarm-runtimes.
  --work-dir DIR      Working/output directory. Default: release/zip-installers.
  --keep-work         Keep staging directories after ZIP creation.
  -h, --help          Show this help.
EOF
}

die() {
  echo "publish-zip-installers: $*" >&2
  exit 1
}

require_command() {
  local command_name="$1"
  command -v "$command_name" >/dev/null 2>&1 ||
    die "required command not found: $command_name"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tag)
      latest_tag="$2"
      shift 2
      ;;
    --runtimes-dir)
      runtimes_dir="$2"
      shift 2
      ;;
    --work-dir)
      work_dir="$2"
      shift 2
      ;;
    --keep-work)
      keep_work=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "unknown argument: $1"
      ;;
  esac
done

require_command awk
require_command find
require_command gh
require_command sort
require_command unzip
require_command zip

cd "$repo_dir"

runtimes_dir="$(cd "$runtimes_dir" 2>/dev/null && pwd)" ||
  die "runtime directory does not exist: $runtimes_dir"
mkdir -p "$work_dir"
work_dir="$(cd "$work_dir" && pwd)"

gh auth status >/dev/null

if [[ -z "$latest_tag" ]]; then
  latest_tag="$(gh release list --limit 1 --json tagName --jq '.[0].tagName')"
fi
[[ -n "$latest_tag" ]] || die "could not determine latest GitHub release tag"

download_dir="$work_dir/download"
artifacts_dir="$work_dir/artifacts"
stage_root="$work_dir/stage"
jar_path="$download_dir/fdswarm.jar"

rm -rf "$download_dir" "$artifacts_dir" "$stage_root"
mkdir -p "$download_dir" "$artifacts_dir" "$stage_root"

echo "Downloading fdswarm.jar from GitHub release $latest_tag..."
gh release download "$latest_tag" --pattern "fdswarm.jar" --dir "$download_dir" --clobber
[[ -f "$jar_path" ]] || die "downloaded release did not contain fdswarm.jar"

extract_implementation_version() {
  local jar="$1"

  { unzip -p "$jar" META-INF/MANIFEST.MF 2>/dev/null || true; } |
    tr -d '\r' |
    awk -F': ' '/^Implementation-Version: / { print $2; exit }'
}

version="$(extract_implementation_version "$jar_path")"
[[ -n "$version" ]] || die "could not extract Implementation-Version from $jar_path"
[[ "$version" != *-SNAPSHOT ]] || die "refusing to publish snapshot version: $version"

release_tag="v$version"
echo "Using release version $version"

if gh release view "$release_tag" >/dev/null 2>&1; then
  echo "GitHub release exists: $release_tag"
else
  gh release create "$release_tag" --title "$release_tag" --generate-notes
  echo "Created GitHub release: $release_tag"
fi

find_runtime_home() {
  local platform="$1"
  local java_exe="$2"
  local root="$runtimes_dir/$platform"
  local java_bin

  [[ -d "$root" ]] || die "missing runtime directory: $root"

  java_bin="$(find "$root" -type f -path "*/bin/$java_exe" -print | sort | head -n 1)"
  [[ -n "$java_bin" ]] || die "could not find bin/$java_exe under $root"

  dirname "$(dirname "$java_bin")"
}

copy_tree() {
  local from="$1"
  local to="$2"

  mkdir -p "$to"
  tar -C "$from" -cf - . | tar -C "$to" -xf -
}

write_windows_launchers() {
  local bin_dir="$1"

  cat > "$bin_dir/fdswarm.bat" <<'EOF'
@echo off
setlocal
set APP_HOME=%~dp0..
"%APP_HOME%\runtime\bin\java.exe" -jar "%APP_HOME%\lib\fdswarm.jar" %*
EOF

  cat > "$bin_dir/install-start-menu-shortcut.bat" <<'EOF'
@echo off
setlocal

set "APP_HOME=%~dp0.."
set "LAUNCHER=%~dp0fdswarm.bat"
set "START_MENU=%APPDATA%\Microsoft\Windows\Start Menu\Programs"
set "SHORTCUT=%START_MENU%\FdSwarm.lnk"

if not exist "%START_MENU%" mkdir "%START_MENU%"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$shell = New-Object -ComObject WScript.Shell; $shortcut = $shell.CreateShortcut($env:SHORTCUT); $shortcut.TargetPath = $env:LAUNCHER; $shortcut.WorkingDirectory = $env:APP_HOME; $shortcut.Description = 'FdSwarm'; $shortcut.Save()"

if errorlevel 1 (
  echo Failed to create Start Menu shortcut.
  exit /b 1
)

echo Created Start Menu shortcut: %SHORTCUT%
EOF
}

write_unix_launcher() {
  local platform="$1"
  local launcher="$2"

  cat > "$launcher" <<EOF
#!/usr/bin/env sh
set -eu
APP_HOME="\$(CDPATH= cd -- "\$(dirname -- "\$0")/.." && pwd)"
EXPECTED_PLATFORM="$platform"
case "\$(uname -s)" in
  Darwin) ACTUAL_OS="macos" ;;
  Linux) ACTUAL_OS="linux" ;;
  *) ACTUAL_OS="\$(uname -s)" ;;
esac
case "\$(uname -m)" in
  x86_64|amd64) ACTUAL_ARCH="x64" ;;
  arm64|aarch64) ACTUAL_ARCH="aarch64" ;;
  *) ACTUAL_ARCH="\$(uname -m)" ;;
esac
ACTUAL_PLATFORM="\$ACTUAL_OS-\$ACTUAL_ARCH"
if [ "\$EXPECTED_PLATFORM" != "\$ACTUAL_PLATFORM" ]; then
  echo "This FdSwarm package is for \$EXPECTED_PLATFORM, but this system is \$ACTUAL_PLATFORM." >&2
  exit 126
fi
exec "\$APP_HOME/runtime/bin/java" -jar "\$APP_HOME/lib/fdswarm.jar" "\$@"
EOF
  chmod +x "$launcher"
}

build_zip() {
  local platform="$1"
  local java_exe="$2"
  local runtime_home
  local package_name
  local stage_dir
  local app_dir
  local zip_path

  runtime_home="$(find_runtime_home "$platform" "$java_exe")"
  package_name="FdSwarm-$version-$platform"
  stage_dir="$stage_root/$package_name"
  app_dir="$stage_dir/FdSwarm"
  zip_path="$artifacts_dir/$package_name.zip"

  rm -rf "$stage_dir" "$zip_path"
  mkdir -p "$app_dir/bin" "$app_dir/lib" "$app_dir/runtime"

  cp "$jar_path" "$app_dir/lib/fdswarm.jar"
  copy_tree "$runtime_home" "$app_dir/runtime"

  if [[ -f "$repo_dir/application.conf" ]]; then
    mkdir -p "$app_dir/conf"
    cp "$repo_dir/application.conf" "$app_dir/conf/application.conf"
  elif [[ -f "$repo_dir/fdswarm/resources/application.conf" ]]; then
    mkdir -p "$app_dir/conf"
    cp "$repo_dir/fdswarm/resources/application.conf" "$app_dir/conf/application.conf"
  fi

  if [[ "$platform" == windows-* ]]; then
    write_windows_launchers "$app_dir/bin"
  else
    write_unix_launcher "$platform" "$app_dir/bin/fdswarm"
  fi

  (cd "$stage_dir" && zip -qr "$zip_path" "FdSwarm")
  echo "Built $zip_path"
}

for platform_entry in "${platforms[@]}"; do
  IFS=: read -r platform java_exe <<< "$platform_entry"
  build_zip "$platform" "$java_exe"
done

echo "Uploading ZIP installers to GitHub release $release_tag..."
for artifact in "$artifacts_dir"/*.zip; do
  gh release upload "$release_tag" "$artifact" --clobber
  echo "Uploaded $(basename "$artifact")"
done

if [[ "$keep_work" != true ]]; then
  rm -rf "$stage_root"
fi

echo "Published ZIP installers for $release_tag"
