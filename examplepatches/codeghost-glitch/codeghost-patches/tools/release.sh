#!/usr/bin/env bash
# Cuts a release that Morphe Manager can consume.
#
#   tools/release.sh <version> [notes-file]
#   NOTES_ONLY=1 tools/release.sh <version>     # print the notes, change nothing
#
# Release notes follow the layout the Morphe patches repo gets from
# semantic-release (conventionalcommits preset): a "## [x.y.z](compare) (date)"
# header plus "### ✨ New Features" / "### 🔧 Improvements" / "### 🐛 Bug Fixes" /
# "### 🚀 Updated App Support" sections, built from the commits since the last
# tag. Commits must use conventional messages (feat:, fix:, perf:, bump:,
# chore:); anything else is hidden, exactly like upstream. Pass a notes file to
# override.
#
# Morphe Manager reads patches-bundle.json from the default branch and downloads
# the .mpp named by its download_url, so the version in gradle.properties, the
# release tag/asset and patches-bundle.json must always agree. Order matters:
# the asset is published before the JSON that points at it.
set -euo pipefail

VERSION="${1:?usage: tools/release.sh <version> [notes-file]}"
NOTES_FILE="${2:-}"

cd "$(dirname "$0")/.."
REPO="$(gh repo view --json nameWithOwner --jq .nameWithOwner)"
ASSET="patches-$VERSION.mpp"
# Releases are created through gh, which only tags on the remote.
git fetch --tags --quiet origin || true
PREV="$(git describe --tags --abbrev=0 2>/dev/null || true)"
RANGE="${PREV:+$PREV..}HEAD"

declare -A SECTIONS=(
  [feat]="✨ New Features"
  [perf]="🔧 Improvements"
  [fix]="🐛 Bug Fixes"
  [bump]="🚀 Updated App Support"
)
ORDER=(feat perf fix bump)

generate_notes() {
  if [[ -n "$PREV" ]]; then
    printf '## [%s](https://github.com/%s/compare/%s...v%s) (%s)\n' \
      "$VERSION" "$REPO" "$PREV" "$VERSION" "$(date -u +%F)"
  else
    printf '## %s (%s)\n' "$VERSION" "$(date -u +%F)"
  fi

  local breaking="" lines=() hash subject kind scope bang msg short entry type
  while IFS=$'\t' read -r hash subject; do
    # Only "type(scope)!: subject" counts; anything else stays out of the notes.
    [[ "$subject" == *": "* ]] || continue
    kind="${subject%%: *}"
    msg="${subject#*: }"
    bang=""
    if [[ "$kind" == *"!" ]]; then
      bang=1
      kind="${kind%!}"
    fi
    scope=""
    if [[ "$kind" == *"("*")" ]]; then
      scope="${kind#*(}"
      scope="${scope%)}"
      kind="${kind%%(*}"
    fi
    type="${kind,,}"
    [[ -n "${SECTIONS[$type]:-}" ]] || continue
    short="${hash:0:7}"
    entry="* "
    [[ -n "$scope" ]] && entry+="**$scope:** "
    entry+="$msg ([$short](https://github.com/$REPO/commit/$hash))"
    lines+=("$type"$'\t'"$entry")
    [[ -n "$bang" ]] && breaking+="* $msg"$'\n'
  done < <(git log --format='%H%x09%s' "$RANGE")

  if [[ -n "$breaking" ]]; then
    printf '\n### ⚠ BREAKING CHANGES\n\n%s' "$breaking"
  fi

  local section body
  for section in "${ORDER[@]}"; do
    body=""
    for entry in "${lines[@]}"; do
      [[ "${entry%%$'\t'*}" == "$section" ]] && body+="${entry#*$'\t'}"$'\n'
    done
    if [[ -n "$body" ]]; then
      printf '\n### %s\n\n%s' "${SECTIONS[$section]}" "$body"
    fi
  done
}

if [[ -n "$NOTES_FILE" ]]; then
  NOTES="$(cat "$NOTES_FILE")"
else
  NOTES="$(generate_notes)"
fi

if [[ "${NOTES_ONLY:-}" == "1" ]]; then
  printf '%s\n' "$NOTES"
  exit 0
fi

sed -i "s/^version = .*/version = $VERSION/" gradle.properties
./gradlew :patches:buildAndroid -q -Pgpr.user=local -Pgpr.key=local

NOTES="$NOTES" VERSION="$VERSION" REPO="$REPO" ASSET="$ASSET" python3 - <<'PY'
import datetime, json, os

env = os.environ
bundle = {
    "created_at": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S"),
    "description": env["NOTES"],
    "download_url": f"https://github.com/{env['REPO']}/releases/download/v{env['VERSION']}/{env['ASSET']}",
    "signature_download_url": "",
    "version": env["VERSION"],
}
with open("patches-bundle.json", "w") as handle:
    json.dump(bundle, handle, indent=2, ensure_ascii=False)
    handle.write("\n")

try:
    with open("CHANGELOG.md") as handle:
        previous = handle.read()
except FileNotFoundError:
    previous = ""
with open("CHANGELOG.md", "w") as handle:
    handle.write(env["NOTES"].rstrip() + "\n\n" + previous)
PY

git add gradle.properties patches-bundle.json CHANGELOG.md
git commit -q -m "chore: release v$VERSION"
git push -q origin HEAD

# The tag is created here, not by `gh release create --target main`: the target is
# the remote branch, which does not contain this release commit yet, so the tag
# would land on the previous commit.
git tag -f "v$VERSION" HEAD
git push -q -f origin "v$VERSION"

gh release create "v$VERSION" "patches/build/libs/$ASSET" \
  --verify-tag --title "v$VERSION" --notes "$NOTES"
echo "released v$VERSION -> https://github.com/$REPO/releases/tag/v$VERSION"
