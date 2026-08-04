#!/usr/bin/env bash
# Update the repository's authoritative LX pin and the small set of user-facing copies.
# Derived catalog entry hashes are intentionally not rewritten here; regenerate them from
# the published jar with the chromatik-mcp-catalog workflow after running this script.
set -euo pipefail

MODE=write
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

usage() {
  echo "usage: $0 [--check|--dry-run] [--root DIR] <lx-version>" >&2
  exit 2
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --check) MODE=check; shift ;;
    --dry-run) MODE=dry-run; shift ;;
    --root) [[ $# -ge 2 ]] || usage; ROOT="$2"; shift 2 ;;
    --help|-h) usage ;;
    --*) usage ;;
    *) break ;;
  esac
done
[[ $# -eq 1 ]] || usage
TARGET="$1"
[[ "$TARGET" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][A-Za-z0-9.-]+)?$ ]] || {
  echo "FAIL: invalid LX version: $TARGET" >&2
  exit 2
}

POM="$ROOT/package/pom.xml"
SOURCES="$ROOT/.claude/skills/chromatik-mcp-catalog/sources.json"
PACKAGE_README="$ROOT/package/README.md"
INSTALL_DOC="$ROOT/docs/install.md"
GETTING_STARTED="$ROOT/site/src/content/docs/getting-started.md"
CATALOG_FORMAT="$ROOT/docs/catalog-format.md"

for file in "$POM" "$SOURCES" "$PACKAGE_README" "$INSTALL_DOC" \
    "$GETTING_STARTED" "$CATALOG_FORMAT"; do
  [[ -f "$file" ]] || { echo "FAIL: managed file missing: $file" >&2; exit 3; }
done

CURRENT="$(sed -n 's/.*<lx.version>\([^<]*\)<\/lx.version>.*/\1/p' "$POM")"
[[ -n "$CURRENT" && "$CURRENT" != *$'\n'* ]] || {
  echo "FAIL: expected exactly one <lx.version> in $POM" >&2
  exit 3
}

require_once() {
  local file="$1" needle="$2" label="$3" count
  count="$(NEEDLE="$needle" perl -0ne '
    $count += () = /\Q$ENV{NEEDLE}\E/g;
    END { print $count // 0 }
  ' "$file")"
  [[ "$count" == 1 ]] || {
    echo "FAIL: expected exactly one $label for LX $CURRENT in $file; found $count" >&2
    exit 3
  }
}

# Strict shape checks make stale or reformatted managed references visible instead of
# silently producing a partial bump.
require_once "$POM" "<lx.version>$CURRENT</lx.version>" "pom dependency pin"
require_once "$SOURCES" \
  "\"classBytes\": \"maven:com.heronarts:lx:$CURRENT\"," \
  "catalog LX classBytes coordinate"
LX_SOURCE_PAIR="$(printf '"classBytes": "maven:com.heronarts:lx:%s",\n    "version": "%s"' \
  "$CURRENT" "$CURRENT")"
require_once "$SOURCES" "$LX_SOURCE_PAIR" \
  "adjacent catalog LX coordinate and source version"
require_once "$PACKAGE_README" "com.heronarts:{lx,glx,glxstudio}:$CURRENT" \
  "package build requirement"
require_once "$INSTALL_DOC" "Chromatik with LX **$CURRENT**" "install requirement"
require_once "$INSTALL_DOC" "\"lxVersion\": \"$CURRENT\"" "install status example"
require_once "$GETTING_STARTED" "Chromatik](https://chromatik.co/download/) with LX **$CURRENT**" \
  "site requirement"
require_once "$GETTING_STARTED" "\"lxVersion\": \"$CURRENT\"" "site status example"
require_once "$CATALOG_FORMAT" "com/heronarts/lx/$CURRENT/lx-$CURRENT.jar" \
  "catalog origin example"
require_once "$CATALOG_FORMAT" "lxVersion: $CURRENT" "catalog version example"

if [[ "$CURRENT" == "$TARGET" ]]; then
  echo "OK: managed LX version references are $TARGET"
  exit 0
fi

if [[ "$MODE" == check ]]; then
  echo "FAIL: managed LX version is $CURRENT, expected $TARGET" >&2
  exit 1
fi

echo "LX version: $CURRENT -> $TARGET"
printf '  %s\n' \
  "package/pom.xml" \
  ".claude/skills/chromatik-mcp-catalog/sources.json" \
  "package/README.md" \
  "docs/install.md" \
  "site/src/content/docs/getting-started.md" \
  "docs/catalog-format.md"

if [[ "$MODE" == dry-run ]]; then
  echo "DRY RUN: no files changed"
  exit 0
fi

OLD="$CURRENT" NEW="$TARGET" perl -0pi -e '
  s{(<lx\.version>)\Q$ENV{OLD}\E(</lx\.version>)}{$1$ENV{NEW}$2}g
' "$POM"
OLD="$CURRENT" NEW="$TARGET" perl -0pi -e '
  s{("classBytes": "maven:com\.heronarts:lx:)\Q$ENV{OLD}\E(",\n\s+"version": ")\Q$ENV{OLD}\E(")}
   {$1$ENV{NEW}$2$ENV{NEW}$3}g
' "$SOURCES"
OLD="$CURRENT" NEW="$TARGET" perl -0pi -e '
  s{(com\.heronarts:\{lx,glx,glxstudio\}:)\Q$ENV{OLD}\E}{$1$ENV{NEW}}g
' "$PACKAGE_README"
for file in "$INSTALL_DOC" "$GETTING_STARTED"; do
  OLD="$CURRENT" NEW="$TARGET" perl -0pi -e '
    s{(with LX \*\*)\Q$ENV{OLD}\E(\*\*)}{$1$ENV{NEW}$2}g;
    s{("lxVersion": ")\Q$ENV{OLD}\E(")}{$1$ENV{NEW}$2}g
  ' "$file"
done
OLD="$CURRENT" NEW="$TARGET" perl -0pi -e '
  s{(com/heronarts/lx/)\Q$ENV{OLD}\E(/lx-)\Q$ENV{OLD}\E(\.jar)}{$1$ENV{NEW}$2$ENV{NEW}$3}g;
  s{(lxVersion: )\Q$ENV{OLD}\E}{$1$ENV{NEW}}g
' "$CATALOG_FORMAT"

"$0" --check --root "$ROOT" "$TARGET"
echo "NEXT: refresh package/src/main/resources/catalog/heronarts.lx.*.md hashes from LX $TARGET"
