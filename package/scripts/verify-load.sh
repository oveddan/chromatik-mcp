#!/usr/bin/env bash
# Headless plugin-load gate: boot LX with no UI, confirm it discovers our jar
# from the Packages dir and runs LxMcpPlugin.initialize(). Uses an isolated
# user.home so it never touches the real ~/Chromatik or ~/LXStudio.
set -euo pipefail

cd "$(dirname "$0")/.."

JAR="target/lx-mcp-0.0.1-SNAPSHOT.jar"

# Resolve a real JDK now. The macOS /usr/bin/java stub resolves the runtime via
# the user's home, so overriding user.home below would break it — point at a
# concrete JDK instead. Try, in order: $JAVA_HOME, /usr/libexec/java_home,
# Maven's own runtime, common Homebrew locations.
resolve_java() {
  local c
  [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]] && { echo "$JAVA_HOME/bin/java"; return; }
  if c="$(/usr/libexec/java_home 2>/dev/null)" && [[ -x "$c/bin/java" ]]; then
    echo "$c/bin/java"; return
  fi
  if c="$(mvn -v 2>/dev/null | sed -n 's/^Java home: //p')" && [[ -x "$c/bin/java" ]]; then
    echo "$c/bin/java"; return
  fi
  for c in /opt/homebrew/opt/openjdk/bin/java /usr/local/opt/openjdk/bin/java; do
    [[ -x "$c" ]] && { echo "$c"; return; }
  done
  c="$(command -v java || true)"
  [[ -n "$c" && "$c" != "/usr/bin/java" ]] && { echo "$c"; return; }
}
JAVA_BIN="$(resolve_java)"
if [[ -z "$JAVA_BIN" || ! -x "$JAVA_BIN" ]]; then
  echo "FAIL: could not locate a runnable JDK (set JAVA_HOME)" >&2
  exit 1
fi

# Build the plugin jar and compile the headless harness (src/test, not shipped).
echo "==> mvn package + test-compile"
mvn -q -B package test-compile

# Resolve the provided-scope classpath (LX + its transitive deps).
CP_FILE="$(mktemp)"
mvn -q dependency:build-classpath -Dmdep.includeScope=provided -Dmdep.outputFile="$CP_FILE"
LX_CP="$(cat "$CP_FILE")"

# Isolated media root: LX bootstraps <user.home>/LXStudio/Packages. It reads the
# JVM `user.home` system property (NOT the HOME env var), so override that.
FAKE_HOME="$(mktemp -d)"
LOG="$(mktemp)"
trap 'rm -f "$CP_FILE" "$LOG"; rm -rf "$FAKE_HOME"' EXIT
mkdir -p "$FAKE_HOME/LXStudio/Packages"
cp "$JAR" "$FAKE_HOME/LXStudio/Packages/"

echo "==> booting LX headless (isolated user.home=$FAKE_HOME)"
# The harness constructs LX (which scans Packages, registers + initializes the
# plugin) then exits — no engine loop to kill. The plugin jar is deliberately
# NOT on this classpath; LX must discover it from the Packages dir.
"$JAVA_BIN" -Duser.home="$FAKE_HOME" \
  -cp "target/test-classes:$LX_CP" \
  lxmcp.HeadlessLoadCheck >"$LOG" 2>&1 || true

echo "----- LX log -----"
cat "$LOG"
echo "------------------"

fail=0

if grep -q "Package:LX-MCP" "$LOG"; then
  echo "OK: package descriptor discovered (Package:LX-MCP)"
else
  echo "FAIL: 'Package:LX-MCP' not found — jar not picked up" >&2
  fail=1
fi

if grep -q "\[LX-MCP\] plugin loaded" "$LOG"; then
  echo "OK: LxMcpPlugin.initialize() ran"
else
  echo "FAIL: '[LX-MCP] plugin loaded' not found — initialize() not called" >&2
  fail=1
fi

if grep -q "Unhandled error in plugin initialize" "$LOG"; then
  echo "FAIL: plugin initialize threw" >&2
  fail=1
fi

if [[ "$fail" -ne 0 ]]; then
  exit 1
fi

echo "LOAD OK"
