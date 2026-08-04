#!/usr/bin/env bash
# Headless plugin-load gate: boot LX with no UI, confirm it discovers our jar
# from the Packages dir and runs ChromatikMcpPlugin.initialize(). Uses an isolated
# user.home so it never touches the real ~/Chromatik or ~/LXStudio.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/.."

# Use a concrete JDK because the macOS /usr/bin/java stub breaks after this gate
# overrides user.home for the isolated LX boot.
JAVA_BIN="$("$SCRIPT_DIR/resolve-java.sh")"

# Build the plugin jar and compile the headless harness (src/test, not shipped).
echo "==> mvn package + test-compile"
mvn -q -B package test-compile

# Version-agnostic: the shaded jar is the only chromatik-mcp-*.jar (shade keeps the
# unshaded copy as original-*.jar, which the glob doesn't match).
JAR="$(ls target/chromatik-mcp-*.jar 2>/dev/null | head -1)"
if [[ -z "$JAR" ]]; then
  echo "FAIL: no target/chromatik-mcp-*.jar after mvn package" >&2
  exit 1
fi

# Resolve ONLY LX + its transitive deps — the faithful Chromatik app classpath.
# Resolving from this project's pom (even with includeScope=provided) emits the
# entire dependency tree including the shaded-in MCP SDK, which let ServiceLoader
# resolve from the parent classpath and made this gate a false positive for
# deployment-shape bugs (the SDK's ServiceLoader lookup fails inside Chromatik's
# child classloader unless the plugin handles it). A throwaway pom that depends
# on LX alone reproduces what Chromatik actually provides.
LX_VERSION="$(mvn -q help:evaluate -Dexpression=lx.version -DforceStdout)"
CP_FILE="$(mktemp)"
GATE_POM_DIR="$(mktemp -d)"
cat > "$GATE_POM_DIR/pom.xml" <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>co.chromatikmcp</groupId>
  <artifactId>load-gate-classpath</artifactId>
  <version>0</version>
  <packaging>pom</packaging>
  <dependencies>
    <dependency>
      <groupId>com.heronarts</groupId>
      <artifactId>lx</artifactId>
      <version>${LX_VERSION}</version>
    </dependency>
  </dependencies>
</project>
EOF
mvn -q -f "$GATE_POM_DIR/pom.xml" dependency:build-classpath -Dmdep.outputFile="$CP_FILE"
LX_CP="$(cat "$CP_FILE")"

# Isolated media root: LX bootstraps <user.home>/LXStudio/Packages. It reads the
# JVM `user.home` system property (NOT the HOME env var), so override that.
FAKE_HOME="$(mktemp -d)"
LOG="$(mktemp)"
trap 'rm -f "$CP_FILE" "$LOG"; rm -rf "$FAKE_HOME" "$GATE_POM_DIR"' EXIT
mkdir -p "$FAKE_HOME/LXStudio/Packages"
cp "$JAR" "$FAKE_HOME/LXStudio/Packages/"

echo "==> booting LX headless (isolated user.home=$FAKE_HOME)"
# The harness constructs LX (which scans Packages, registers + initializes the
# plugin) then exits — no engine loop to kill. The plugin jar is deliberately
# NOT on this classpath; LX must discover it from the Packages dir.
"$JAVA_BIN" -Duser.home="$FAKE_HOME" \
  -cp "target/test-classes:$LX_CP" \
  chromatikmcp.HeadlessLoadCheck >"$LOG" 2>&1 || true

echo "----- LX log -----"
cat "$LOG"
echo "------------------"

fail=0

if grep -q "Package:Chromatik-MCP" "$LOG"; then
  echo "OK: package descriptor discovered (Package:Chromatik-MCP)"
else
  echo "FAIL: 'Package:Chromatik-MCP' not found — jar not picked up" >&2
  fail=1
fi

if grep -q "\[Chromatik-MCP\] plugin loaded" "$LOG"; then
  echo "OK: ChromatikMcpPlugin.initialize() ran"
else
  echo "FAIL: '[Chromatik-MCP] plugin loaded' not found — initialize() not called" >&2
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
