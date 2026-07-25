---
title: Driving Chromatik well
description: Second-person guidance for the agent connected over MCP — discovery etiquette, restart handling, timeout semantics, and verifying your own work.
---

## Who this page is for

You are the agent connected to a running Chromatik over MCP. This page is written to
you, not about you — if a human handed you a link to this site, this is the page to
read before you start calling tools. The rest of the site explains the system to
humans; this one is operating instructions for the model in the loop.

## Discover, never guess

Every component and parameter is addressed by its canonical LX path
(`/lx/mixer/channel/1/fader`). Never fabricate one — get it from a discovery call
(`list_channels`, `list_parameters`, `get_views`, `list_available_*`).

Sibling indices are 1-based and **shift when items are removed or inserted**. A path
you held before a structural mutation (add/remove/move a channel, pattern, effect, or
view) may point at the wrong component afterward. Re-list after any structural change
instead of reusing cached paths.

Calls are cheap but not free — each one occupies the LX engine thread. When you're
polling or inspecting a mixer tree, batch: one `list_channels` beats thirty
`get_parameter` calls.

## Assume restarts

Any connection failure — refused connection, a dead session, a tool call that errors
where it didn't before — should be read as "Chromatik may have restarted," not as a
transient glitch to retry past. Recover in order:

1. Re-read `~/.chromatik-mcp/status.json` — the port may have changed.
2. Re-initialize the MCP session against whatever port that file now names.
3. Re-list before reusing any canonical path you held from before the restart.

A restart also resets engine state you might assume is sticky — `output/enabled`, for
instance, comes back off. Don't infer the show is in the state you left it; ask.

## Timeouts are not cancellations

A tool call that times out (`internal: Engine task timed out…`) has **not** been
cancelled. The task stays queued on the engine thread and the mutation still applies
when the engine drains it — so a timeout is not evidence the change failed. Re-read
state to find out what actually happened; never blind-retry a timed-out mutation, or
you risk applying it twice.

More generally, dispatch on the `Result` error code, not the message text: expected
failures return `isError: true` with a stable `"code: message"` body, where `code` is
one of `not_found`, `invalid_argument`, or `internal`. Those three codes are the
contract — build your error handling against them, not against wording that can change.

## Know the instruments

Before reasoning about what a pattern, effect, or modulator actually does — its color
modes, which parameters interact, what a knob's range really controls — call
`get_component_doc` on its class. The catalog entries exist for most stock LX
components and cover exactly the semantics that otherwise get guessed wrong. A
`stale: true` flag in the response means the component's code changed since the doc
was written — trust the live parameter tree over the prose in that case, but read the
doc first regardless.

## Verify your own work

Don't report success on the strength of an unchecked mutation. The loop:

1. **Mutate.** Most mutating tools verify-and-echo — a `set_parameter` response
   echoes the base value it actually landed, so set-then-verify is often already free;
   read the response instead of assuming the call did what you asked.
2. **Look.** `get_frame` returns a cheap summary — non-black fraction, lit fraction,
   mean brightness, dominant colors, an NxN mean-color grid — on every call. It returns a
   token-expensive PNG only when you pass `include_image: true`. Use the summary in a
   tight loop; reach for the PNG at checkpoints, not on every iteration.
3. **Adjust.** If the frame doesn't match what you intended, change the parameter and
   loop back to step 1 — against the actual render, not your mental model of what the
   change should have done.

Only after the change has landed (echo/readback) *and* looks right (frame) should you
tell your human it's done.

## What the server already told you

The MCP `initialize` response carries a server-level `instructions` string — your
client may or may not surface it to you, so it's reproduced here in full:

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

This is sent verbatim in the `initialize` result (`Tools.INSTRUCTIONS` in the server
source) — it's the one thing the server tells every connecting client unprompted, and
this page is the one place it's visible to you as prose rather than buried in a
handshake payload.
