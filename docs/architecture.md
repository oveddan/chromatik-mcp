# Architecture

The contract an integrator builds against: connection, wire shape, addressing, threading,
undo, and the state lifecycle of the embedded MCP server. For installing and connecting,
see the [README](../README.md).

The jar embeds an HTTP MCP server (official Java MCP SDK, streamable-HTTP on embedded
Tomcat) inside the LX runtime as an `LXPlugin`. Any MCP-speaking client — Claude Code,
Claude Desktop, Cursor, Codex, custom orchestrators — connects to it directly and
calls tools that mutate LX state **in-process**: every call reads or mutates the same
live object graph the console renders, and the human and the agent share one undo
stack. No separate server process, no `.lxp` file editing, no reload cycle.

This page is the contract you build against, guarantee by guarantee.

## Connection

- Endpoint: `http://<host>:<port>/mcp`, streamable HTTP. `initialize` returns an
  `Mcp-Session-Id` header your client must echo on every subsequent request; sessions
  die with the Chromatik process, so treat a connection error as "Chromatik restarted"
  and re-initialize.
- Discovery: the plugin writes `~/.chromatik-mcp/status.json` on startup — `{pid,
  port, host, url, projectPath, lxVersion, serverVersion, buildTime, connected,
  lastActivityAt}`. Pin a fixed port instead with `{"port": 3232}` in
  `~/.chromatik-mcp/config.json` (see [the README](../README.md#3-configure-the-port-and-host-optional)).
- The `initialize` result carries server-level `instructions` — mixer semantics,
  addressing rules, quantization behavior. Surface them to your model; they exist to
  prevent the standard first-session mistakes.
- Identity: `get_status` reports the running server's `version` and `buildTime`.
  Compare `buildTime` after any reinstall — LX hot-reloads overwritten jars in a way
  that orphans the old server, which keeps answering with stale code.
- Security: default bind is loopback-only and there is **no authentication layer**. A
  non-loopback bind hands full control of the show to anyone who can reach the
  address (the server logs a warning saying exactly that).

## Wire shape

Every tool call returns the same envelope:

- **Success** — `structuredContent` holds the payload (always a JSON object), with a
  `TextContent` mirror of the same JSON for clients that don't read structured
  output.
- **Expected failure** — `isError: true` with a `"code: message"` text. Codes:
  `not_found` (a path that resolves to nothing), `invalid_argument` (wrong type,
  wrong mode, out-of-range value — the message says what and why), `internal` (a bug;
  it's also logged server-side).

Mutating tools verify their mutation applied before reporting success — LX's command
layer swallows some failures, so a success payload means the engine state actually
changed, not merely that a command was issued.

## Addressing

Everything — components and parameters — is addressed by its canonical LX path
(`/lx/mixer/channel/1/pattern/2/speed`). The rules that keep you out of trouble:

- **Discover, never guess.** Paths come from `list_channels`, `list_parameters`,
  `get_views`, etc. Sibling indices are 1-based and **shift when items are removed or
  inserted** — re-list after structural changes instead of reusing cached paths.
- Discrete/enum parameters accept an **option name string** (`"OSC"`, `"Cube"`) as
  well as an integer index; payloads list the valid `options`.
- A parameter under live modulation reports its **effective value plus `baseValue`**;
  `set_parameter` moves the base — the modulation keeps riding on top.
- Parameter payloads carry an `oscAddress` — the same control surface is reachable
  over OSC (ports in `get_project_info`), so anything you wire an agent to can later
  be mapped from a console.

## Threading and concurrency

Tool handlers never touch `lx.*` from HTTP threads: every call is marshalled onto the
LX engine thread and executed there. Consequences you can rely on:

- Each call is **atomic** with respect to the render loop and to other calls.
- **Concurrent agent sessions are safe** — calls interleave without corrupting
  engine state, and each mutation lands as its own undo step.
- Calls are cheap but not free: they occupy the engine thread, so batch reads (one
  `list_channels`, not thirty `get_parameter`s) when polling.

## Undo

Mutations route through LX's own `LXCommand` system, so every change is one Cmd-Z
step at the console — the operator can unwind an agent's session step by step.
Documented exceptions, called out in the relevant tool descriptions: trigger fires
(including firing a swatch's `recallPath` — the `set_swatch` tool applies the same
swatch undoably), `group_channels` (LX has no explicit-list command to invert), and
`recall_snapshot` (an LX quirk — the undo entry captures post-recall values, so undo
won't restore plain parameters; recall another snapshot instead).

The `undo` and `redo` tools expose that same history to the client, one step at a
time. Two properties matter for anything built on them:

- **The history is global to the running engine**, not scoped to a session. It may
  contain steps made in the UI or by another MCP client. Both responses name the
  command that moved and report whether a further undo or redo is available — read
  that rather than assuming which step you unwound.
- **A failing upstream command clears both stacks** and can leave partially changed
  state. The error reports post-failure availability, which is the signal to stop and
  inspect the affected area rather than retry.

```
tool handler  ──> domain primitive  ──> LXCommand.perform(...)   (mutation with undo)
(MCP-shaped)     (intent, narrow)   ──> direct lx.engine.* edit  (mutation without undo)
                                    ──> read lx.engine.*         (read-only)
```

## State lifecycle

- All mutations are **in-memory**. Nothing touches the project file until someone
  saves — the human in Chromatik, or the agent via `save_project` (`save_model` for
  the structure export). A crash or restart discards unsaved work, so treat saving
  as a deliberate, human-approved step rather than something to sprinkle after every
  mutation.
- After a restart: the port may change (unless pinned), the session is gone, paths
  may resolve differently, and engine state resets (e.g. `output/enabled` can come
  back off). Re-read `status.json`, re-initialize, re-list.

## Knowing the instruments

`get_component_doc` serves generated behavior docs for stock LX patterns, effects,
and modulators — what a component renders, how its parameters interact. Entries are
keyed to **bytecode hashes**, so the response honestly flags `stale: true` when code
changed after the doc was written, and `list_available_*` responses carry
`documented` flags. Consult it before reasoning about a component's behavior; the
parameter tree tells you what knobs exist, not what the algorithm does with them.

## What the server tells every client

The `initialize` result carries a server-level `instructions` string. Your client may or
may not surface it to the model, so it's reproduced here in full — this is the one thing
the server says unprompted, and it exists to prevent the standard first-session mistakes:

> LX mixer semantics: a channel's patternMode is 'playlist' (one active pattern shows)
> or 'blend' (all enabled patterns composite simultaneously, each scaled by its
> compositeLevel parameter, 0-1). For pixels to reach fixtures, the whole chain must
> be on: pattern contributing → channel enabled and fader > 0 → master
> fader > 0 → engine output enabled (see get_project_info's output object).
> Every component and parameter is addressed by its canonical LX path (e.g.
> /lx/mixer/channel/1/fader); use list_parameters on any component path to discover
> its parameters instead of guessing names. Scene colors flow from the global
> palette (get_palette) to palette-linked patterns and effects; recall a saved
> swatch via fire_trigger on its recallPath. A parameter with live modulations
> reports its effective value plus baseValue; set_parameter moves the base. A new
> wire_modulator wiring needs depth: pass its range argument or set rangePath
> afterwards. Views are named model subsets (see get_views), created via add_view; a
> device's view selector clips its rendering to that subset — map a device by
> set_parameter on its 'view' path to the view's label (discrete/selector
> parameters accept an option name string as well as an integer index) — and
> 'Default' inherits the view from the parent device/channel instead. get_tempo
> reports the engine tempo (bpm, clock source, beat position) and its
> launchQuantization: with quantization set, a fire_trigger on a quantized
> trigger (pattern/clip launch) may report pending:true instead of firing
> immediately, deferring to the next tempo boundary. Snapshots (list_snapshots,
> add_snapshot, recall_snapshot) capture and recall whole-look state — mixer,
> pattern, effect, and modulation values together — with an optional fade
> controlled by the engine's transition settings.

Source: `Tools.INSTRUCTIONS` in the server. The house rules an agent should follow on top
of this live in the driving skill:
[agent-plugin/skills/driving-chromatik/SKILL.md](../agent-plugin/skills/driving-chromatik/SKILL.md).
