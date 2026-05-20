#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd -- "$script_dir/.." && pwd)"

app_name="FdSwarm"
main_class="fdswarm.FdSwarm"
assembly_jar="${FDSWARM_ASSEMBLY_JAR:-$repo_dir/out/fdswarm/assembly.dest/fdswarm.jar}"
installer_version="${FDSWARM_VERSION:-0.0.0}"
build_number="${FDSWARM_BUILD:-0}"
jpackage_bin="${JPACKAGE:-jpackage}"
work_dir="$repo_dir/out/fdswarm/msi.dest"
artifacts_dir="$repo_dir/release/artifacts"
win_upgrade_uuid="8F095DE2-D316-43A7-94C3-7702217CAE1D"

runtime_ids=(
  "windows-x64"
  "windows-arm64"
)

runtime_paths=(
  "$repo_dir/release/jdks/windows-x64/runtime"
  "$repo_dir/release/jdks/windows-arm64/runtime"
)

usage() {
  cat <<EOF
Usage: $(basename "$0")

Build one FdSwarm MSI installer for each configured runtime image.

Environment:
  FDSWARM_ASSEMBLY_JAR  Jar to package. Defaults to out/fdswarm/assembly.dest/fdswarm.jar
  FDSWARM_VERSION       Numeric installer version. Defaults to 0.0.0
  FDSWARM_BUILD         Optional numeric build number for output filenames. Defaults to 0
  JPACKAGE              jpackage executable. Defaults to jpackage on PATH

Note: MSI packaging must be run with a jpackage installation that supports
Windows MSI output, with WiX available on PATH.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ ! "$installer_version" =~ ^[0-9]+[.][0-9]+[.][0-9]+$ ]]; then
  echo "FDSWARM_VERSION must be numeric major.minor.patch, got: $installer_version" >&2
  exit 1
fi

if [[ ! "$build_number" =~ ^[0-9]+$ ]]; then
  echo "FDSWARM_BUILD must be numeric, got: $build_number" >&2
  exit 1
fi

if [[ ! -f "$assembly_jar" ]]; then
  echo "Assembly JAR not found: $assembly_jar" >&2
  exit 1
fi

if ! command -v "$jpackage_bin" >/dev/null 2>&1; then
  echo "jpackage not found: $jpackage_bin" >&2
  exit 1
fi

artifact_version="$installer_version"
if [[ "$build_number" != "0" ]]; then
  artifact_version="$installer_version-build$build_number"
fi

mkdir -p "$work_dir" "$artifacts_dir"

echo "Building MSI installers"
echo "Repo: $repo_dir"
echo "JAR: $assembly_jar"
echo "Version: $installer_version"
echo "Build: $build_number"
echo "jpackage: $jpackage_bin"

for i in "${!runtime_ids[@]}"; do
  runtime_id="${runtime_ids[$i]}"
  runtime_path="${runtime_paths[$i]}"
  input_dir="$work_dir/$runtime_id/input"
  dest_dir="$work_dir/$runtime_id/jpackage"
  console_launcher="$work_dir/$runtime_id/FdSwarmConsole.properties"

  if [[ ! -d "$runtime_path" ]]; then
    echo "Runtime image not found: $runtime_path" >&2
    exit 1
  fi

  rm -rf "$input_dir" "$dest_dir"
  mkdir -p "$input_dir" "$dest_dir"
  cp "$assembly_jar" "$input_dir/fdswarm.jar"

  cat > "$console_launcher" <<EOF
main-jar=fdswarm.jar
main-class=$main_class
win-console=true
EOF

  args=(
    --type msi
    --name "$app_name"
    --app-version "$installer_version"
    --dest "$dest_dir"
    --input "$input_dir"
    --main-jar "fdswarm.jar"
    --main-class "$main_class"
    --runtime-image "$runtime_path"
    --add-launcher "${app_name}Console=$console_launcher"
    --win-menu
    --win-shortcut
    --win-upgrade-uuid "$win_upgrade_uuid"
  )

  if [[ -f "$repo_dir/fdswarm/resources/icons/icon.ico" ]]; then
    args+=(--icon "$repo_dir/fdswarm/resources/icons/icon.ico")
  fi

  echo
  echo "Packaging $runtime_id from $runtime_path"
  "$jpackage_bin" "${args[@]}"

  produced_msi="$(find "$dest_dir" -maxdepth 1 -type f -name '*.msi' -print | sort | tail -n 1)"
  if [[ -z "$produced_msi" ]]; then
    echo "jpackage completed but no MSI was found in $dest_dir" >&2
    exit 1
  fi

  artifact="$artifacts_dir/${app_name}-${artifact_version}-${runtime_id}.msi"
  mv "$produced_msi" "$artifact"
  echo "MSI: $artifact"
done
