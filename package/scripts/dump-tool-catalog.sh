#!/usr/bin/env bash
# Regenerates docs/tools.json from the actual LxTool instances (ToolCatalogDump).
# Run this whenever a tool's name/description/schema/readOnly changes, then
# `node scripts/generate-tool-reference.mjs` to re-render docs/tools.md.
# ToolCatalogDriftTest fails the build gate if the two ever fall out of sync.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PACKAGE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$PACKAGE_DIR/.." && pwd)"

OUT="${1:-$REPO_ROOT/docs/tools.json}"

echo "==> mvn test-compile"
mvn -q -o -f "$PACKAGE_DIR/pom.xml" test-compile

CP_FILE="$(mktemp)"
trap 'rm -f "$CP_FILE"' EXIT
echo "==> resolving test classpath"
mvn -q -o -f "$PACKAGE_DIR/pom.xml" dependency:build-classpath \
  -Dmdep.includeScope=test -Dmdep.outputFile="$CP_FILE"
DEP_CP="$(cat "$CP_FILE")"

echo "==> dumping tool catalog to $OUT"
JAVA_BIN="$("$SCRIPT_DIR/resolve-java.sh")"
"$JAVA_BIN" -cp "$PACKAGE_DIR/target/test-classes:$PACKAGE_DIR/target/classes:$DEP_CP" \
  chromatikmcp.ToolCatalogDump "$OUT"

echo "OK: wrote $OUT"
