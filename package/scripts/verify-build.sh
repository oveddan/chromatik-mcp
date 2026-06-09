#!/usr/bin/env bash
# Compile gate: build the package jar and confirm the lx.package descriptor was
# token-filtered. Pass --load to also run the headless plugin-load gate
# (scripts/verify-load.sh), which boots LX with no UI and checks the plugin
# initializes.
set -euo pipefail

cd "$(dirname "$0")/.."

WITH_LOAD=0
if [[ "${1:-}" == "--load" ]]; then
  WITH_LOAD=1
fi

JAR="target/lx-mcp-0.0.1-SNAPSHOT.jar"

echo "==> mvn package"
mvn -q -B package

if [[ ! -f "$JAR" ]]; then
  echo "FAIL: expected jar not produced at $JAR" >&2
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
  exec "$(dirname "$0")/verify-load.sh"
fi
