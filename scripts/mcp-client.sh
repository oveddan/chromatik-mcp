#!/usr/bin/env bash
# mcp-client.sh — curl+python3 client for the chromatik-mcp streamable-HTTP endpoint.
#
# Usage:
#   scripts/mcp-client.sh init
#   scripts/mcp-client.sh tools
#   scripts/mcp-client.sh call <tool> ['<json-args>']
#
# Port resolution order: $CHROMATIK_MCP_PORT -> ~/.chromatik-mcp/status.json ->
# ~/.lx-mcp/status.json (legacy, pre-rename installs) -> error.
#
# The session id is cached at ~/.chromatik-mcp/client-session. `tools`/`call`
# auto-initialize when there is no cached session, or transparently re-init and
# retry once if the cached session is rejected (e.g. Chromatik restarted).

set -u

STATE_DIR="$HOME/.chromatik-mcp"
SID_FILE="$STATE_DIR/client-session"
PROTOCOL_VERSION="2024-11-05"

die() {
  echo "error: $*" >&2
  exit 1
}

resolve_port() {
  if [ -n "${CHROMATIK_MCP_PORT:-}" ]; then
    echo "$CHROMATIK_MCP_PORT"
    return 0
  fi

  local status_file
  for status_file in "$HOME/.chromatik-mcp/status.json" "$HOME/.lx-mcp/status.json"; do
    if [ -f "$status_file" ]; then
      local port
      port=$(python3 -c "
import json, sys
try:
    print(json.load(open('$status_file'))['port'])
except Exception:
    sys.exit(1)
" 2>/dev/null)
      if [ -n "$port" ]; then
        echo "$port"
        return 0
      fi
    fi
  done

  return 1
}

url() {
  local port
  port=$(resolve_port) || die "could not resolve MCP port: set \$CHROMATIK_MCP_PORT, or start Chromatik with the chromatik-mcp plugin enabled (checks \$CHROMATIK_MCP_PORT, ~/.chromatik-mcp/status.json, ~/.lx-mcp/status.json)"
  echo "http://127.0.0.1:${port}/mcp"
}

# Sends the initialize + notifications/initialized handshake, storing the
# resulting session id. Prints "session: <id>" and "instructions:" + body.
do_init() {
  local target headers_file body
  target=$(url) || exit 1
  mkdir -p "$STATE_DIR"
  headers_file=$(mktemp)

  body=$(curl -s -D "$headers_file" -X POST "$target" \
    -H 'Content-Type: application/json' \
    -H 'Accept: application/json, text/event-stream' \
    -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"$PROTOCOL_VERSION\",\"capabilities\":{},\"clientInfo\":{\"name\":\"chromatik-mcp-client-sh\",\"version\":\"1.0\"}}}")

  local sid
  sid=$(grep -i '^mcp-session-id' "$headers_file" | tr -d '\r' | awk '{print $2}')
  rm -f "$headers_file"

  if [ -z "$sid" ]; then
    echo "$body" >&2
    die "initialize did not return an Mcp-Session-Id header"
  fi

  echo "$sid" > "$SID_FILE"

  curl -s -X POST "$target" \
    -H 'Content-Type: application/json' \
    -H 'Accept: application/json, text/event-stream' \
    -H "Mcp-Session-Id: $sid" \
    -d '{"jsonrpc":"2.0","method":"notifications/initialized"}' > /dev/null

  echo "$body" | sed 's/^data: //' | grep '{' | python3 -c "
import json, sys
r = json.load(sys.stdin)['result']
print('session:', '$sid')
print('instructions:')
print(r.get('instructions', '(none)'))
"
}

ensure_session() {
  if [ ! -s "$SID_FILE" ]; then
    do_init > /dev/null || exit 1
  fi
}

# Sends a single JSON-RPC request against the cached session, printing the raw
# SSE-stripped body and the HTTP status code (as "HTTP_STATUS:<code>" on the
# last line) so callers can detect a rejected/stale session.
raw_request() {
  local target sid response status
  target=$(url) || exit 1
  sid=$(cat "$SID_FILE" 2>/dev/null)

  response=$(curl -s -w '\nHTTP_STATUS:%{http_code}' -X POST "$target" \
    -H 'Content-Type: application/json' \
    -H 'Accept: application/json, text/event-stream' \
    -H "Mcp-Session-Id: $sid" \
    -d "$1")

  echo "$response"
}

# Performs a JSON-RPC request, transparently re-initializing and retrying once
# if the session is rejected. Prints the JSON-RPC response body.
request() {
  local payload response status body
  payload="$1"

  ensure_session
  response=$(raw_request "$payload")
  status=$(echo "$response" | grep '^HTTP_STATUS:' | tail -1 | cut -d: -f2)
  body=$(echo "$response" | sed '/^HTTP_STATUS:/d')

  if [ "$status" = "400" ] || [ "$status" = "404" ]; then
    do_init > /dev/null || exit 1
    response=$(raw_request "$payload")
    body=$(echo "$response" | sed '/^HTTP_STATUS:/d')
  fi

  echo "$body" | sed 's/^data: //' | grep '{'
}

do_tools() {
  request '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}' | python3 -c "
import json, sys
r = json.load(sys.stdin)
result = r.get('result')
if result is None:
    print(json.dumps(r, indent=1))
    sys.exit(1)
for tool in result.get('tools', []):
    print(tool['name'])
"
}

do_call() {
  local tool args_json
  tool="${1:-}"
  if [ $# -ge 2 ]; then
    args_json="$2"
  else
    args_json='{}'
  fi
  [ -n "$tool" ] || die "usage: mcp-client.sh call <tool> ['<json-args>']"

  local payload
  payload=$(python3 -c '
import json, sys
tool = sys.argv[1]
args = json.loads(sys.argv[2])
print(json.dumps({
    "jsonrpc": "2.0", "id": 3, "method": "tools/call",
    "params": {"name": tool, "arguments": args},
}))
' "$tool" "$args_json")

  request "$payload" | python3 -c "
import json, sys
r = json.load(sys.stdin)
res = r.get('result', r)
sc = res.get('structuredContent') if isinstance(res, dict) else None
print(json.dumps(sc if sc is not None else res, indent=1))
"
}

main() {
  local cmd="${1:-}"
  case "$cmd" in
    init) do_init ;;
    tools) do_tools ;;
    call) shift; do_call "$@" ;;
    *) die "usage: mcp-client.sh {init|tools|call <tool> ['<json-args>']}" ;;
  esac
}

main "$@"
