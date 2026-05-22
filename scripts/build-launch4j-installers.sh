#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"

app_name="FdSwarm"
main_class="fdswarm.FdSwarm"
vendor="FdSwarm"
assembly_jar="${FDSWARM_ASSEMBLY_JAR:-$repo_dir/out/fdswarm/assembly.dest/fdswarm.jar}"
installer_version="${FDSWARM_VERSION:-0.0.0}"
installer_version="${installer_version#v}"
build_number="${FDSWARM_BUILD:-0}"
work_dir="$repo_dir/out/fdswarm/launch4j.dest"
artifacts_dir="$repo_dir/release/artifacts"
publish_dir="${FDSWARM_LAUNCH4J_DIR:-/Library/WebServer/Documents/fdswarm/launch4j}"
launch4j_version="${LAUNCH4J_VERSION:-3.50}"

case "$(uname -s)" in
  Darwin)
    launch4j_archive_name="launch4j-$launch4j_version-macosx-x86.tgz"
    ;;
  Linux)
    launch4j_archive_name="launch4j-$launch4j_version-linux-x64.tgz"
    ;;
  *)
    launch4j_archive_name="launch4j-$launch4j_version-win32.zip"
    ;;
esac

launch4j_download_url="${LAUNCH4J_DOWNLOAD_URL:-https://sourceforge.net/projects/launch4j/files/launch4j-3/$launch4j_version/$launch4j_archive_name/download}"

die() {
  echo "error: $*" >&2
  exit 1
}

require_command() {
  local cmd="$1"
  command -v "$cmd" >/dev/null 2>&1 || die "Required command not found: $cmd"
}

xml_escape() {
  local value="$1"
  value="${value//&/&amp;}"
  value="${value//</&lt;}"
  value="${value//>/&gt;}"
  value="${value//\"/&quot;}"
  value="${value//\'/&apos;}"
  printf '%s' "$value"
}

find_launch4j_executable() {
  local root_dir="$1"

  [[ -d "$root_dir" ]] || return 1

  local candidates=(
    "$root_dir/launch4jc"
    "$root_dir/launch4jc.exe"
    "$root_dir/launch4j"
    "$root_dir/launch4j.exe"
    "$root_dir/bin/launch4jc"
    "$root_dir/bin/launch4jc.exe"
    "$root_dir/bin/launch4j"
    "$root_dir/bin/launch4j.exe"
  )

  local candidate
  for candidate in "${candidates[@]}"; do
    if [[ -f "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done

  local nested
  nested="$(find "$root_dir" -type f \( -name launch4jc -o -name launch4jc.exe \) -print -quit)"
  if [[ -n "$nested" ]]; then
    printf '%s\n' "$nested"
    return 0
  fi

  find "$root_dir" -type f \( -name launch4j -o -name launch4j.exe \) -print -quit
}

normalize_launch4j_scripts() {
  local root_dir="$1"
  local script_path

  while IFS= read -r script_path; do
    perl -pi -e 's/\r$//' "$script_path"
    chmod +x "$script_path" || true
  done < <(find "$root_dir" -type f \( -name launch4jc -o -name launch4j \) -print)
}

download_launch4j_archive() {
  local url="$1"
  local archive_path="$2"

  require_command curl
  echo "Launch4j not found; downloading $launch4j_version to $archive_path" >&2
  curl -fL --retry 3 --output "$archive_path" "$url"
}

install_local_launch4j() {
  local tools_dir="$work_dir/tools"
  local install_dir="$tools_dir/launch4j-$launch4j_version"
  local archive_path="$tools_dir/$launch4j_archive_name"
  local existing

  existing="$(find_launch4j_executable "$install_dir" || true)"
  if [[ -n "$existing" ]]; then
    normalize_launch4j_scripts "$install_dir"
    existing="$(find_launch4j_executable "$install_dir" || true)"
    printf '%s\n' "$existing"
    return 0
  fi

  mkdir -p "$tools_dir"
  download_launch4j_archive "$launch4j_download_url" "$archive_path"

  rm -rf "$install_dir"
  mkdir -p "$install_dir"
  case "$archive_path" in
    *.zip)
      require_command unzip
      unzip -q "$archive_path" -d "$install_dir"
      ;;
    *.tgz|*.tar.gz)
      require_command tar
      tar -xzf "$archive_path" -C "$install_dir"
      ;;
    *)
      die "Unsupported Launch4j archive type: $archive_path"
      ;;
  esac

  normalize_launch4j_scripts "$install_dir"

  local installed
  installed="$(find_launch4j_executable "$install_dir" || true)"
  [[ -n "$installed" ]] || die "Downloaded Launch4j but no launcher executable was found under: $install_dir"

  chmod +x "$installed" || true
  printf '%s\n' "$installed"
}

resolve_launch4j_command() {
  if [[ -n "${LAUNCH4J:-}" ]]; then
    if [[ -f "$LAUNCH4J" || -x "$LAUNCH4J" ]]; then
      printf '%s\n' "$LAUNCH4J"
      return 0
    fi

    command -v "$LAUNCH4J" >/dev/null 2>&1 || die "LAUNCH4J is set but was not found: $LAUNCH4J"
    command -v "$LAUNCH4J"
    return 0
  fi

  if [[ -n "${LAUNCH4J_HOME:-}" ]]; then
    local home_executable
    home_executable="$(find_launch4j_executable "$LAUNCH4J_HOME" || true)"
    [[ -n "$home_executable" ]] || die "LAUNCH4J_HOME is set but no Launch4j executable was found under: $LAUNCH4J_HOME"
    printf '%s\n' "$home_executable"
    return 0
  fi

  if command -v launch4jc >/dev/null 2>&1; then
    command -v launch4jc
    return 0
  fi

  local candidate_dir candidate_executable
  for candidate_dir in /Applications/Launch4j /opt/homebrew/opt/launch4j /usr/local/opt/launch4j /opt/local/libexec/launch4j; do
    candidate_executable="$(find_launch4j_executable "$candidate_dir" || true)"
    if [[ -n "$candidate_executable" ]]; then
      printf '%s\n' "$candidate_executable"
      return 0
    fi
  done

  install_local_launch4j
}

runtime_base_dirs() {
  if [[ -n "${FDSWARM_RUNTIMES_DIR:-}" ]]; then
    printf '%s\n' "$FDSWARM_RUNTIMES_DIR"
  fi

  printf '%s\n' \
    "$repo_dir/fdswarm-runtimes" \
    "$repo_dir/runtimes" \
    "$repo_dir/runtimes/windows"
}

resolve_windows_runtime_image() {
  local runtime_id="$1"
  local env_name="$2"
  local env_value="${!env_name:-}"

  if [[ -n "$env_value" ]]; then
    [[ -d "$env_value" ]] || die "$env_name does not point to a directory: $env_value"
    [[ -f "$env_value/bin/java.exe" ]] || die "$env_name does not contain bin/java.exe: $env_value"
    [[ -f "$env_value/release" ]] || die "$env_name does not contain a release file: $env_value"
    printf '%s\n' "$env_value"
    return 0
  fi

  local base runtime_dir java_exe match
  while IFS= read -r base; do
    [[ -d "$base" ]] || continue
    runtime_dir="$base/$runtime_id"
    [[ -d "$runtime_dir" ]] || continue

    java_exe="$(find "$runtime_dir" -type f -path '*/bin/java.exe' -print -quit)"
    match="${java_exe%/bin/java.exe}"
    if [[ -n "$match" && -f "$match/release" ]]; then
      printf '%s\n' "$match"
      return 0
    fi
  done < <(runtime_base_dirs)

  die "No runtime image found for $runtime_id. Run scripts/fetch-liberica-runtimes.sh or set $env_name."
}

write_launch4j_config() {
  local config_path="$1"
  local output_exe="$2"
  local jar_path="$3"
  local header_type="$4"
  local icon_path="${5:-}"
  local icon_element=""

  if [[ -n "$icon_path" && -f "$icon_path" ]]; then
    icon_element="  <icon>$(xml_escape "$icon_path")</icon>"
  fi

  IFS=. read -r major_version minor_version patch_version <<< "$installer_version"
  local file_version="$major_version.$minor_version.$patch_version.$build_number"
  local artifact_version="$installer_version"
  if [[ "$build_number" != "0" ]]; then
    artifact_version="$installer_version-build$build_number"
  fi

  cat > "$config_path" <<EOF
<launch4jConfig>
  <dontWrapJar>false</dontWrapJar>
  <headerType>$(xml_escape "$header_type")</headerType>
  <jar>$(xml_escape "$jar_path")</jar>
  <classPath>
    <mainClass>$(xml_escape "$main_class")</mainClass>
  </classPath>
  <outfile>$(xml_escape "$output_exe")</outfile>
  <errTitle>$(xml_escape "$app_name")</errTitle>
  <chdir>.</chdir>
  <priority>normal</priority>
  <downloadUrl>https://adoptium.net/</downloadUrl>
  <supportUrl>https://github.com/dicklieber/fdswarm</supportUrl>
  <stayAlive>false</stayAlive>
  <restartOnCrash>false</restartOnCrash>
$icon_element
  <jre>
    <path>runtime</path>
    <requiresJdk>false</requiresJdk>
    <requires64Bit>false</requires64Bit>
    <minVersion>21</minVersion>
  </jre>
  <versionInfo>
    <fileVersion>$(xml_escape "$file_version")</fileVersion>
    <txtFileVersion>$(xml_escape "$artifact_version")</txtFileVersion>
    <fileDescription>$(xml_escape "$app_name")</fileDescription>
    <copyright>$(xml_escape "$vendor")</copyright>
    <productVersion>$(xml_escape "$file_version")</productVersion>
    <txtProductVersion>$(xml_escape "$artifact_version")</txtProductVersion>
    <productName>$(xml_escape "$app_name")</productName>
    <companyName>$(xml_escape "$vendor")</companyName>
    <internalName>$(xml_escape "$app_name")</internalName>
    <originalFilename>$(xml_escape "$(basename "$output_exe")")</originalFilename>
  </versionInfo>
</launch4jConfig>
EOF
}

require_command find
require_command jar

[[ "$installer_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "FDSWARM_VERSION must be numeric major.minor.patch, got: $installer_version"
[[ "$build_number" =~ ^[0-9]+$ ]] || die "FDSWARM_BUILD must be numeric, got: $build_number"
[[ -f "$assembly_jar" ]] || die "Assembly JAR not found: $assembly_jar"

assembly_jar="$(cd "$(dirname "$assembly_jar")" && pwd)/$(basename "$assembly_jar")"
main_class_entry="${main_class//.//}.class"
jar tf "$assembly_jar" | grep -Fxq "$main_class_entry" || die "Assembly JAR does not contain main class $main_class ($main_class_entry): $assembly_jar"

windows_x64_runtime="$(resolve_windows_runtime_image windows-x64 FDSWARM_RUNTIME_WINDOWS_X64)"
windows_arm64_runtime="$(resolve_windows_runtime_image windows-arm64 FDSWARM_RUNTIME_WINDOWS_ARM64)"
launch4j="$(resolve_launch4j_command)"

artifact_version="$installer_version"
if [[ "$build_number" != "0" ]]; then
  artifact_version="$installer_version-build$build_number"
fi

mkdir -p "$work_dir" "$artifacts_dir"
mkdir -p "$publish_dir"

existing_publish_count="$(find "$publish_dir" -mindepth 1 -maxdepth 1 -print | wc -l | tr -d ' ')"
echo "Clearing $existing_publish_count existing files from $publish_dir"
find "$publish_dir" -mindepth 1 -maxdepth 1 -exec rm -rf {} +

echo "Building Launch4j installers"
echo "Repo: $repo_dir"
echo "JAR: $assembly_jar"
echo "Version: $installer_version"
echo "Build: $build_number"
echo "Launch4j: $launch4j"
echo "Publish: $publish_dir"

package_runtime() {
  local runtime_id="$1"
  local runtime_path="$2"
  local runtime_work_dir="$work_dir/$runtime_id"
  local stage_root="$runtime_work_dir/stage"
  local stage_dir="$stage_root/$app_name"
  local runtime_dest="$stage_dir/runtime"
  local gui_exe="$stage_dir/$app_name.exe"
  local console_exe="$stage_dir/${app_name}Console.exe"
  local gui_config="$runtime_work_dir/$app_name.xml"
  local console_config="$runtime_work_dir/${app_name}Console.xml"
  local zip_path="$runtime_work_dir/$app_name-$artifact_version-$runtime_id-launch4j.zip"
  local artifact="$artifacts_dir/$app_name-$artifact_version-$runtime_id-launch4j.zip"
  local published_artifact="$publish_dir/$(basename "$artifact")"
  local icon_path="$repo_dir/fdswarm/resources/icons/icon.ico"

  [[ -d "$runtime_path" ]] || die "Runtime image not found: $runtime_path"

  rm -rf "$stage_root" "$gui_config" "$console_config" "$zip_path"
  mkdir -p "$stage_dir"
  cp -R "$runtime_path" "$runtime_dest"

  if [[ -f "$icon_path" ]]; then
    write_launch4j_config "$gui_config" "$gui_exe" "$assembly_jar" gui "$icon_path"
    write_launch4j_config "$console_config" "$console_exe" "$assembly_jar" console "$icon_path"
  else
    write_launch4j_config "$gui_config" "$gui_exe" "$assembly_jar" gui
    write_launch4j_config "$console_config" "$console_exe" "$assembly_jar" console
  fi

  echo
  echo "Packaging $runtime_id from $runtime_path"
  "$launch4j" "$gui_config"
  "$launch4j" "$console_config"

  if command -v ditto >/dev/null 2>&1; then
    ditto -c -k --keepParent "$stage_dir" "$zip_path"
  else
    require_command zip
    (cd "$stage_root" && zip -qr "$zip_path" "$app_name")
  fi

  mv -f "$zip_path" "$artifact"
  echo "Launch4j: $artifact"

  cp -f "$artifact" "$published_artifact"
  echo "Copied: $published_artifact"
}

package_runtime windows-x64 "$windows_x64_runtime"
package_runtime windows-arm64 "$windows_arm64_runtime"
