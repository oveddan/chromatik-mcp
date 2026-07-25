---
name: driving-chromatik
description: House rules for driving a live Chromatik (LX Studio) lighting show over the chromatik-mcp MCP server — connecting and recovering from restarts, canonical-path addressing, timeout and error-code semantics, consulting component docs before reasoning about a pattern, and the mutate/look/adjust verification loop. Use whenever calling chromatik MCP tools against a running instance.
---

## When this applies

Use this whenever you're calling chromatik-mcp tools against a running Chromatik (LX
Studio) instance — connecting, discovering the mixer/pattern tree, mutating parameters,
wiring modulation, or checking the render. It's house rules for driving the show well,
not the tool schemas themselves — call `list_channels`, `list_parameters`, and friends to
discover those live.

## Connect and confirm

The server's port lives in `~/.chromatik-mcp/status.json` (pid, port, host, url,
projectPath, lxVersion, serverVersion, buildTime, connected, lastActivityAt) — the one
filesystem touchpoint the plugin writes on startup. Read it before connecting.

To pin a stable port instead of chasing an ephemeral one, put `{"port": 3232}` in
`~/.chromatik-mcp/config.json`. This plugin's bundled `.mcp.json` points at
`http://127.0.0.1:${CHROMATIK_MCP_PORT:-3232}/mcp`, so set `CHROMATIK_MCP_PORT` in your
environment if you use a different port — but that variable only applies under Claude
Code. Under Codex the port is a literal `3232` with no expansion, so pin the port in
`~/.chromatik-mcp/config.json` instead of relying on the environment variable.

Once connected, call `get_status` first: it reports the running server's own identity
(name, jar version, build time, LX version) plus live connection info, and a successful
call also proves the engine loop is draining tasks. Compare its identity against what you
expect before trusting anything else it tells you — a stale process left over from a jar
reinstall looks alive but is running old code.

## Discover, never guess

Every component and parameter is addressed by its canonical LX path (e.g.
`/lx/mixer/channel/1/fader`). Get it from a discovery call — `list_channels`,
`list_parameters`, `get_views`, `list_available_*` — never fabricate one.

Sibling indices are 1-based and shift when items are added, removed, or reordered. A path
held from before a structural mutation (add/remove/move a channel, pattern, effect, view,
fixture) may point at the wrong component afterward — re-list instead of reusing it.

Calls are cheap but not free: each occupies the LX engine thread. Batch discovery — one
`list_channels` beats thirty `get_parameter` calls when you're surveying a mixer tree.

## Assume restarts

Any connection failure — refused connection, a dead session, a tool call erroring where
it didn't before — means "Chromatik may have restarted," not "retry the same call."
Recover in order: re-read `status.json` (the port may have changed), re-initialize the
MCP session against whatever port it now names, and re-list before reusing any canonical
path held from before the restart.

A restart also resets engine state you might assume is sticky — `output.enabled`, for
instance, comes back off. Don't infer the show is in the state you left it; ask.

## Timeouts are not cancellations

A tool call that times out (`internal: Engine task timed out…`) has not been cancelled —
the task stays queued on the engine thread and the mutation still applies once the engine
drains it. Re-read state to find out what actually happened; never blind-retry a
timed-out mutation, or you risk applying it twice.

Dispatch on the `Result` error CODE, not the message text: expected failures return
`isError: true` with a stable `"code: message"` body, where `code` is `not_found`,
`invalid_argument`, or `internal`. Those three are the contract — build error handling
against them, not against wording that can change. See the plugin's bundled
`references/error-codes.md` for the full wire shape.

## Know the instruments

Before reasoning about what a pattern, effect, or modulator actually does — its color
modes, which parameters interact, what a knob's range really controls — call
`get_component_doc` on its class (by `class` name or a live instance's `path`). The
catalog covers exactly the semantics that otherwise get guessed wrong. Staleness lives at
`catalog.stale`, nested under the `catalog` block, not top-level — and it's three-valued:
`true` (code changed since the doc was written — trust the live parameter tree over the
prose), `false` (fresh), or the string `"unknown"` (staleness couldn't be determined; read
the doc but don't treat it as verified). Read the doc first regardless of which value you
get. A registered-but-undocumented class returns `documented: false`, not an error.

## Verify your own work

Don't report success on the strength of an unchecked mutation. Loop:

1. **Mutate.** Most mutating tools verify-and-echo — `set_parameter`'s response echoes
   what actually happened, but which field carries what depends on whether the parameter
   is modulated: unmodulated, `value` is the base value you set and `baseValue` is absent
   entirely; modulated, `value` is the live *effective* reading and `baseValue` is what
   you actually set — read the response instead of assuming the call did what you asked.
2. **Look.** `get_frame` returns a cheap numeric summary — non-black fraction, lit
   fraction, mean brightness, dominant colors, an NxN mean-color grid — on every call,
   and a token-expensive PNG only when you pass `include_image: true`. Use the summary
   in a tight loop; reach for the PNG at checkpoints.
3. **Adjust.** If the frame doesn't match intent, change the parameter and loop back to
   step 1 — against the actual render, not your mental model of what the change should
   have done.

Only report done after the change has landed (echo/readback) *and* looks right (frame).

## The visibility chain

Pixels only reach fixtures when the whole chain is on: pattern contributing → channel
enabled and fader > 0 → master fader > 0 → engine output enabled (`get_project_info`'s
`output.enabled`, set via `output.enabledPath`).

`get_frame` reads the composited mixer frame, which sits upstream of the last two links.
If a change doesn't show up in `get_frame`, only the first two links — pattern
contributing, channel enabled and fader — can be the cause.

The reverse also matters: `get_frame` looking correct does not mean pixels reach
fixtures. Master fader and `output.enabled` are downstream of the frame and invisible to
it — and `output.enabled` is exactly the parameter that comes back off after a restart
(see "Assume restarts" above), so check it directly rather than trusting the render.

## Wiring has no depth by default

A new `wire_modulator` wiring starts at zero range and is inert — it exists but has no
visible effect until depth is set. Pass the optional `range` argument (e.g. `1.0` for
full depth) to apply it immediately, or `set_parameter` on the wiring's returned
`rangePath` afterwards. A wiring LX rejects (e.g. a circular dependency) clears
Chromatik's entire undo history as a side effect — that's LX's own `perform()` behavior,
not a bug in this server.

## Batch carefully

`apply_operations` runs up to 50 mutation-tool calls in one engine frame — useful when
several changes need no half-built state rendered in between. It does not make the batch
atomic:

- Validation is all-or-nothing. A missing/non-array `operations`, an empty or
  oversized (>50) array, or any per-entry validation failure (a non-object entry, a
  non-string `tool`, an unknown/read-only/nested tool name, a non-object `args`) fails
  the whole call with a top-level `invalid_argument` and applies nothing. Check `isError`
  first.
- Past validation, execution is continue-on-error. Op 3 failing doesn't stop ops 1, 2, or
  4 through 20 from applying. Per-op outcomes live in `results[]` (`{index, ok, result}`
  or `{index, ok: false, code, message}`) — check both `isError` and `results`.
- It does not collapse into one undo step — each operation still produces its own undo
  entry (or entries), so undoing an N-operation batch takes roughly N presses of Cmd-Z
  (e.g. a wiring created with `range` is itself two undo steps).
- One failing operation can silently wipe undo/redo history for every earlier operation
  in the *same* batch (LX's `perform()` behavior), even though those earlier operations
  still report `ok: true`.
- All operations share one engine frame, so an I/O-heavy operation (e.g.
  `reload_fixtures`, which re-reads every `.lxf` from disk) stalls the whole batch onto
  that frame and can trip the 30s executor timeout — which, per the timeout rule above,
  does not cancel the batch; it still applies once the engine drains it.

See the plugin's bundled `references/addressing.md` for canonical-vs-OSC path details and
`references/error-codes.md` for the full `Result` wire shape.
