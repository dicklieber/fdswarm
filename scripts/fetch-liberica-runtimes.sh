#!/usr/bin/env bash

set -euo pipefail

dest="fdswarm-runtimes"
env_file=""

usage() {
  cat <<'EOF'
Usage:
  scripts/fetch-liberica-runtimes.sh [--dest DIR] [--env-file FILE]

Downloads and unpacks BellSoft Liberica JDK 21 Full runtimes for:
  - Windows x64
  - Windows ARM64
  - macOS aarch64
  - Linux x64
  - Linux aarch64

By default, runtimes are written under fdswarm-runtimes for
scripts/publish-zip-installers.sh.

Options:
  --dest DIR        Directory for downloaded archives and unpacked runtimes.
  --env-file FILE   Write optional shell export overrides to FILE.
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

windows_url="$(liberica_url windows x86 zip)"
windows_archive="$dest/cache/windows-x64.zip"
windows_dir="$dest/windows-x64"
download "Liberica JDK 21 Full for Windows x64" "$windows_url" "$windows_archive"
extract_zip "$windows_archive" "$windows_dir"
windows_home="$(find_java_home "$windows_dir" java.exe)"

windows_arm64_url="$(liberica_url windows arm zip)"
windows_arm64_archive="$dest/cache/windows-arm64.zip"
windows_arm64_dir="$dest/windows-arm64"
download "Liberica JDK 21 Full for Windows ARM64" "$windows_arm64_url" "$windows_arm64_archive"
extract_zip "$windows_arm64_archive" "$windows_arm64_dir"
windows_arm64_home="$(find_java_home "$windows_arm64_dir" java.exe)"

macos_url="$(liberica_url macos arm tar.gz)"
macos_archive="$dest/cache/macos-aarch64.tar.gz"
macos_dir="$dest/macos-aarch64"
download "Liberica JDK 21 Full for macOS aarch64" "$macos_url" "$macos_archive"
extract_tar_gz "$macos_archive" "$macos_dir"
macos_home="$(find_java_home "$macos_dir" java)"

linux_url="$(liberica_url linux x86 tar.gz)"
linux_archive="$dest/cache/linux-x64.tar.gz"
linux_dir="$dest/linux-x64"
download "Liberica JDK 21 Full for Linux x64" "$linux_url" "$linux_archive"
extract_tar_gz "$linux_archive" "$linux_dir"
linux_home="$(find_java_home "$linux_dir" java)"

linux_aarch64_url="$(liberica_url linux arm tar.gz)"
linux_aarch64_archive="$dest/cache/linux-aarch64.tar.gz"
linux_aarch64_dir="$dest/linux-aarch64"
download "Liberica JDK 21 Full for Linux aarch64" "$linux_aarch64_url" "$linux_aarch64_archive"
extract_tar_gz "$linux_aarch64_archive" "$linux_aarch64_dir"
linux_aarch64_home="$(find_java_home "$linux_aarch64_dir" java)"

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
