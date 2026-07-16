#!/usr/bin/env bash
# Compact mvn gate: runs `mvn -f package/pom.xml package`, keeps the full log on disk, and
# prints only a one-line success summary or the extracted errors on failure.
# Agents run this repeatedly — the raw mvn log (compiler + 200+ surefire tests +
# shade) floods context, so this script filters it down.
#
# Runs offline (-o) first: a warm build is ~10s, but WITHOUT -o Maven does network
# round-trips to check for plugin/dependency updates that can add minutes. If the offline
# build fails specifically because an artifact isn't cached yet (first checkout, version
# bump), it retries once online.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

POM="$REPO_ROOT/package/pom.xml"

if [[ ! -f "$POM" ]]; then
  echo "FAIL: pom not found: $POM" >&2
  exit 2
fi

LOG="$(mktemp "${TMPDIR%/}/build-gate.$$.XXXXXX")"

mvn -o -f "$POM" package >"$LOG" 2>&1
RC=$?

# Offline can fail because an artifact isn't cached yet (not a code failure). Detect that
# specific signature and retry once online; a genuine compile/test failure is not retried.
if [[ "$RC" -ne 0 ]] && grep -qE 'offline mode|but the artifact|Cannot access .* in offline|Could not resolve dependencies' "$LOG"; then
  echo "(offline build could not resolve an artifact — retrying online)" >&2
  mvn -f "$POM" package >"$LOG" 2>&1
  RC=$?
fi

if [[ "$RC" -eq 0 ]]; then
  SUMMARY="$(grep '^\[INFO\] Tests run:' "$LOG" | tail -1)"
  if [[ -z "$SUMMARY" ]]; then
    SUMMARY="no test summary found"
  else
    SUMMARY="${SUMMARY#\[INFO\] }"
  fi
  echo "BUILD SUCCESS — ${SUMMARY}"
  echo "full log: $LOG"
  exit 0
fi

echo "BUILD FAILURE (exit $RC) — full log: $LOG"
echo "--- failed tests ---"
FAILED_TESTS="$(grep -E '<<< FAILURE!|<<< ERROR!' "$LOG" | head -30)"
echo "$FAILED_TESTS"
echo "--- error lines ---"
ERROR_LINES="$(grep '\[ERROR\]' "$LOG" \
  | grep -vE '^\[ERROR\]\s*$' \
  | grep -vE 'For more information|Re-run Maven|To see the full stack|-> \[Help|\[Help 1\]' \
  | grep -vE '<<< FAILURE!|<<< ERROR!' \
  | head -60)"
if [[ -z "$FAILED_TESTS" && -z "$ERROR_LINES" ]]; then
  echo "--- log tail ---"
  tail -20 "$LOG"
else
  echo "$ERROR_LINES"
fi

exit "$RC"
