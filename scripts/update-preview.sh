#!/usr/bin/env bash
# Builds the markdown renderer of MkDocsEditor-Web at the given commit and copies it into the
# assets of the preview. Web owns the renderer, so changes to these files belong there.
set -euo pipefail

ref="${1:?usage: update-preview.sh <commit of MkDocsEditor-Web>}"
assets="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets/preview"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

git -C "$work" init -q
git -C "$work" fetch -q --depth 1 https://github.com/MkDocsEditor/MkDocsEditor-Web.git "$ref"
git -C "$work" checkout -q FETCH_HEAD
commit="$(git -C "$work" rev-parse HEAD)"

(
    cd "$work"
    npm ci
    npm run build:preview
    npm run check:preview
)

cp "$work/dist-preview/mkdocs-renderer.js" "$work/dist-preview/mkdocs-renderer.css" "$assets/"
echo "$commit" > "$assets/mkdocs-renderer.version"

# the bundle does not carry every licence text, and the app has to ship them
notices="$assets/mkdocs-renderer-notices.txt"
: > "$notices"
for package in markdown-it markdown-it-attrs markdown-it-deflist markdown-it-footnote markdown-it-mark \
    markdown-it-task-lists entities linkify-it mdurl punycode.js uc.micro highlight.js dompurify; do
    dir="$work/node_modules/$package"
    version="$(node -p "require('$dir/package.json').version")"
    license="$(find "$dir" -maxdepth 1 -iname 'licen[cs]e*' | sort | head -n 1)"
    if [ -z "$license" ]; then
        echo "No licence text for $package" >&2
        exit 1
    fi
    {
        echo "=== $package $version ==="
        cat "$license"
        echo
    } >> "$notices"
done
echo "Preview renderer updated to $commit"
