# Tool surface conventions (decided in PR-3)

Settled once, before the tool fan-out; every later tool PR follows these. Changing any of
them is a cross-cutting change that touches every tool — propose it as its own PR.

## Naming

- `verb_noun` snake_case. `get_*` for single reads, `list_*` for collections (plural
  noun), `add_*` / `remove_*` / `set_*` for mutations.
- `list_available_*` = instantiable **classes** from the LX registry; `list_*` = live
  **instances** in the project.

## Entity addressing

- The **canonical LX path** is the one address every tool accepts and returns (e.g.
  `/lx/mixer/channel/1/fader`): `LXPath.getCanonicalPath()` produces it,
  `LXPath.get(lx, path)` resolves it natively — the two round-trip. Tools resolve paths
  through `lxmcp.domain.Resolve` (PR-3b), whose typed failures (`NOT_FOUND`,
  `TYPE_MISMATCH`, `INVALID_PATH`) the seam maps to the wire codes below.
- List tools include the `LXComponent` id (stable int, `lx.getComponent(id)`) for each
  component entry as supplementary output; tools do **not** accept ids as input in v1.
- Child arrays are 1-indexed in paths (LX's OSC convention); numeric `index` fields in
  payloads are 0-based Java indices. The path is the address — `index` is informational.

## Wire shape (`Result<T>` over MCP)

- **Success**: `structuredContent` = a JSON object (never a bare array — list payloads
  are wrapped, e.g. `{"patterns": [...]}`), plus a `TextContent` mirror of the same JSON
  for clients that don't read structured output (MCP-spec recommended).
- **Expected failure**: `isError: true` with `TextContent` `"code: message"`. Codes are
  the constants on `lxmcp.tools.Result` — `not_found`, `invalid_argument`, `internal` —
  stable so agents can dispatch on them.
- Unexpected exceptions map to `internal` at the seam (`lxmcp.tools.Tools`); they never
  cross the MCP boundary as stack traces.
- The SDK validates `inputSchema` server-side: a request missing a required argument is
  rejected (`isError: true`, SDK-worded message) before the handler runs. Handler
  `invalid_argument` checks only need to cover what a JSON Schema can't express (empty
  strings, semantic constraints).
- `outputSchema` is deliberately **not** declared in v1: the SDK is at 2.0.0-RC1 and its
  validation semantics for `isError` results against a declared schema are unverified.
  Revisit at the SDK GA bump (tracker follow-up in `docs/build-plan.md`).

## Image-bearing results (PR-8)

- A tool that returns media uses `Result.okImage(payload, pngSupplier)` — the seam adds
  an `ImageContent` (base64 PNG) after the structuredContent + text mirror. PNG is the
  only media type until a second media-bearing tool motivates generalizing.
- **The supplier runs on the HTTP worker thread**, after the handler has left the engine
  thread — so it must close only over immutable data (a detached snapshot record), never
  over live LX state. This keeps encoding cost off the engine thread without violating
  the engine-thread rule below.
- A supplier that throws maps to `internal` at the seam, like any handler exception.

## Mutations

- Mutations route through `LXCommand` via a domain primitive (CLAUDE.md layering).
  **`lx.command.perform()` swallows command failures** — it pushes a UI error and
  returns normally (`LXCommandEngine.java:77-85`) — so a mutation primitive must verify
  its effect by observing engine state and throw if it didn't apply. "Command-backed"
  does not mean "cannot fail", and an unverified read-back returns the wrong object on
  failure.
- Two LX-behavior caveats on that failure path: `perform()` also calls `clear()`, which
  **wipes the user's entire undo and redo history**, and the failure is double-reported
  (Chromatik shows the `pushError` dialog; the MCP client gets the `internal` error) —
  both are LX's behavior, not ours; don't "fix" the duplication at the seam.
- A state-read size check suffices when the command's only realistic failure precedes
  its first mutation (instantiation, validation). For commands that can fail *after*
  mutating, the exact detector is `lx.command.getUndoCommand()` — the performed command
  on success, empty stack after a failed `perform()`. Decide per primitive in PR-5.
- A tool call that times out (`internal: Engine task timed out…`) has **not** been
  cancelled: the task stays in the engine queue and the mutation still applies when the
  engine drains it. Agents should re-read state after a timeout, never blind-retry.
- Every mutation's domain test is do → undo → assert restored (qa-strategy); the undo
  assertion doubles as proof the primitive used a real `LXCommand`.

## Threading

- Every handler runs on the LX engine thread via `EngineExecutor.call(...)`; Tomcat
  worker threads never touch `lx.*` directly. Domain read primitives return immutable
  snapshot records assembled on the engine thread, so no live LX object is read
  off-thread.

## Tool annotations

- `readOnlyHint` is set from `LxTool.readOnly()` — `true` for all PR-3 discovery tools,
  `false` for mutations (PR-4 onward).

## Test shape

Per `docs/spike/qa-strategy.md`: a domain-primitive unit test against headless LX plus a
handler integration test over in-process streamable-HTTP (`lxmcp.tools.ToolsIntegrationTest`
is the seed). Mutations additionally get do → undo → assert.
