# Tool surface conventions (decided in PR-3)

Settled once, before the tool fan-out; every later tool PR follows these. Changing any of
them is a cross-cutting change that touches every tool — propose it as its own PR.

## Naming

- `verb_noun` snake_case. `get_*` for single reads, `list_*` for collections (plural
  noun), `add_*` / `remove_*` / `set_*` for mutations.
- `list_available_*` = instantiable **classes** from the LX registry; `list_*` = live
  **instances** in the project.

### Argument naming

Decided once (#108) so it isn't re-litigated per tool:

- An arg naming a **class to instantiate** is `class` (e.g. `add_pattern`, `add_effect`,
  `add_modulator`, `add_channel`'s optional seed pattern, `get_component_doc`).
- An arg naming the **canonical path of the container being added to** is `containerPath`
  (e.g. `add_pattern`'s channel, `add_effect`'s channel/master/pattern chain).
- Modulation endpoints are `sourcePath` / `targetPath` (`wire_modulator`, `wire_trigger`).
- `scope` remains for optional engine/device scoping (unrelated to the above — it says
  *where* a mutation lands, not *what* it targets).
- A single-subject path arg (the thing being read, wired to a live instance, etc.) stays
  `path`.
- Exempt: the MIDI tools' `type` (a MIDI message type, "cc"/"note") and `channel` (a MIDI
  channel number 0-15) denote MIDI-protocol concepts, not a class or a container — they
  keep their names. `add_fixture`'s `type` (a .lxf fixture type string) is also exempted —
  it is not a class or container, and agents must not rename it.

## Entity addressing

- The **canonical LX path** is the one address every tool accepts and returns (e.g.
  `/lx/mixer/channel/1/fader`): `LXPath.getCanonicalPath()` produces it,
  `LXPath.get(lx, path)` resolves it natively — the two round-trip. Tools resolve paths
  through `chromatikmcp.domain.Resolve` (PR-3b), whose typed failures (`NOT_FOUND`,
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
  the constants on `chromatikmcp.tools.Result` — `not_found`, `invalid_argument`, `internal` —
  stable so agents can dispatch on them.
- Unexpected exceptions map to `internal` at the seam (`chromatikmcp.tools.Tools`); they never
  cross the MCP boundary as stack traces.
- The SDK validates `inputSchema` server-side: a request missing a required argument, or
  carrying one it doesn't declare, is rejected (`isError: true`, SDK-worded message)
  before the handler runs. Handler `invalid_argument` checks only need to cover what a
  JSON Schema can't express (empty strings, semantic constraints).
- The SDK's default validator hardcodes an "outputSchema"/"structuredContent" wording for
  every such rejection (a 2.0.0-RC1 quirk — see `RewordingJsonSchemaValidator`), which
  would otherwise mislabel an input-argument failure as an output error; the server
  rewrites that wording so the message names the input schema instead.
- `outputSchema` is deliberately **not** declared in v1: the SDK is at 2.0.0-RC1 and its
  validation semantics for `isError` results against a declared schema are unverified.
  Revisit at the SDK GA bump (tracker follow-up in `docs/build-plan.md`). Declaring one
  also requires revisiting `RewordingJsonSchemaValidator`, which currently assumes no tool
  has an `outputSchema` and rewrites every validator failure accordingly — `EmbeddedMcpServer`
  enforces that assumption at startup.
- **Explicit exception**: `apply_operations` always returns `Result.ok` at the top level,
  even when individual batched operations failed — per-op outcomes live in its `results`
  array (`{index, ok, result}` or `{index, ok: false, code, message}`), reusing the same
  codes a top-level call would return. A client that only dispatches on the top-level
  `isError` will silently miss those per-op failures; it must inspect `results`. This is a
  deliberate divergence for a batch tool (there is no single expected/unexpected outcome
  for a multi-operation call), not an oversight — the rest of the tool surface follows the
  rule above.

## Drill-down

- Single-item detail on a collection is a sibling `get_*` tool (e.g. `get_fixture` next to
  `list_fixtures`, `get_channel` next to `list_channels`) resolved through
  `chromatikmcp.domain.Resolve`, never a `path` argument bolted onto the `list_*` tool. A
  `path`-on-`list_*` shape was tried and reverted (#123 removed a `list_channels{path}`
  drill-down): it was O(all items) to serve one, mis-typed a malformed path as `not_found`
  instead of `invalid_argument` by bypassing `Resolve`, and 404'd on entities (the master
  bus) that aren't in the collection's natural type. A `get_*` sibling reuses the
  collection tool's per-entry payload-shaping helper as its base, and may extend it with
  detail that would be too expensive to include in a list: `GetChannel` calls the same
  `ListChannels.channelFull`/`masterFull` helpers with no additions, so its output matches
  `list_channels{detail:"full"}`'s entry exactly by construction; `GetFixture` starts from
  `ListFixtures.toMap` but adds `parameters`, `submodels`, and (conditionally)
  `subfixturesAvailable`, `children`, `jsonParameters` on top. Either way the shared helper
  means the two tools can't drift on the fields they do have in common. Returns just that
  entity — no collection envelope. `list_parameters`, `list_snapshots`, and
  `list_midi_mappings` should follow this same shape when they hit the same
  payload-size problem.

## List tool detail levels

- `list_*` tools accept an optional `detail` argument with values `summary` (default) or
  `full`. **`summary` is the default** — an agent that never reads the docs gets the cheap
  path.
- `summary` omits expensive per-entry data (full `patterns`/`effects` arrays,
  `controls` blocks, range/polarity metadata on wirings) but includes per-entry
  **metadata** (path, id, label, index, type) and **count keys** only where the full
  array is omitted (e.g. `patternCount`/`effectCount` in a channel summary, but never
  bare counts for arrays that are emitted). A summary entry is a lean survey; counts
  tell agents "there are more details available via `detail: full`" without doubling the
  payload size.
- `full` emits the complete per-entry shape — all arrays, all parameter objects, all
  option lists.
- **A key that appears in both modes keeps the same JSON type.** Narrowing an object to
  a bare string in summary (e.g. emitting `crossfaderBlendMode` as a label instead of
  `{current, path}`) silently breaks any client reading a subfield, and drops the
  settable path that `set_parameter` needs. Drop the *expensive part* of a value —
  a long `options` array — not the value's shape. The count keys are the deliberate
  exception: they exist only in summary, precisely because the array they count does not.

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

Per `docs/qa-strategy.md`: a domain-primitive unit test against headless LX plus a
handler integration test over in-process streamable-HTTP (`chromatikmcp.tools.ToolsIntegrationTest`
is the seed). Mutations additionally get do → undo → assert.
