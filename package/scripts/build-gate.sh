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
#
# Watchdog: `new LX()` in tests can hit a known macOS deadlock (JDK javax.sound
# JSSecurityManager class lock vs. coremidi4j CoreMidiDeviceProvider class lock, taken in
# opposite order by racing threads) that wedges the mvn JVM at 0% CPU forever. If the log
# stops growing for STALL_LIMIT seconds, the build is killed and retried once; a second
# stall fails fast with a pointer to the log instead of hanging the caller indefinitely.
#
# Single-flight: concurrent gate runs (e.g. from parallel agent worktrees) serialize on a
# mkdir-based lock, since macOS has no flock(1).
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

POM="$REPO_ROOT/package/pom.xml"

if [[ ! -f "$POM" ]]; then
  echo "FAIL: pom not found: $POM" >&2
  exit 2
fi

LOCK_DIR="/tmp/lx-mcp-build-gate.lock"
LOCK_HELD=0
release_lock() { [[ "$LOCK_HELD" -eq 1 ]] && rm -rf "$LOCK_DIR"; }
trap release_lock EXIT INT TERM

waited=0
while ! mkdir "$LOCK_DIR" 2>/dev/null; do
  holder="$(cat "$LOCK_DIR/pid" 2>/dev/null)"
  if [[ -n "$holder" ]] && ! kill -0 "$holder" 2>/dev/null; then
    echo "(stealing stale build-gate lock from dead pid $holder)" >&2
    rm -rf "$LOCK_DIR"
    continue
  fi
  [[ "$waited" -ge 600 ]] && { echo "FAIL: timed out waiting 10m for build-gate lock" >&2; exit 3; }
  sleep 5
  waited=$((waited + 5))
done
echo "$$" >"$LOCK_DIR/pid"
LOCK_HELD=1

LOG="$(mktemp "${TMPDIR%/}/build-gate.$$.XXXXXX")"

STALL_LIMIT=120
POLL_INTERVAL=10

# Runs `mvn "$@" >"$LOG" 2>&1` in the background and kills it (returning 124) if the log
# stops growing for STALL_LIMIT seconds — the signature of the class-lock deadlock above.
run_mvn_watched() {
  : >"$LOG"
  mvn "$@" >"$LOG" 2>&1 &
  local mvn_pid=$! last_size=-1 stalled=0 size
  while kill -0 "$mvn_pid" 2>/dev/null; do
    sleep "$POLL_INTERVAL"
    size=$(wc -c <"$LOG" 2>/dev/null || echo 0)
    if [[ "$size" -eq "$last_size" ]]; then
      stalled=$((stalled + POLL_INTERVAL))
    else
      stalled=0
    fi
    last_size="$size"
    if [[ "$stalled" -ge "$STALL_LIMIT" ]]; then
      pkill -TERM -P "$mvn_pid" 2>/dev/null
      kill -TERM "$mvn_pid" 2>/dev/null
      sleep 2
      pkill -KILL -P "$mvn_pid" 2>/dev/null
      kill -KILL "$mvn_pid" 2>/dev/null
      wait "$mvn_pid" 2>/dev/null
      return 124
    fi
  done
  wait "$mvn_pid"
}

# Runs a build with the stall watchdog, retrying once on a detected deadlock; a second
# stall in a row fails the gate outright rather than hanging forever.
attempt_build() {
  run_mvn_watched "$@"
  local rc=$?
  if [[ "$rc" -eq 124 ]]; then
    echo "deadlock watchdog triggered (log stalled ${STALL_LIMIT}s+) — retrying" >&2
    run_mvn_watched "$@"
    rc=$?
    if [[ "$rc" -eq 124 ]]; then
      echo "FAIL: build stalled twice — this matches the known macOS javax.sound/coremidi4j class-lock deadlock (see CLAUDE.md / ProviderWarmupListener). Full log: $LOG" >&2
      exit 4
    fi
  fi
  return "$rc"
}

attempt_build -o -f "$POM" package
RC=$?

# Offline can fail because an artifact isn't cached yet (not a code failure). Detect that
# specific signature and retry once online; a genuine compile/test failure is not retried.
if [[ "$RC" -ne 0 ]] && grep -qE 'offline mode|but the artifact|Cannot access .* in offline|Could not resolve dependencies' "$LOG"; then
  echo "(offline build could not resolve an artifact — retrying online)" >&2
  attempt_build -f "$POM" package
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
