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
