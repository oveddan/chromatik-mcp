# Live-testing findings queue

Small findings from live MCP driving, per the `/chromatik-mcp-fix` workflow — batched
into slices when related items accumulate; lines deleted when their fix merges.

## Queued

- 2026-08-02 — **RESOLVED (intentional, now documented): lanes 0-based, locators 1-based.**
  Live lint flagged the split (`list_clip_lanes` → `"index": 0` vs `list_locators` →
  `"index": 1`) as a possible off-by-one hazard at the `{"at": "locator:<n>"}` boundary.
  Owner confirmed the split is deliberate: lane `index` mirrors `lane.getIndex()` /
  `move_clip_lane`'s 0-based argument, locators are 1-indexed everywhere (argument, payload,
  path, cursor sugar — `Compositions.locatorSummary` documents the rationale). Recorded in
  docs/tool-conventions.md "Positional addressing" with the agent rule of thumb: path
  ordinals 1-based, lane `index` 0-based, locator `index` 1-based. Remaining (small): the
  `at: locator:<n>` sugar's 1-based n is stated in Schemas' cursor description but could be
  restated in list_locators' description for symmetry. [L1, was MEDIUM → doc'd]

- 2026-08-02 — **get_composition inlines the complete `lanes[]` array.** On a real project
  (130 lanes) the payload is byte-identical in shape to `list_clip_lanes`' output, making
  that tool fully redundant and making the "composition overview" call the heaviest read
  on the server; an agent wanting just transport/markers pays for all lanes every time.
  Fix: trim `get_composition` to counts (it already has `laneCount`/`locatorCount`) or
  gate the array behind an `includeLanes` flag. [L1, LOW]

- 2026-08-02 — **`list_modulations` dead-ends on the `anyLocalModulation` rollup.**
  `list_channels` advertises `anyLocalModulation: true` on `/lx/mixer/channel/1`, but
  `list_modulations {"scope":"/lx/mixer/channel/1"}` errors
  `invalid_argument: Not a device or modulation engine at path ... (found LXChannel)` —
  the natural follow-up to the rollup marker fails, forcing a walk of every
  pattern/effect checking `hasLocalModulation`. Fix: accept a bus scope and aggregate its
  devices' engines, or extend the error text to point at the per-device workflow. [L1, LOW]

- 2026-08-02 — **catalog gaps for live custom components.**
  `apotheneum.jvyduna.patterns.bliss.Mountains`, `...bliss.Terraform`, and
  `apotheneum.jvyduna.effects.CompositionAutomation` (all actively contributing in the
  live project) return `documented: false` from `get_component_doc`, so the per-class
  no-op lint cannot vouch for the most-live custom components. Catalog follow-up — add
  `catalog/<fqcn>.md` entries; no server code change. [L1, LOW]

- 2026-08-02 — **`add_channel` silently auto-creates composition lanes; echo doesn't say
  so.** Live E2E: `add_channel` bumped laneCount 126→129 (bus + midiNote + pattern lanes
  for the new channel) before any lane was explicitly added; the only clue was the
  laneCount in a later `add_clip_lane` response. Benign LX behavior, but the `add_channel`
  echo (or its description) should disclose the lane side-effect so agents don't misread
  concurrent-mutation. [L2, doc/payload gap]

- 2026-08-02 — **`go_locator` while transport is stopped moves the insertMarker, not the
  playhead.** Live E2E: `go_locator` with `launched:false, running:false` left the
  playhead untouched and moved the insert marker — DAW-correct, but callers expecting a
  playhead jump will be surprised. Fix: state this in the `go_locator` description (and
  the echo already reports `launched`/`running`, which helps). [L2, doc gap]

- 2026-08-02 — **future tool candidate: `send_osc` over LXOscEngine.sendMessage (new in
  LX 1.2.2).** `LXOscEngine.sendMessage(String, Object...)` / `(String, OscArgument...)`
  (LXOscEngine.java:357-379) is the first public API for emitting *arbitrary* outbound
  OSC — to lighting desks, Ableton, Resolume — rather than only mirroring parameter
  changes. Fans out to the engine transmitter plus every `LXOscConnection.Output`,
  auto-boxing Integer/Long/Float/Double/String/Boolean. Two gotchas from the source for
  whoever builds it: it is a silent no-op when no transmitter is active, and addresses
  matching an output's OSC filter are dropped — so the tool must read back
  `lx.engine.osc` state and report "sent to N outputs", never a bare success. Not
  implemented in the 1.2.2 bump (out of scope by design).

- 2026-07-20 — **status.json stale while the server was up.** Live session
  found the running Chromatik (pid 42911) serving MCP on port 55230 while BOTH
  discovery files disagreed: legacy `~/.lx-mcp/status.json` (dead pid 75845,
  port 52429, pre-rename) and `~/.chromatik-mcp/status.json` (pid 46296, port
  55342 — also not the running instance). Discovery only worked by `lsof`-ing
  the process. Plugin writes the file on startup only; the running instance
  either failed the write or holds a pre-rename jar. Fix ideas: rewrite on a
  heartbeat (mtime doubles as liveness), or verify-on-read (probe pid/port
  before trusting); at minimum log the write failure loudly. Also: delete or
  tombstone the legacy `~/.lx-mcp/` file post-rename so old clients fail fast
  instead of reading stale data. Root-cause before slicing.

- 2026-07-16 — **v2: auth token for non-loopback binds.** `~/.chromatik-mcp/config.json`
  already supports `host`/`port` (remote works today, with a security warning
  logged at startup — ChromatikMcpPlugin.java:42-47). V2: require a bearer token when
  the bind isn't loopback; refuse to start open otherwise. Deferred by user
  decision 2026-07-16.
- 2026-07-16 — **mDNS/Bonjour advertisement** (`_chromatik-mcp._tcp`) for zero-config
  discovery across machines — no status.json read, no pinned host needed; a
  remote client just browses the LAN for the rig. Pairs with the auth-token
  item above (advertising an open unauthenticated server is worse than a
  quiet one, so token lands first or together). Deferred as a nice-to-have,
  user request 2026-07-16.

## In flight

(none)

## Merged (2026-07-17 — PR-9 gap-sweep fan-out, planned slices rather than live findings)

- get_tempo + engine globals (#55); snapshots (#59); palette mutation (#57); mixer
  performance surface in list_channels (#58).
- view lifecycle now undoable via LXCommand.Structure.AddView/RemoveView (#56) —
  closes the round-3 "not undoable" caveat below. The remove_view index-clamp trap
  remains (undo restores the view definition, not stale device assignments).
- Deliberately skipped: channel group/ungroup tools — LXCommand.Mixer.GroupSelectedChannels
  reads UI selection state, not an explicit channel list. Revisit if LX grows an
  explicit-list command.
- Known LX quirk (documented + pinned by test, not fixable server-side): undo after
  recall_snapshot is a no-op for plain parameters (LX captures undo state post-mutation).

## Merged (2026-07-16 round 3)

- views write surface: add_view/remove_view + set_parameter by option name (#48).
  Trap documented in remove_view: LX clamps device selector indices on removal —
  devices mapped to the removed view silently reassign to whatever view takes
  that index, NOT to Default.

- CoreMIDI test deadlock: warmup listener + gate watchdog + single-flight lock (#42) + CI job timeout (#41)
- UI plugin auto-enable — one checkbox (#43)
- add/doc tools accept short type names from list_available_* (#44)
- get_status/status.json server identity (version + buildTime) (#45)
- views read surface: /lx/structure resolution + get_views (#46)
- remove_modulator tool (#47)
