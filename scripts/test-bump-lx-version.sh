#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/bump-lx-version-test.XXXXXX")"
trap 'rm -rf "$FIXTURE_ROOT"' EXIT

for dir in package .claude/skills/chromatik-mcp-catalog docs \
    site/src/content/docs .github/workflows; do
  mkdir -p "$FIXTURE_ROOT/$dir"
done
for file in package/pom.xml package/README.md \
    .claude/skills/chromatik-mcp-catalog/sources.json docs/install.md \
    docs/catalog-format.md site/src/content/docs/getting-started.md \
    .github/workflows/build.yml; do
  cp "$REPO_ROOT/$file" "$FIXTURE_ROOT/$file"
done

CURRENT="$(sed -n 's/.*<lx.version>\([^<]*\)<\/lx.version>.*/\1/p' \
  "$FIXTURE_ROOT/package/pom.xml")"
TARGET=99.98.97
# An unrelated source may coincidentally use the LX version. The helper must scope both its
# shape check and replacement to the LX entry identified by its Maven coordinate.
OLD="$CURRENT" perl -0pi -e '
  s{("Apotheneum".*?"version": ")local(")}{$1$ENV{OLD}$2}s
' "$FIXTURE_ROOT/.claude/skills/chromatik-mcp-catalog/sources.json"
before="$(find "$FIXTURE_ROOT" -type f -exec shasum -a 256 {} \; | sort | shasum -a 256)"

"$REPO_ROOT/scripts/bump-lx-version.sh" --check --root "$FIXTURE_ROOT" "$CURRENT"
if "$REPO_ROOT/scripts/bump-lx-version.sh" --check --root "$FIXTURE_ROOT" "$TARGET"; then
  echo "FAIL: --check accepted an unapplied target" >&2
  exit 1
fi
"$REPO_ROOT/scripts/bump-lx-version.sh" --dry-run --root "$FIXTURE_ROOT" "$TARGET"
after_dry_run="$(find "$FIXTURE_ROOT" -type f -exec shasum -a 256 {} \; | sort | shasum -a 256)"
[[ "$before" == "$after_dry_run" ]] || { echo "FAIL: --dry-run changed files" >&2; exit 1; }

workflow_before="$(shasum -a 256 "$FIXTURE_ROOT/.github/workflows/build.yml")"
"$REPO_ROOT/scripts/bump-lx-version.sh" --root "$FIXTURE_ROOT" "$TARGET"
"$REPO_ROOT/scripts/bump-lx-version.sh" --check --root "$FIXTURE_ROOT" "$TARGET"
grep -F '"version": "'"$CURRENT"'"' \
  "$FIXTURE_ROOT/.claude/skills/chromatik-mcp-catalog/sources.json" >/dev/null || {
    echo "FAIL: bump changed an unrelated matching source version" >&2
    exit 1
  }
after_first="$(find "$FIXTURE_ROOT" -type f -exec shasum -a 256 {} \; | sort | shasum -a 256)"
"$REPO_ROOT/scripts/bump-lx-version.sh" --root "$FIXTURE_ROOT" "$TARGET"
after_second="$(find "$FIXTURE_ROOT" -type f -exec shasum -a 256 {} \; | sort | shasum -a 256)"
workflow_after="$(shasum -a 256 "$FIXTURE_ROOT/.github/workflows/build.yml")"

[[ "$after_first" == "$after_second" ]] || { echo "FAIL: bump is not idempotent" >&2; exit 1; }
[[ "$workflow_before" == "$workflow_after" ]] || { echo "FAIL: bump changed workflow JDK" >&2; exit 1; }
echo "bump-lx-version.sh: check, dry-run, write, and idempotence tests passed"
