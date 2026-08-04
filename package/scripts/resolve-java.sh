#!/usr/bin/env bash
# Prints a concrete Java executable suitable for scripts that invoke the JVM directly.
set -euo pipefail

resolve_java() {
  local candidate

  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    echo "$JAVA_HOME/bin/java"
    return
  fi
  if candidate="$(/usr/libexec/java_home 2>/dev/null)" \
      && [[ -x "$candidate/bin/java" ]]; then
    echo "$candidate/bin/java"
    return
  fi
  if candidate="$(mvn -v 2>/dev/null | sed -n 's/^Java home: //p')" \
      && [[ -x "$candidate/bin/java" ]]; then
    echo "$candidate/bin/java"
    return
  fi
  for candidate in /opt/homebrew/opt/openjdk/bin/java /usr/local/opt/openjdk/bin/java; do
    if [[ -x "$candidate" ]]; then
      echo "$candidate"
      return
    fi
  done
  candidate="$(command -v java || true)"
  if [[ -n "$candidate" && "$candidate" != "/usr/bin/java" && -x "$candidate" ]]; then
    echo "$candidate"
    return
  fi

  echo "FAIL: could not locate a runnable JDK; set JAVA_HOME to a JDK installation" >&2
  return 1
}

resolve_java
