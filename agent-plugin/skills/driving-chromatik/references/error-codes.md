# Error codes and wire shape reference

Distilled from `docs/tool-conventions.md` in the chromatik-mcp repo — the `Result<T>`
shape every tool returns over MCP.

## Success

A successful call returns `structuredContent` — a JSON object, never a bare array (list
payloads are wrapped, e.g. `{"patterns": [...]}`) — plus a `TextContent` mirror of the
same JSON for clients that don't read structured output.

## Expected failure

`isError: true` with a `TextContent` body shaped `"code: message"`. The codes are stable,
constant strings — build dispatch logic against them, never against the message text,
which can change:

| code | meaning |
|---|---|
| `not_found` | the addressed path/class/entity doesn't resolve |
| `invalid_argument` | the call's arguments are semantically invalid (a JSON Schema violation is rejected before the handler even runs, with an SDK-worded message — these three codes cover what schema validation can't express: empty strings, cross-field constraints, ambiguous short names, etc.) |
| `internal` | an unexpected failure — including a timed-out engine task (see below) |

Unexpected exceptions inside a handler map to `internal` at the server seam; they never
cross the MCP boundary as a raw stack trace.

## Timeouts are not cancellations

A tool call that times out reports `internal: Engine task timed out…`. This does **not**
mean the mutation was rolled back or skipped: the task stays queued on the LX engine
thread and still applies once the engine drains it. Practical consequence: re-read state
after a timeout to see what actually happened, and never blind-retry a timed-out
mutation — retrying a mutation that already landed applies it twice.

## Mutations and undo

Mutations route through `LXCommand` for undo support. `lx.command.perform()` swallows
command failures internally (it pushes a UI error and returns normally) rather than
throwing, so a well-built mutation primitive verifies its effect by re-reading engine
state — "command-backed" does not guarantee "succeeded." Two side effects worth knowing
about when something goes wrong:

- A failed `perform()` call also wipes the **entire** undo and redo history, not just the
  attempted command's own undo slot.
- The failure is double-reported: Chromatik's own UI shows a `pushError` dialog *and* the
  MCP client gets the `internal` error. That's LX's behavior, not a bug in this server.

## `apply_operations`: the one deliberate divergence

`apply_operations` validates the whole batch before applying any of it: a missing or
non-array `operations`, an empty or oversized (>50 entries) array, or any per-entry
validation failure (a non-object entry, a non-string `tool`, an unknown/read-only/nested
tool name, a non-object `args`) fails the **entire call** with a top-level
`invalid_argument` and applies nothing. Check `isError` first.

Past validation, execution is continue-on-error — a failing operation does not stop the
rest of the batch from applying. Per-op outcomes live in its `results` array:

- `{index, ok: true, result}` — same shape a top-level call to that tool would return.
- `{index, ok: false, code, message}` — same codes as above.

A client that only checks the top-level `isError` will silently miss per-op failures once
validation has passed; it must inspect `results` too. Other sharp edges specific to
batching:

- Batching does **not** collapse into one undo step. Each operation still produces its
  own undo entry (or entries), so undoing an N-operation batch takes N presses of Cmd-Z.
- Because a failed `perform()` wipes undo/redo history (see above), one failing operation
  in a batch can silently erase undo history for every *earlier* operation in the same
  batch — even though those earlier operations still report `ok: true`.
- All operations in a batch run inside a single engine frame. An I/O-heavy operation
  (e.g. `reload_fixtures`, which re-reads every `.lxf` fixture file from disk) stalls the
  whole batch's cost onto that one frame, and a large or slow batch can trip the engine
  task's timeout — which, per the timeout rule above, does not cancel the batch; it still
  applies once the engine drains it.
