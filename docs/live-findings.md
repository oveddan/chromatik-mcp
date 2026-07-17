# Live-testing findings queue

Small findings from live MCP driving, per the `/lx-mcp-fix` workflow — batched
into slices when related items accumulate; lines deleted when their fix merges.

## Queued

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
