# chromatik-mcp — build plan (spike phase complete; tool-surface rollout in progress)

## Context

Original plan was Node + filesystem `.lxp` editing + Java watcher (17 PRs). Feedback from Mark Slee (LX creator) and Tracy Scott collapsed it to a Java-only architecture:

- MCP server lives inside the LXPlugin jar; streamable-HTTP transport; works with any MCP-speaking agentic platform (Claude Code, Claude Desktop, Cursor, Codex, custom orchestrators). No client-specific assumptions in the server.
- Mutations go through `LXCommand` (Mark confirmed the whole API surface is one source file, organized as static inner classes — `LXCommand.Parameter.SetValue`, `LXCommand.Modulation.AddModulator`, etc.) — gives undo for free.
- No Node, no `.lxp` editing, no file watcher, no TS mirror of LX semantics.

The original "PR-1 spike" bundled five distinct deliverables (SDK feasibility, LXCommand inventory, embedding pattern, QA strategy, Phase-2 skim) under one review — too big. Split into three small spike PRs, each independently reviewable, mostly parallelizable. Each is executed by its own 4-agent pipeline (Research → Analysis → Writing → Review).

A **PR-0 scaffold step** lands first to give the spike PRs a buildable Java project to work in.

> **How this gets built:** the agent dev loop that produces these PRs — pipeline, objective gates, failure modes — is documented in [`loop-engineering.md`](loop-engineering.md), with the `/lx-mcp-loop` skill (`.claude/skills/lx-mcp-loop/`) as the operational checklist.

## Progress tracker

Sessions update this as work lands. Mark `[x]` when a PR is merged to `main`; leave a one-line note (branch / PR link / blocker) after the dash. Keep it honest — `[~]` means in-progress, `[ ]` not started.

- [x] **PR-0** — Java/Maven scaffold — merged via [#1](https://github.com/oveddan/chromatik-mcp/pull/1); scaffold builds, headless load gate passes
- [x] **PR-1a** — Java MCP SDK feasibility (go/no-go gate) — merged via [#3](https://github.com/oveddan/chromatik-mcp/pull/3); **GO**. `io.modelcontextprotocol.sdk:mcp:2.0.0-RC1` on embedded Tomcat; in-process `initialize` embed test + headless load gate both green. See `docs/sdk-feasibility.md`.
- [x] **PR-1b** — LXCommand inventory + tool mapping — merged via [#5](https://github.com/oveddan/chromatik-mcp/pull/5); deliverable `docs/lxcommand-mapping.md` (every v1 tool maps 1:1 to an LXCommand; no `compose_scene`; engine-thread concurrency flagged as top risk).
- [x] **PR-1c** — Automated QA strategy — merged via [#6](https://github.com/oveddan/chromatik-mcp/pull/6); deliverable `docs/qa-strategy.md` (LX confirmed headless-testable; do→undo→assert as built-in correctness check; engine-thread concurrency test shape). Added `HeadlessLxHarnessTest` (executable gate; since retired — the domain suite exercises the same ground) + `.github/workflows/build.yml` (CI).
- [x] *Spike-phase gate*: all three deliverables exist + all Review agents PASS + embed test runs
- [x] **PR-2** — Embed HTTP MCP server + status file — merged via [#7](https://github.com/oveddan/chromatik-mcp/pull/7); `tools/list` works, `chromatikmcp.engine.EngineExecutor` (engine-thread serialization, the #1-risk mechanism) + concurrency regression test landed. Jar slimming deferred — see follow-up below.
- [x] **PR-3** — Read-only discovery tools + wire-shape decisions — merged via [#9](https://github.com/oveddan/chromatik-mcp/pull/9); conventions recorded in `docs/tool-conventions.md`
- [x] **PR-3b** — Path/entity resolver (`resolve(lx, path)` domain primitive; prerequisite for every path-taking mutation) — merged via [#12](https://github.com/oveddan/chromatik-mcp/pull/12) (replaced auto-closed [#10](https://github.com/oveddan/chromatik-mcp/pull/10)); typed failures NOT_FOUND / TYPE_MISMATCH / INVALID_PATH
- [x] **PR-4** — First mutation (`add_macro_knob`) via LXCommand — merged via [#11](https://github.com/oveddan/chromatik-mcp/pull/11); also fixed the deployment blocker (SDK ServiceLoader vs Chromatik's child classloader — TCCL swap in `EmbeddedMcpServer.start`), made `verify-load.sh` deployment-faithful, and bound the server to 127.0.0.1. **Live Chromatik demo passed 2026-06-11**: plugin enabled in preferences, MCP handshake + add_macro_knob over HTTP against a running Apotheosis project (18 channels), knob bank appeared, Cmd-Z removed it
- [x] **PR-5a** — `set_parameter` (first fan-out slice; reuses the resolver) — merged via [#14](https://github.com/oveddan/chromatik-mcp/pull/14); `Parameters.set` dispatches on runtime type (String/Boolean/Discrete-int/numeric) via `LXCommand.Parameter.*`, verified per-perform via `getUndoCommand()`; rejects all `AggregateParameter`s (components individually addressable), momentary triggers (`fire_trigger` is future work), and out-of-range discrete ints (LX wraps); snapshot echoes the base (unmodulated) value; do→undo→assert per type. Hardened by a Fable multi-angle review (5 confirmed findings fixed)
- [x] **PR-5b** — Channels / patterns / effects tools — merged via [#22](https://github.com/oveddan/chromatik-mcp/pull/22); nine tools (`add/remove_channel`, `add/remove/activate/move_pattern`, `add/remove/move_effect`) via `Channels` primitives; pre-checked hazards: locked effects and out-of-range indices (LX throws inside perform → undo-stack wipe), BLEND-mode `activate_pattern` (goPattern silently no-ops outside PLAYLIST); pattern primitives use `getEngine()` so PatternRack-hosted patterns resolve; effect containers = channel/master/pattern
- [x] **PR-5c** — Modulators + modulation-routing tools — merged via [#15](https://github.com/oveddan/chromatik-mcp/pull/15); `add_modulator` (registry-resolved class, global or device-scoped via `scope`, subsumes and removes `add_macro_knob`), `wire_modulator` + `wire_trigger` (engine inferred from source, scope pre-validated — LX registers the graph edge before its own scope check), `remove_modulation` (path-addressed, both kinds); `oscAddress` exposed in parameter payloads and per-parameter in `add_modulator`'s response (label-based modulator addresses ≠ canonical paths — `docs/osc-addressing.md`); `get_project_info` reports OSC ports
- [ ] **PR-5d** — MIDI mapping tool —
- [~] **PR-6** — Install docs + multi-agent usage examples + README rewrite — branch `pr-6-install-docs`; `docs/install.md` (build → `mvn install -Pinstall` → enable in Preferences → status.json discovery with pid-liveness caveat → client connection incl. Claude Code one-liner → troubleshooting), `docs/usage-examples.md` (five agent flows: understand / build structure / chain effects / macro-map with OSC / multi-agent patterns), README quick-start + structure-tools + catalog capabilities sections (capabilities base landed via [#19](https://github.com/oveddan/chromatik-mcp/pull/19); license: MIT, 2026-07-08)
- [x] **PR-7a** — Semantic catalog: format contract (`docs/catalog-format.md`), `lx-mcp-catalog` generation skill (hash-keyed incremental, Sonnet summarizers, portable sources config), initial 24 stock-LX pattern/effect entries in `package/src/main/resources/catalog/`, and the catalog-freshness step added to the dev loop — merged via [#20](https://github.com/oveddan/chromatik-mcp/pull/20). Docs are a cache of source understanding; three-tier runtime resolution (overlay → class's own jar → chromatik-mcp jar) so drop-in content jars can ship their own docs; all 24 entries fact-checked against LX source after the first pass showed a ~50% error rate
- [x] **PR-7b** — `get_component_doc` MCP tool + `domain/Catalog.java` (three-tier lookup: `~/.chromatik-mcp/catalog/` overlay → class's own jar → absent; flat-frontmatter parse, bytecode-hash staleness false/true/"unknown" with per-class cache), `documented` flag in `list_available_*`, `CatalogFormatTest` gate (walks every entry inside `mvn package`) — merged via [#23](https://github.com/oveddan/chromatik-mcp/pull/23) (reopened [#21](https://github.com/oveddan/chromatik-mcp/pull/21), auto-closed on stack-base delete)
- [ ] **PR-7c** — Catalog batches: stock LX modulators + Apotheneum (generated into that repo's own `src/main/resources/catalog/`) —
- [x] **PR-8** — `get_frame` render preview (the Phase-2 visual-loop keystone, pulled forward) — merged via [#29](https://github.com/oveddan/chromatik-mcp/pull/29); reads back the last completed engine frame via `copyFrameThreadSafe`, returns a PNG rendering (MCP ImageContent, `Result.OkImage` seam extension, encode on the HTTP worker thread) + compact summary (non-black fraction, mean brightness, dominant colors, NxN mean-color grid); token levers: `include_image=false`, `grid`, small `width`
- [ ] *Follow-up (deferred from PR-2)* — slim the shaded jar: exclude unused Tomcat submodules / optional deps to drop the non-fatal `ClassNotFoundException: jakarta.mail.Authenticator` at load and shrink the ~9 MB artifact —
- [ ] *Follow-up* — status.json lifecycle: delete on `dispose()`, rewrite when the open project changes (LX listener), document the pid-liveness check as the client contract (file is currently written once at startup and never cleaned up; two Chromatik instances overwrite each other) —
- [ ] *Follow-up* — bump MCP SDK `2.0.0-RC1` → GA when released —
- [ ] *Follow-up (community feedback, 2026-07-09)* — **search/filter tools for small-context agents**: `list_channels` returns the whole mixer tree, which blows up context on large projects when a cheap driver model lists everything to find one thing (reported from an Eve/deepseek deployment). Add a `search_components {query, kind?}` (name/label/class/tag match → paths only) and/or path-prefix + depth filters on the list tools, so targeted lookup never requires a full dump —
- [x] *Follow-up (from PR-5c)* — `list_modulations` + `fire_trigger` — merged via [#18](https://github.com/oveddan/chromatik-mcp/pull/18) (reopened [#16](https://github.com/oveddan/chromatik-mcp/pull/16), auto-closed on stack-base delete); README capabilities summary merged via [#19](https://github.com/oveddan/chromatik-mcp/pull/19); `list_modulations {scope?}` snapshots one engine's live modulators (with OSC addresses) + continuous/trigger wirings (with rangePath); `fire_trigger {path}` pulses a TriggerParameter or momentary boolean — deliberately NOT command-backed (firing is an action, not undoable state; the auto-reset leaves nothing for Cmd-Z to restore) —
- [x] *Resolved (2026-06-10)* — `save_project` persistence tool deferred to **Phase 2**: v1 mutations stay in-memory; the user saves manually in Chromatik —

Legend: `[ ]` not started · `[~]` in progress · `[x]` merged. When you pick up a PR, set it to `[~]` and put your branch name after the dash so parallel sessions don't collide.

### Lessons learned

Distilled rules from work that landed — write them here (and in file-based memory), not in chat, so the next session consults them instead of re-deriving. The dev loop that produces these PRs is documented in [`loop-engineering.md`](loop-engineering.md).

- **`lx.command.perform()` swallows command failures** (pushes a UI error, wipes undo/redo, returns normally). Mutation primitives must verify by state-read and throw if the command didn't apply — an unverified `get(size-1)` read-back returns the wrong object on failure. (PR-4; now in `tool-conventions.md`.)
- **The MCP SDK resolves its JSON mapper via the thread-context classloader**, which Chromatik's child `LXClassLoader` is never set as. `EmbeddedMcpServer.start` swaps the TCCL for startup. CI missed this because tests have the SDK on the system classpath — `verify-load.sh` now uses a deployment-faithful (LX-only) classpath. (PR-4.)
- **Repeated `new LX()` in one JVM deadlocks** on the JDK-global javax.sound/CoreMIDI lock. Construct LX once per test class; surefire has a fork timeout so a wedged fork fails the build instead of hanging it. (PR-3.)
- **`new LX(model)` does not reindex points.** `LXPoint.index` comes from a JVM-global counter and only `LXStructure.setStaticModel` calls `reindexPoints()` — the immutable-model constructor path does not. So the second headless `new LX(new GridModel(...))` in one JVM has points indexed 64+, silently breaking any per-point buffer access (patterns that only `setColors(...)` never notice). Test harnesses must construct with `new GridModel(...).reindexPoints()`; `Frames.capture` throws a descriptive error if it sees a mis-indexed model. (PR-8.)

## PR-0 — Java/Maven scaffold (pre-step)

Mirrors the Apotheneum convention (`/Users/danoved/Source/Apotheneum`) so contributors familiar with one project recognize the other.

**Files to create**:

- `package/pom.xml` — derived from `Apotheneum/pom.xml`. Standalone (no parent POM), Java 21 (`maven.compiler.release=21`), `com.heronarts:lx:1.2.1` as `provided`, resource filtering enabled for `src/main/resources/lx.package`, install profile that copies the built jar to `~/Chromatik/Packages/`. Update coordinates to `groupId=co.chromatikmcp`, `artifactId=chromatik-mcp`, `version=0.0.1-SNAPSHOT`.
- `package/src/main/resources/lx.package` — JSON descriptor with the same `name`/`mediaDir`/`author`/`url`/`build` shape as Apotheneum. `name: "Chromatik-MCP"`.
- `package/src/main/java/chromatikmcp/ChromatikMcpPlugin.java` — stub `implements LXPlugin` with `@LXPlugin.Name("Chromatik-MCP")`. Empty `initialize(LX lx)`.
- `package/.gitignore` — Maven + IDE artifacts (`target/`, `*.class`, `.settings`, `.project`, `.classpath`, `.idea/`).

**Not in scope for PR-0**: no MCP SDK dependency yet (that lands as part of PR-1a once the version is pinned), no domain code, no tests beyond the build itself.

**Execution**: small enough to skip the 4-agent pipeline. One implementer pass + user review.

**Verification**:
- `cd package && mvn package` succeeds; produces `target/chromatik-mcp-0.0.1-SNAPSHOT.jar`.
- `mvn -Pinstall install` copies the jar to `~/Chromatik/Packages/`.
- Restart Chromatik; "Chromatik-MCP" appears in the installed-packages list. Plugin contributes nothing yet — that's expected.

## Spike PRs

### PR-1a — Java MCP SDK feasibility (the go/no-go gate)

Smallest, most consequential. If this fails, the whole architecture pivot is wrong.

Open questions:
- Does the official Java MCP SDK (`modelcontextprotocol/java-sdk`) support streamable-HTTP transport at the maturity we need? Which version?
- Can the SDK be embedded inside a long-running JVM (LX) without taking over main?
- What's the embedding pattern — the rough shape of starting the MCP server from `LXPlugin.initialize(lx)`?
- Port discovery: confirm `~/.chromatik-mcp/status.json` with `{pid, port, projectPath, lxVersion}` is the right handshake.

Output: `docs/sdk-feasibility.md` + a runnable embed test that accepts an MCP `initialize` request.

### PR-1b — LXCommand inventory + tool mapping

Read-only investigation of LX's command surface. Independent of PR-1a.

Open questions:
- Walk `heronarts/lx/command/LXCommand.java` (in the LX source repo) and enumerate every inner-class action (`LXCommand.<Category>.<Action>` + constructor signature).
- For each planned tool (`add_channel`, `set_parameter`, `add_modulator`, `wire_modulator`, `add_midi_mapping`, etc.), map to a concrete `LXCommand` action — or "needs direct in-memory edit, document undo skip."
- Tool granularity: do fine-grained primitives compose well for multi-agent fan-out, or do we also need a higher-level `compose_scene` tool?
- **Phase-2 capability skim** (light, not blocking): does LX expose pattern/effect source or class metadata so a future agent could reason about pattern algorithms? Just enumerate — the tool itself waits.

Output: `docs/lxcommand-mapping.md` with the mapping table front and center.

### PR-1c — Automated QA strategy

Design doc. Mostly independent; the one dependency on PR-1b is "which tools use LXCommand-backed undo verification" — a small late edit, not a structural block. Can start in parallel.

Open questions:
- Can `LX` run headless in tests? (Check LX's own `src/test/` for existing patterns.)
- Default per-tool test shape: domain primitive unit test + MCP-handler integration test.
- For `LXCommand`-backed mutations: use **do → undo → assert state restored** as a built-in correctness check. Catches "did we actually use a real LXCommand?" for free.
- For direct-edit mutations: define the fallback verification (likely state-snapshot diff + manual rollback in teardown).
- Can the embedded HTTP MCP server be tested in-process from JUnit? (Cross-checks PR-1a's findings.)
- What runs in CI (headless) vs. local-only (live LX)?
- Multi-agent workflow tests: out of scope for v1, flagged for manual/recorded verification in PR-7.

Output: `docs/qa-strategy.md` — concrete patterns + a verification template that PR-2 onwards fills in per-tool.

**Testability assumption + escalation rule**: the default expectation is that LX is testable from JUnit (it's a normal Java library); PR-1c's job is to confirm this and document the patterns. If PR-1c finds a real blocker — LX requires a display/GL context with no headless mode, the Java MCP SDK can't be tested in-process, etc. — that is an **architecture-level escalation**, not something the QA-strategy agent should quietly work around. The Writing Agent surfaces the blocker in `docs/qa-strategy.md`'s TL;DR, and the Review Agent flags it as FAIL with the blocker description. We then re-plan before PR-2.

## Per-PR execution: 4-agent pipeline

Each spike PR runs the same pipeline. Sequential. Each agent writes one file under `docs/spike/<pr-id>/`; the next agent reads it. No other handoff state. (Those pipeline artifacts were removed after the spike phase completed; the three canonical deliverables were promoted to `docs/`.)

**1. Research Agent** (read-only; Explore-type)
- *Reads*: the source files and external docs declared per PR (LXCommand.java for 1b, MCP Java SDK docs for 1a, LX/src/test/ for 1c, etc.).
- *Output*: `01-research-notes.md` — raw facts and citations, no decisions.

**2. Analysis Agent**
- *Reads*: research notes.
- *Job*: produce decisions for every open question in the PR. Structured reasoning, not prose.
- *Output*: `02-analysis.md`.

**3. Writing Agent**
- *Reads*: analysis + research notes (for citations).
- *Job, part 1*: synthesize the canonical PR artifact (`sdk-feasibility.md` / `lxcommand-mapping.md` / `qa-strategy.md`). Scannable structure: TL;DR, the central table or test pattern, one section per remaining open question.
- *Job, part 2 — docs sync*: audit the canonical doc set for any statement now contradicted or made stale by these findings, and produce updates. **Files to audit**: `README.md`, `CLAUDE.md`, `docs/build-plan.md`. Touch only what's stale — do not rewrite sections that are still correct. If nothing is stale, say so explicitly in the output (don't silently skip).
- *Output*: the PR's deliverable file + any updates to the audited docs.

A dedicated docs-sync agent would be over-engineering at this scale (~3 canonical docs). If the docs surface grows substantially post-MVP, the audit step can be split out into its own agent then.

**4. Review Agent**
- *Reads*: the deliverable + the PR's question list + the research notes + the diff of any audited-doc updates.
- *Job*: independently verify every question has a defended answer; spot-check claims against research notes; flag unsourced assertions and hidden assumptions. Also verify the docs-sync audit: are the audited-doc updates accurate, and is there anything the Writing Agent missed (a stale statement in `README.md`/`CLAUDE.md`/`docs/build-plan.md` that should have been updated but wasn't)?
- *Output*: `04-review.md` with PASS / FAIL+gaps. On FAIL, route gaps back to the appropriate upstream agent and re-run downstream.

**Handoff rule**: each agent reads only its declared inputs, writes only its declared output. If a downstream agent needs information not in an upstream artifact, that's a defect in the upstream agent's scope.

## Spike-phase dependencies

```
PR-0 (Java scaffold) ──┬──> PR-1a (SDK feasibility) ──┐
                       │                              │
                       │   PR-1b (LXCommand map) ─────┼──> spike-phase complete ──> downstream PRs
                       │                              │
                       └──> PR-1c (QA strategy) ──────┘
                                  (small dependency on PR-1b's table)
```

PR-0 lands first — it's the minimum buildable Java project. PR-1a depends on PR-0 because its embed test needs a Maven project to live in. PR-1b and PR-1c are read-only docs; they don't strictly need PR-0, but landing it first makes their references concrete. After PR-0, PR-1a/1b/1c run in parallel.

## Spike-phase verification

- PR-0's `mvn package` builds; Chromatik-MCP appears in Chromatik's installed packages.
- All three spike deliverable files exist (`docs/sdk-feasibility.md`, `lxcommand-mapping.md`, `qa-strategy.md`).
- All three Review agents returned PASS.
- The runnable embed test from PR-1a starts and accepts an MCP `initialize` request.

## Downstream PRs (post-spike roadmap)

Planned in detail now that the spike findings are in. Each PR stays independently demoable; anything bigger than these slices is too big (see the scope guard in `CLAUDE.md`).

- **PR-2** — Embed HTTP MCP server in the plugin; write status file; `tools/list` works. (In progress — see tracker.)

- **PR-3** — Read-only discovery tools. `get_project_info` plus the discovery set agents need before any mutation is composable: `list_channels` (channels with their patterns/effects), `list_available_patterns` / `list_available_effects` / `list_available_modulators` (from `LXRegistry` — without these, `add_pattern` args are unguessable), and `get_parameter`. Proves the tool-registration pattern end-to-end.
  - Decisions to settle here, since they shape every later tool:
    - `Result<T>` wire shape over MCP — `isError` + text content vs. structured content; check whether the Java SDK (2.0.0-RC1) supports `outputSchema`/structured tool output.
    - Tool naming convention (verb_noun snake_case, singular vs. plural, etc.) — fixed once, before the fan-out.
    - Canonical entity addressing — LX OSC path (e.g. `/lx/mixer/channel/3/pattern/2/...`) vs. component id. Decide here, where read tools exercise it cheaply; PR-3b implements the resolver.
  - Verification: JUnit integration tests call each tool over in-process HTTP against the headless harness; live check from any MCP client against a running Chromatik.

- **PR-3b** — Path/entity resolver. The `resolve(lx, path) → component/parameter` domain primitive that PR-1b's review flagged as a hard prerequisite for roughly half the v1 tool surface (`set_parameter`, `wire_modulator`, `add_midi_mapping`, every `remove_*`). Implements the addressing convention decided in PR-3. Unit tests against the headless harness: valid paths, missing components, type mismatches — each mapping to a typed error. Lands before any mutation that takes a path argument.

- **PR-4** — First mutation (`add_macro_knob`) via `LXCommand`. Also nails the `Result.error` mapping pattern at the tool seam — the first mutation is the first real error surface.
  - Verification: do→undo→assert in JUnit; live demo: knob appears in Chromatik, Cmd-Z undoes.

- **PR-5 fan-out** — the rest of the v1 tool surface, pre-sliced so parallel sessions don't collide (one tracker line each). Each slice follows the per-tool template in `docs/qa-strategy.md`: domain primitives + handlers + do→undo→assert tests.
  - **PR-5a** — `set_parameter`. First: it reuses the PR-3b resolver, has the highest leverage, and exercises the polymorphic `SetValue`/`SetNormalized`/`SetString`/`SetColor` dispatch.
  - **PR-5b** — channels + patterns + effects (`add_channel`, `remove_channel`, `add_pattern`, `remove_pattern`, `add_effect`, `remove_effect`).
  - **PR-5c** — modulators + routing (`add_modulator`, `wire_modulator` continuous + trigger, `remove_modulation` both kinds).
  - **PR-5d** — MIDI mapping (`add_midi_mapping`).

- **PR-6** — Install docs for multiple agentic platforms + multi-agent usage examples + README rewrite + **license decision** (currently TBD; must land before this publicity step). Bump the MCP SDK to GA here if it has been released by then.

- **PR-9 fan-out** — surface unexposed LX functionality (gap sweep 2026-07-17: chromatik-mcp uses 12 of ~110 `LXCommand` classes; these slices close the highest-value gaps, all parallel-safe). All five merged 2026-07-17:
  - **PR-9a** (#55) — tempo + engine globals: `get_tempo` read tool (bpm, clock source INTERNAL/MIDI/OSC, beats-per-bar, launch quantization, tap/nudge paths, beat trigger source); `get_project_info` gains engine `speed`/`framesPerSecond` and output `brightness`/`gamma` paths. Plain parameters — discoverability, not new mutation.
  - **PR-9b** (#59) — snapshots: `list_snapshots` / `add_snapshot` / `recall_snapshot` (transitioned or immediate) / `update_snapshot` / `remove_snapshot` via `LXCommand.Snapshots.*`. Known LX quirk (pinned by test): undo of a recall is a no-op for plain parameters — LX builds the per-view undo entry after mutating the value.
  - **PR-9c** (#57) — palette mutation: `save_swatch` / `set_swatch` / `remove_swatch` / `move_swatch` / `add_color` / `remove_color` via `LXCommand.Palette.*` (palette was read-only).
  - **PR-9d** (#58) — mixer performance surface: `list_channels` gains crossfader + A/B crossfade groups, cue/aux, per-channel blend mode, auto-mute, pattern auto-cycle/transition settings (paths included). Grouping tools skipped: `LXCommand.Mixer.GroupSelectedChannels` sources channels from UI selection state, not an explicit list.
  - **PR-9e** (#56) — view lifecycle undoability: `add_view`/`remove_view` now route through `LXCommand.Structure.AddView`/`RemoveView` (they exist — corrects the "no command exists for view lifecycle" assumption from the original views slices). Undo of remove restores the view definition but not stale device assignments..

**Phase 2 (post-MVP, not in the current PR list):**

These are scoped and feasibility-checked in [`loop-engineering.md` → Future explorations](loop-engineering.md#future-explorations); pick them up from there.

- **Runtime visual self-correction loop** (was "Visual-feedback agent"): an agent composing a show reads its rendered output, then a verifier sub-agent grades it against a written visual rubric and the agent self-corrects. **Keystone shipped in PR-8** (`get_frame`: PNG rendering + summary readback); what remains of this item is the loop itself — the verifier-sub-agent grading pattern and a written visual rubric.
- **Pattern/effect comprehension agent**: choose patterns by behavior. **Constraint recorded** — LX exposes no source/bytecode surface, only registry metadata + annotations + the parameter tree (what knobs exist, not what the algorithm does). The metadata-level version is buildable now; algorithm-level comprehension needs filesystem source-read or a decompiler, outside LX's API.
- **Formal verifier + objective `/goal` gate for the dev loop**: bind "done" to `mvn package` + headless harness + do→undo→assert green, checked by an independent verifier separate from the implementer (the maker/checker split). Today the review agent is ad hoc; formalize when the fan-out volume makes hand-spawning a reviewer per PR the bottleneck.

```
PR-0 ──┬──> PR-1a ┐
       │   PR-1b ├──> PR-2 ──> PR-3 ──> PR-3b ──> PR-4 ──┬──> PR-5a ──> PR-5b / PR-5c / PR-5d (parallel)
       └──> PR-1c ┘                                       └──> PR-6 (docs)
```
