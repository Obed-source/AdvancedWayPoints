#!/usr/bin/env bash
# Decides whether a merged pull request makes a release and which version it gets.
#
# Input (environment):
#   CURRENT   last released version, e.g. 1.4.2
#   TITLE     pull request title
#   BODY      pull request description
#   LABELS    pull request labels, one per line
#   FILES     changed files, one per line
#
# Output (stdout, GitHub Actions format):
#   release=true|false
#   reason=<why not>          (only when release=false)
#   bump=major|minor|patch
#   version=<next version>
#
# Rules, first match wins:
#   label "no-release"                               -> no release
#   no file under src/, build.gradle, settings.gradle,
#   gradle/ or gradlew changed                       -> no release (docs, CI...)
#   label "release:major|minor|patch"                -> that bump
#   title "type!: ..." or "BREAKING CHANGE" in body  -> major
#   title "feat: ..." / "feat(scope): ..."           -> minor
#   anything else (fix, perf, refactor, chore...)    -> patch
set -euo pipefail

has_label() { grep -qxF "$1" <<<"${LABELS:-}"; }

if has_label "no-release"; then
  echo "release=false"
  echo "reason=label no-release"
  exit 0
fi

if ! grep -qE '^(src/|build\.gradle$|settings\.gradle$|gradle/|gradlew)' <<<"${FILES:-}"; then
  echo "release=false"
  echo "reason=no plugin code changed"
  exit 0
fi

title_lower=$(tr '[:upper:]' '[:lower:]' <<<"${TITLE:-}")
if has_label "release:major"; then
  bump=major
elif has_label "release:minor"; then
  bump=minor
elif has_label "release:patch"; then
  bump=patch
elif grep -qE '^[a-z]+(\([^)]*\))?!:' <<<"$title_lower" || grep -q 'BREAKING CHANGE' <<<"${BODY:-}"; then
  bump=major
elif grep -qE '^feat(\([^)]*\))?:' <<<"$title_lower"; then
  bump=minor
else
  bump=patch
fi

IFS=. read -r major minor patch <<<"${CURRENT%%[-+]*}"
major=${major:-0}; minor=${minor:-0}; patch=${patch:-0}
case "$bump" in
  major) major=$((major + 1)); minor=0; patch=0 ;;
  minor) minor=$((minor + 1)); patch=0 ;;
  patch) patch=$((patch + 1)) ;;
esac

echo "release=true"
echo "bump=$bump"
echo "version=$major.$minor.$patch"
