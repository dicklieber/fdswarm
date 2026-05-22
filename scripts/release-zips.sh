#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"

app_name="FdSwarm"
product_name="fdswarm"
main_class="fdswarm.FdSwarm"
main_jar_name="fdswarm.jar"
assembly_jar="$repo_dir/out/fdswarm/assembly.dest/$main_jar_name"
work_dir="$repo_dir/out/fdswarm/dist-all.dest"
artifacts_dir="$repo_dir/release/artifacts"
runtime_root="${FDSWARM_RUNTIMES_DIR:-$repo_dir/fdswarm-runtimes}"
publish=true
tag=""
skip_verify_docs=false

usage() {
  cat <<'EOF'
Usage:
  scripts/release-zips.sh [--no-publish] [--tag TAG] [--skip-verify-docs]

Builds ZIP distributions for each runtime found under fdswarm-runtimes from:
  out/fdswarm/assembly.dest/fdswarm.jar

Options:
  --no-publish        Build ZIPs without uploading them to GitHub.
  --tag TAG           GitHub release tag. Defaults to v<jar Implementation-Version>.
  --skip-verify-docs  Do not require FDSwarmDocs/index.html in the JAR.
  -h, --help          Show this help.
EOF
}

die() {
  echo "release-zips: $*" >&2
  exit 1
}

require_command() {
  local cmd="$1"
  command -v "$cmd" >/dev/null 2>&1 || die "required command not found: $cmd"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --publish)
      publish=true
      shift
      ;;
    --no-publish)
      publish=false
      shift
      ;;
    --tag)
      tag="${2:-}"
      [[ -n "$tag" ]] || die "--tag requires a value"
      shift 2
      ;;
    --skip-verify-docs)
      skip_verify_docs=true
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

extract_jar_entry() {
  local jar_path="$1"
  local entry="$2"

  unzip -p "$jar_path" "$entry" 2>/dev/null || true
}

jar_contains_entry() {
  local jar_path="$1"
  local entry="$2"

  jar tf "$jar_path" | grep -Fxq "$entry"
}

manifest_value() {
  local jar_path="$1"
  local name="$2"

  extract_jar_entry "$jar_path" META-INF/MANIFEST.MF |
    tr -d '\r' |
    awk -v name="$name" -F': ' '$1 == name { print $2; exit }'
}

discover_runtime_images() {
  [[ -d "$runtime_root" ]] ||
    die "runtime root not found: $runtime_root. Run scripts/fetch-liberica-runtimes.sh."

  local runtime_dir platform java_path runtime_image
  find "$runtime_root" -mindepth 1 -maxdepth 1 -type d ! -name cache -print |
    sort |
    while IFS= read -r runtime_dir; do
      platform="$(basename "$runtime_dir")"
      java_path="$(find "$runtime_dir" -type f \( -path '*/bin/java' -o -path '*/bin/java.exe' \) -print -quit)"
      if [[ -n "$java_path" ]]; then
        runtime_image="${java_path%/bin/java}"
        runtime_image="${runtime_image%/bin/java.exe}"
        printf '%s\t%s\n' "$platform" "$runtime_image"
      fi
    done
}

write_launchers() {
  local stage="$1"
  local platform="$2"
  local sh_launcher="$stage/bin/$product_name"
  local bat_launcher="$stage/bin/$product_name.bat"

  cat > "$bat_launcher" <<EOF
@echo off
set APP_HOME=%~dp0..
"%APP_HOME%\\runtime\\bin\\java.exe" -jar "%APP_HOME%\\lib\\$product_name-all.jar" %*
EOF

  if [[ "$platform" == windows-* ]]; then
    cat > "$stage/bin/install-start-menu-shortcut.bat" <<EOF
@echo off
setlocal

set "APP_HOME=%~dp0.."
set "LAUNCHER=%~dp0$product_name.bat"
set "START_MENU=%APPDATA%\\Microsoft\\Windows\\Start Menu\\Programs"
set "SHORTCUT=%START_MENU%\\$app_name.lnk"

if not exist "%START_MENU%" mkdir "%START_MENU%"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "\$shell = New-Object -ComObject WScript.Shell; \$shortcut = \$shell.CreateShortcut(\$env:SHORTCUT); \$shortcut.TargetPath = \$env:LAUNCHER; \$shortcut.WorkingDirectory = \$env:APP_HOME; \$shortcut.Description = '$app_name'; \$shortcut.Save()"

if errorlevel 1 (
  echo Failed to create Start Menu shortcut.
  exit /b 1
)

echo Created Start Menu shortcut: %SHORTCUT%
EOF
  fi

  cat > "$sh_launcher" <<EOF
#!/usr/bin/env sh
set -eu
APP_HOME="\$(cd "\$(dirname "\$0")/.." && pwd)"
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
  echo "This $app_name package is for \$EXPECTED_PLATFORM, but this system is \$ACTUAL_PLATFORM." >&2
  exit 126
fi
exec "\$APP_HOME/runtime/bin/java" -jar "\$APP_HOME/lib/$product_name-all.jar" "\$@"
EOF
  chmod 755 "$sh_launcher"
}

stage_application_conf() {
  local stage="$1"
  local conf=""

  if [[ -f "$repo_dir/application.conf" ]]; then
    conf="$repo_dir/application.conf"
  elif [[ -f "$repo_dir/fdswarm/resources/application.conf" ]]; then
    conf="$repo_dir/fdswarm/resources/application.conf"
  fi

  if [[ -n "$conf" ]]; then
    mkdir -p "$stage/conf"
    cp -f "$conf" "$stage/conf/application.conf"
  fi
}

zip_stage() {
  local stage_root="$1"
  local dist_name="$2"
  local zip_path="$3"

  rm -f "$zip_path"
  if command -v ditto >/dev/null 2>&1; then
    ditto -c -k --keepParent "$stage_root/$dist_name" "$zip_path"
  else
    require_command zip
    (cd "$stage_root" && zip -qr "$zip_path" "$dist_name")
  fi
}

build_zip_dist() {
  local platform="$1"
  local runtime="$2"
  local stage_root="$work_dir/stage-$platform"
  local stage="$stage_root/$app_name"
  local zip_path="$artifacts_dir/$app_name-$jar_version-$platform.zip"

  rm -rf "$stage_root" "$zip_path"
  mkdir -p "$stage/bin" "$stage/lib" "$stage/runtime"

  cp -f "$assembly_jar" "$stage/lib/$product_name-all.jar"
  cp -R "$runtime"/. "$stage/runtime/"
  stage_application_conf "$stage"
  write_launchers "$stage" "$platform"
  zip_stage "$stage_root" "$app_name" "$zip_path"

  echo "ZIP: $zip_path"
  built_zips+=("$zip_path")
}

publish_zip() {
  local zip_path="$1"

  require_command gh
  gh auth status >/dev/null
  gh release view "$tag" >/dev/null
  echo "Publishing $zip_path to GitHub release $tag"
  gh release upload "$tag" "$zip_path" --clobber
}

require_command find
require_command jar
require_command unzip

[[ -f "$assembly_jar" ]] || die "assembly JAR not found: $assembly_jar"

jar_version="$(manifest_value "$assembly_jar" Implementation-Version)"
[[ -n "$jar_version" ]] || die "JAR manifest does not contain Implementation-Version: $assembly_jar"
[[ "$jar_version" != *-SNAPSHOT ]] || die "refusing to package snapshot JAR version: $jar_version"

main_class_entry="${main_class//.//}.class"
jar_contains_entry "$assembly_jar" "$main_class_entry" ||
  die "JAR does not contain main class $main_class ($main_class_entry): $assembly_jar"

if [[ "$skip_verify_docs" == false ]]; then
  jar_contains_entry "$assembly_jar" FDSwarmDocs/index.html ||
    die "JAR does not contain FDSwarmDocs/index.html: $assembly_jar"
fi

if [[ -z "$tag" ]]; then
  tag="v$jar_version"
fi

mkdir -p "$work_dir" "$artifacts_dir"

echo "Building ZIP distributions"
echo "JAR: $assembly_jar"
echo "Version: $jar_version"
echo "Runtimes: $runtime_root"
echo "Artifacts: $artifacts_dir"

built_zips=()
while IFS=$'\t' read -r platform runtime; do
  [[ -n "$platform" && -n "$runtime" ]] || continue
  build_zip_dist "$platform" "$runtime"
done < <(discover_runtime_images)

if [[ "${#built_zips[@]}" -eq 0 ]]; then
  die "no runtime images found under $runtime_root"
fi

if [[ "$publish" == true ]]; then
  for zip_path in "${built_zips[@]}"; do
    publish_zip "$zip_path"
  done
fi
