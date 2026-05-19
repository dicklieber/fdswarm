#!/usr/bin/env bash
#
# Prepare a release by updating main, creating a version tag on GitHub,
# and letting the GitHub Actions workflow perform the actual build and
# GitHub Release creation after the tag is created.

set -euo pipefail

BRANCH="${BRANCH:-main}"

die() {
  echo "release: $*" >&2
  exit 1
}

require_clean_worktree() {
  if ! git diff --quiet || ! git diff --cached --quiet; then
    die "working tree is not clean. Commit, stash, or discard local changes first."
  fi

  if [[ -n "$(git ls-files --others --exclude-standard)" ]]; then
    die "working tree has untracked files. Commit, stash, or remove them first."
  fi
}

require_gh() {
  command -v gh >/dev/null ||
    die "GitHub CLI is required. Install gh and authenticate with 'gh auth login'."

  gh auth status >/dev/null ||
    die "GitHub CLI is not authenticated. Run 'gh auth login' first."
}

latest_release_tag() {
  gh api --paginate repos/{owner}/{repo}/tags --jq '.[].name' |
    grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' |
    sort -t. -k1.2,1n -k2,2n -k3,3n |
    tail -n 1
}

latest_release_tag_or_empty() {
  latest_release_tag ||
    true
}

remote_tag_exists() {
  local tag="$1"

  gh api "repos/{owner}/{repo}/git/ref/tags/$tag" --silent >/dev/null 2>&1
}

create_release_tag() {
  local tag="$1"
  local commit_sha="$2"
  local tag_sha

  tag_sha="$(
    gh api repos/{owner}/{repo}/git/tags \
      -f tag="$tag" \
      -f message="Release $tag" \
      -f object="$commit_sha" \
      -f type=commit \
      --jq .sha
  )"

  gh api repos/{owner}/{repo}/git/refs \
    -f ref="refs/tags/$tag" \
    -f sha="$tag_sha" \
    --silent
}

start_release_workflow() {
  local tag="$1"

  gh workflow run ci.yaml \
    --ref "$tag" \
    -f release_version="$tag"
}

remote_branch_sha() {
  gh api "repos/{owner}/{repo}/git/ref/heads/$BRANCH" --jq .object.sha
}

local_tag_exists() {
  git tag --list "$1" |
    head -n 1
}

next_patch_tag() {
  local latest="$1"
  local version major minor patch

  if [[ -z "$latest" ]]; then
    echo "v0.0.1"
    return
  fi

  version="${latest#v}"
  IFS=. read -r major minor patch <<< "$version"
  echo "v${major}.${minor}.$((patch + 1))"
}

validate_tag() {
  local tag="$1"

  [[ "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] ||
    die "release tags must look like v1.2.3, got: $tag"

  if [[ -n "$(local_tag_exists "$tag")" ]]; then
    die "tag already exists locally: $tag"
  fi

  if remote_tag_exists "$tag"; then
    die "tag already exists on GitHub: $tag"
  fi
}

require_gh

current_branch="$(git branch --show-current)"
[[ "$current_branch" == "$BRANCH" ]] ||
  die "release must be run from the $BRANCH branch. Current branch: ${current_branch:-detached HEAD}"

echo "Checking for a clean worktree..."
require_clean_worktree

local_branch_sha="$(git rev-parse HEAD)"
remote_main_sha="$(remote_branch_sha)"
if [[ "$local_branch_sha" != "$remote_main_sha" ]]; then
  echo "Updating local $BRANCH from GitHub..."
  if ! gh repo sync --branch "$BRANCH"; then
    echo "Warning: could not update local $BRANCH from GitHub. The branch likely has diverging local commits."
    echo "Warning: release will use the current GitHub $BRANCH commit; your local checkout will not be changed."
  else
    echo "Rechecking for a clean worktree after update..."
    require_clean_worktree

    local_branch_sha="$(git rev-parse HEAD)"
    remote_main_sha="$(remote_branch_sha)"
  fi
fi

latest_tag="$(latest_release_tag_or_empty)"
default_tag="$(next_patch_tag "$latest_tag")"

if [[ -n "${1:-}" ]]; then
  release_tag="$1"
else
  echo "Latest release tag: ${latest_tag:-none}"
  read -r -p "Release tag [$default_tag]: " release_tag
  release_tag="${release_tag:-$default_tag}"
fi

validate_tag "$release_tag"

echo
echo "Release tag: $release_tag"
echo "This will create $release_tag on GitHub at the current GitHub $BRANCH commit."
if [[ "$local_branch_sha" != "$remote_main_sha" ]]; then
  echo "Local $BRANCH differs from GitHub $BRANCH."
  echo "Local:  $local_branch_sha"
  echo "GitHub: $remote_main_sha"
fi
read -r -p "Continue? [y/N] " answer
[[ "$answer" =~ ^[Yy]$ ]] || die "aborted"

create_release_tag "$release_tag" "$remote_main_sha"
start_release_workflow "$release_tag"

echo "Created $release_tag on GitHub and started the release workflow."
