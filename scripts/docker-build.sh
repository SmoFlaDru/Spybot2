#!/usr/bin/env bash
# Builds the Docker images with `docker compose build`, also from a git worktree.
#
# The web image reads the commit and the git history (for the changelog page) at build time. In a
# normal checkout the Dockerfile copies .git itself. In a git worktree .git is just a pointer file
# to a directory outside the build context, so the build would see no repository. For a worktree
# this script makes a throwaway local clone of the checked-out commit and hands its .git to the
# build as the named build context "gitdir".
#
# Usage: scripts/docker-build.sh [docker compose build arguments, e.g. spybot-web]
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -f .git ]; then
    exec docker compose build "$@"
fi

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

git clone --quiet --no-checkout --no-hardlinks . "$tmp/repo"
printf 'services:\n  spybot-web:\n    build:\n      additional_contexts:\n        gitdir: %s\n' \
    "$tmp/repo/.git" > "$tmp/gitdir.yml"

files=(-f docker-compose.yml)
if [ -f docker-compose.override.yml ]; then
    files+=(-f docker-compose.override.yml)
fi
docker compose "${files[@]}" -f "$tmp/gitdir.yml" build "$@"
