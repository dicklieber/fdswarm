#!/usr/bin/env bash
#
# Prepare a release by updating from origin/main, creating a version tag,
# and pushing the branch and tag. The GitHub Actions workflow performs the
# actual build and GitHub Release creation after the tag is pushed.

set -euo pipefail

REMOTE="${REMOTE:-origin}"
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

latest_release_tag() {
  git tag --sort=-v:refname |
    grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' |
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

  if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
    die "tag already exists locally: $tag"
  fi

  if git ls-remote --exit-code --tags "$REMOTE" "refs/tags/$tag" >/dev/null 2>&1; then
    die "tag already exists on $REMOTE: $tag"
  fi
}

current_branch="$(git branch --show-current)"
[[ "$current_branch" == "$BRANCH" ]] ||
  die "release must be run from the $BRANCH branch. Current branch: ${current_branch:-detached HEAD}"

echo "Checking for a clean worktree..."
require_clean_worktree

echo "Fetching $REMOTE/$BRANCH and tags..."
git fetch "$REMOTE" "$BRANCH" --tags

echo "Updating local $BRANCH from $REMOTE/$BRANCH..."
git pull --ff-only "$REMOTE" "$BRANCH"

echo "Rechecking for a clean worktree after update..."
require_clean_worktree

latest_tag="$(latest_release_tag || true)"
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
echo "This will push $BRANCH and $release_tag to $REMOTE."
read -r -p "Continue? [y/N] " answer
[[ "$answer" =~ ^[Yy]$ ]] || die "aborted"

git tag -a "$release_tag" -m "Release $release_tag"
git push "$REMOTE" "$BRANCH" "$release_tag"

echo "Pushed $release_tag. GitHub Actions will build packages and create the release."
