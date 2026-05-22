#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"
version_file="version.txt"
build_number_file="build.number"
mill_output_dir="${MILL_OUTPUT_DIR:-/private/tmp/fdswarm-mill-out}"
assembly_jar="$mill_output_dir/fdswarm/assembly.dest/fdswarm.jar"

cd "$repo_dir"

die() {
  echo "release-jar: $*" >&2
  exit 1
}

fail_after_release_files_changed() {
  restore_release_files
  die "$*"
}

confirm() {
  local prompt="$1"
  local answer
  local answer_lc

  read -r -p "$prompt [y/N]: " answer
  answer_lc="$(printf '%s' "$answer" | tr '[:upper:]' '[:lower:]')"
  case "$answer_lc" in
    y|yes|ok)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

git_status="$(git status --porcelain)"
if [[ -n "$git_status" ]]; then
  die "git working tree must be clean before release-jar runs.

Changes:
$git_status"
fi

[[ -f "$version_file" ]] || die "missing $version_file"

snapshot_version="$(<"$version_file")"
snapshot_version="${snapshot_version//$'\r'/}"
snapshot_version="${snapshot_version//$'\n'/}"

[[ "$snapshot_version" == *-SNAPSHOT ]] ||
  die "$version_file must end with -SNAPSHOT, found: $snapshot_version"

if [[ -f "$build_number_file" ]]; then
  original_build_number_exists=true
  build_number="$(<"$build_number_file")"
  build_number="${build_number//$'\r'/}"
  build_number="${build_number//$'\n'/}"
  [[ -n "$build_number" ]] || build_number="0"
else
  original_build_number_exists=false
  build_number="0"
fi

[[ "$build_number" =~ ^[0-9]+$ ]] ||
  die "$build_number_file must contain an integer, found: $build_number"

next_build_number=$((build_number + 1))
release_version="${snapshot_version%-SNAPSHOT}-$next_build_number"
release_files_restored=false

restore_release_files() {
  if [[ "$release_files_restored" == false ]]; then
    printf '%s\n' "$snapshot_version" > "$version_file"
    if [[ "$original_build_number_exists" == true ]]; then
      printf '%s\n' "$build_number" > "$build_number_file"
    else
      rm -f "$build_number_file"
    fi
    release_files_restored=true
    echo "Restored $version_file to $snapshot_version"
  fi
}

trap restore_release_files ERR INT TERM

printf '%s\n' "$next_build_number" > "$build_number_file"
printf '%s\n' "$release_version" > "$version_file"

echo "Prepared release version: $release_version"
if ! confirm "Is $version_file correct"; then
  restore_release_files
  die "release aborted because $version_file was not confirmed"
fi

echo "Building fdswarm.jar..."
MILL_OUTPUT_DIR="$mill_output_dir" ./mill --no-server clean
MILL_OUTPUT_DIR="$mill_output_dir" ./mill --no-server fdswarm.assembly
[[ -f "$assembly_jar" ]] ||
  fail_after_release_files_changed "assembly completed but did not create expected jar: $assembly_jar"

printf '%s\n' "$snapshot_version" > "$version_file"
release_files_restored=true
echo "Restored $version_file to $snapshot_version"
trap - ERR INT TERM

git add "$version_file" "$build_number_file"
git commit -m "Release build $next_build_number"

if confirm "Push release commit"; then
  git push
else
  echo "Push skipped."
fi

echo "Release jar: $assembly_jar"
