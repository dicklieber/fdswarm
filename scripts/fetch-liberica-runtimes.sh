#!/usr/bin/env bash

set -euo pipefail

dest="fdswarm-runtimes"
env_file=""
missing_only=false

usage() {
  cat <<'EOF'
Usage:
  scripts/fetch-liberica-runtimes.sh [--dest DIR] [--env-file FILE] [--missing-only]

Downloads and unpacks BellSoft Liberica JDK 21 Full runtimes for:
  - Windows x64
  - Windows ARM64
  - macOS aarch64
  - Linux x64
  - Linux aarch64

By default, runtimes are written under fdswarm-runtimes for
BuildZipInstallers.scala and PublishZipInstallers.scala.

Options:
  --dest DIR        Directory for downloaded archives and unpacked runtimes.
  --env-file FILE   Write optional shell export overrides to FILE.
  --missing-only    Reuse installed runtimes; fetch only missing platform images.
  -h, --help        Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dest)
      dest="$2"
      shift 2
      ;;
    --env-file)
      env_file="$2"
      shift 2
      ;;
    --missing-only)
      missing_only=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

require_command() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Required command not found: $cmd" >&2
    exit 1
  fi
}

require_command curl
require_command find
require_command tar
require_command unzip

mkdir -p "$dest/cache"
if [[ -n "$env_file" ]]; then
  mkdir -p "$(dirname "$env_file")"
  : > "$env_file"
fi

liberica_url() {
  local os_name="$1"
  local arch="$2"
  local package_type="$3"
  local url

  url="$(
    curl -fsSL \
      "https://api.bell-sw.com/v1/liberica/releases?version-modifier=latest&version-feature=21&bitness=64&os=$os_name&arch=$arch&package-type=$package_type&bundle-type=jdk-full&output=text&fields=downloadUrl" |
      head -n 1
  )"

  if [[ ! "$url" =~ ^https:// ]]; then
    echo "BellSoft API did not return a download URL for $os_name/$arch/$package_type: $url" >&2
    exit 1
  fi

  printf '%s\n' "$url"
}

download() {
  local name="$1"
  local url="$2"
  local archive="$3"

  echo "Downloading $name"
  echo "  $url"
  curl -fL --retry 3 --output "$archive" "$url"
}

extract_zip() {
  local archive="$1"
  local out_dir="$2"

  rm -rf "$out_dir"
  mkdir -p "$out_dir"
  unzip -q "$archive" -d "$out_dir"
}

extract_tar_gz() {
  local archive="$1"
  local out_dir="$2"

  rm -rf "$out_dir"
  mkdir -p "$out_dir"
  tar -xzf "$archive" -C "$out_dir"
}

find_java_home() {
  local root="$1"
  local exe="$2"
  local java_bin

  java_bin="$(find "$root" -type f -path "*/bin/$exe" -print -quit)"
  if [[ -z "$java_bin" ]]; then
    echo "Could not find bin/$exe under $root" >&2
    exit 1
  fi

  dirname "$(dirname "$java_bin")"
}

write_env() {
  local name="$1"
  local value="$2"

  local export_line
  export_line="$(printf 'export %s=%q' "$name" "$value")"
  if [[ -n "$env_file" ]]; then
    echo "$export_line" >> "$env_file"
  fi
}

fetch_runtime() {
  local platform="$1" os_name="$2" arch="$3" format="$4" executable="$5"
  local directory="$dest/$platform" archive="$dest/cache/$platform.$format"
  local existing="" url
  if [[ -d "$directory" ]]; then
    existing="$(find "$directory" -type f -path "*/bin/$executable" -print -quit)"
  fi
  if [[ "$missing_only" == true && -n "$existing" ]]; then
    echo "Reusing runtime: $platform"
  else
    url="$(liberica_url "$os_name" "$arch" "$format")"
    download "Liberica JDK 21 Full for $platform" "$url" "$archive"
    if [[ "$format" == zip ]]; then
      extract_zip "$archive" "$directory"
    else
      extract_tar_gz "$archive" "$directory"
    fi
  fi
  runtime_home="$(find_java_home "$directory" "$executable")"
}

fetch_runtime windows-x64 windows x86 zip java.exe
windows_home="$runtime_home"
fetch_runtime windows-arm64 windows arm zip java.exe
windows_arm64_home="$runtime_home"
fetch_runtime macos-aarch64 macos arm tar.gz java
macos_home="$runtime_home"
fetch_runtime linux-x64 linux x86 tar.gz java
linux_home="$runtime_home"
fetch_runtime linux-aarch64 linux arm tar.gz java
linux_aarch64_home="$runtime_home"

echo
echo "Runtime homes:"
echo "  windows-x64:    $windows_home"
echo "  windows-arm64:  $windows_arm64_home"
echo "  macos-aarch64:  $macos_home"
echo "  linux-x64:      $linux_home"
echo "  linux-aarch64:  $linux_aarch64_home"

write_env FDSWARM_RUNTIME_WINDOWS_X64 "$windows_home"
write_env FDSWARM_RUNTIME_WINDOWS_ARM64 "$windows_arm64_home"
write_env FDSWARM_RUNTIME_MACOS_AARCH64 "$macos_home"
write_env FDSWARM_RUNTIME_LINUX_X64 "$linux_home"
write_env FDSWARM_RUNTIME_LINUX_AARCH64 "$linux_aarch64_home"

if [[ -n "$env_file" ]]; then
  echo
  echo "Wrote optional runtime overrides to $env_file"
fi
