#!/usr/bin/env bash
# Move the app version forward and keep versionCode in step.
#
#   scripts/bump-version.sh patch     # 1.0.0 -> 1.0.1   (default)
#   scripts/bump-version.sh minor     # 1.0.0 -> 1.1.0
#   scripts/bump-version.sh major     # 1.0.0 -> 2.0.0
#   scripts/bump-version.sh 2.3.1     # explicit
#
# versionCode always increases by one, whatever the name does: Android refuses
# to install an APK whose versionCode is not greater than the installed one.
set -euo pipefail

cd "$(dirname "$0")/.."
file=version.properties

[ -f "$file" ] || { echo "no $file in $(pwd)" >&2; exit 1; }

current_code=$(sed -n 's/^versionCode=//p' "$file")
current_name=$(sed -n 's/^versionName=//p' "$file")
[ -n "$current_code" ] && [ -n "$current_name" ] || {
    echo "malformed $file: expected versionCode and versionName" >&2
    exit 1
}

bump=${1:-patch}
if [[ "$bump" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    next_name=$bump
else
    IFS=. read -r major minor patch <<< "$current_name"
    case "$bump" in
        major) major=$((major + 1)); minor=0; patch=0 ;;
        minor) minor=$((minor + 1)); patch=0 ;;
        patch) patch=$((patch + 1)) ;;
        *) echo "usage: $0 [major|minor|patch|X.Y.Z]" >&2; exit 1 ;;
    esac
    next_name="$major.$minor.$patch"
fi

next_code=$((current_code + 1))

# sed -i is GNU on Termux but BSD on macOS; writing through a temp file works
# on both.
tmp=$(mktemp)
sed -e "s/^versionCode=.*/versionCode=$next_code/" \
    -e "s/^versionName=.*/versionName=$next_name/" "$file" > "$tmp"
mv "$tmp" "$file"

echo "$current_name ($current_code) -> $next_name ($next_code)"
echo "Now move the CHANGELOG.md entries into a '## [$next_name] - $(date +%F)' section."
