#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"
version_file="version.txt"
build_number_file="build.number"
mill_output_dir="${MILL_OUTPUT_DIR:-}"

cd "$repo_dir"

die() {
  echo "release-jar: $*" >&2
  exit 1
}

require_command() {
  local command_name="$1"
  command -v "$command_name" >/dev/null 2>&1 ||
    die "required command not found: $command_name"
}

fail_after_release_files_changed() {
  restore_release_files
  die "$*"
}

run_mill() {
  if [[ -n "$mill_output_dir" ]]; then
    MILL_OUTPUT_DIR="$mill_output_dir" ./mill --no-server "$@"
  else
    ./mill --no-server "$@"
  fi
}

find_assembly_jar() {
  local assembly_dir
  local jar_path

  if [[ -n "$mill_output_dir" ]]; then
    assembly_dir="$mill_output_dir/fdswarm/assembly.dest"
  else
    assembly_dir="$repo_dir/out/fdswarm/assembly.dest"
  fi

  [[ -d "$assembly_dir" ]] ||
    fail_after_release_files_changed "assembly completed but did not create expected directory: $assembly_dir"

  jar_path="$assembly_dir/fdswarm.jar"
  if [[ -f "$jar_path" ]]; then
    printf '%s\n' "$jar_path"
    return 0
  fi

  jar_path="$(find "$assembly_dir" -maxdepth 1 -type f -name '*.jar' -print | sort | tail -n 1)"
  [[ -n "$jar_path" ]] ||
    fail_after_release_files_changed "assembly completed but did not create a jar in: $assembly_dir"

  printf '%s\n' "$jar_path"
}

extract_implementation_version() {
  local jar_path="$1"

  { unzip -p "$jar_path" META-INF/MANIFEST.MF 2>/dev/null || true; } |
    tr -d '\r' |
    awk -F': ' '/^Implementation-Version: / { print $2; exit }'
}

publish_release_jar() {
  local jar_path="$1"
  local target_ref="$2"
  local jar_version
  local tag

  require_command gh
  require_command unzip

  jar_version="$(extract_implementation_version "$jar_path")"
  [[ -n "$jar_version" ]] ||
    die "could not extract Implementation-Version from $jar_path"
  [[ "$jar_version" != *-SNAPSHOT ]] ||
    die "refusing to publish snapshot jar version: $jar_version"

  tag="v$jar_version"

  echo "Publishing fdswarm.jar to GitHub release $tag..."
  gh auth status >/dev/null

  if gh release view "$tag" >/dev/null 2>&1; then
    echo "GitHub release exists: $tag"
  else
    gh release create "$tag" --target "$target_ref" --title "$tag" --generate-notes
    echo "Created GitHub release: $tag"
  fi

  gh release upload "$tag" "$jar_path" --clobber
  echo "Published fdswarm.jar to GitHub release $tag"
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
run_mill clean
run_mill fdswarm.assembly
assembly_jar="$(find_assembly_jar)"
./scripts/ci/verify-docs-in-jar.sh "$assembly_jar"

printf '%s\n' "$snapshot_version" > "$version_file"
release_files_restored=true
echo "Restored $version_file to $snapshot_version"
trap - ERR INT TERM

git add "$version_file" "$build_number_file"
git commit -m "Release build $next_build_number"
release_commit="$(git rev-parse HEAD)"

if confirm "Push release commit"; then
  git push
else
  echo "Push skipped."
fi

publish_release_jar "$assembly_jar" "$release_commit"

echo "Release jar: $assembly_jar"
