#!/usr/bin/env bash
# Compile gate: build the package jar and confirm the lx.package descriptor was
# token-filtered. Pass --load to also run the headless plugin-load gate
# (scripts/verify-load.sh), which boots LX with no UI and checks the plugin
# initializes.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/.."

WITH_LOAD=0
if [[ "${1:-}" == "--load" ]]; then
  WITH_LOAD=1
fi

echo "==> mvn package"
mvn -q -B package

# Version-agnostic: the shaded jar is the only chromatik-mcp-*.jar (shade keeps the
# unshaded copy as original-*.jar, which the glob doesn't match).
JAR="$(ls target/chromatik-mcp-*.jar 2>/dev/null | head -1)"
if [[ -z "$JAR" ]]; then
  echo "FAIL: no target/chromatik-mcp-*.jar after mvn package" >&2
  exit 1
fi

echo "==> checking lx.package descriptor inside jar"
descriptor=$(unzip -p "$JAR" lx.package)
if grep -q '@[a-zA-Z.]*@' <<<"$descriptor"; then
  echo "FAIL: lx.package still contains unfiltered @...@ tokens:" >&2
  echo "$descriptor" >&2
  exit 1
fi

echo "OK"

if [[ "$WITH_LOAD" -eq 1 ]]; then
  echo
  exec "$SCRIPT_DIR/verify-load.sh"
fi
