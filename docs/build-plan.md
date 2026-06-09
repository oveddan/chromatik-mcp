# lx-mcp — spike phase plan (three small PRs, each an agent pipeline)

## Context

Original plan was Node + filesystem `.lxp` editing + Java watcher (17 PRs). Feedback from Mark Slee (LX creator) and Tracy Scott collapsed it to a Java-only architecture:

- MCP server lives inside the LXPlugin jar; streamable-HTTP transport; works with any MCP-speaking agentic platform (Claude Code, Claude Desktop, Cursor, Codex, custom orchestrators). No client-specific assumptions in the server.
- Mutations go through `LXCommand` (Mark confirmed the whole API surface is one source file, organized as static inner classes — `LXCommand.Parameter.SetValue`, `LXCommand.Modulation.AddModulator`, etc.) — gives undo for free.
- No Node, no `.lxp` editing, no file watcher, no TS mirror of LX semantics.

The original "PR-1 spike" bundled five distinct deliverables (SDK feasibility, LXCommand inventory, embedding pattern, QA strategy, Phase-2 skim) under one review — too big. Split into three small spike PRs, each independently reviewable, mostly parallelizable. Each is executed by its own 4-agent pipeline (Research → Analysis → Writing → Review).

A **PR-0 scaffold step** lands first to give the spike PRs a buildable Java project to work in.

## Progress tracker

Sessions update this as work lands. Mark `[x]` when a PR is merged to `main`; leave a one-line note (branch / PR link / blocker) after the dash. Keep it honest — `[~]` means in-progress, `[ ]` not started.

- [x] **PR-0** — Java/Maven scaffold — merged via [#1](https://github.com/oveddan/lx-mcp/pull/1); scaffold builds, headless load gate passes
- [x] **PR-1a** — Java MCP SDK feasibility (go/no-go gate) — merged via [#3](https://github.com/oveddan/lx-mcp/pull/3); **GO**. `io.modelcontextprotocol.sdk:mcp:2.0.0-RC1` on embedded Tomcat; in-process `initialize` embed test + headless load gate both green. See `docs/spike/sdk-feasibility.md`.
- [x] **PR-1b** — LXCommand inventory + tool mapping — merged via [#5](https://github.com/oveddan/lx-mcp/pull/5); deliverable `docs/spike/lxcommand-mapping.md` (every v1 tool maps 1:1 to an LXCommand; no `compose_scene`; engine-thread concurrency flagged as top risk).
- [x] **PR-1c** — Automated QA strategy — merged via [#6](https://github.com/oveddan/lx-mcp/pull/6); deliverable `docs/spike/qa-strategy.md` (LX confirmed headless-testable; do→undo→assert as built-in correctness check; engine-thread concurrency test shape). Adds `HeadlessLxHarnessTest` (executable gate) + `.github/workflows/build.yml` (CI).
- [x] *Spike-phase gate*: all three deliverables exist + all Review agents PASS + embed test runs
- [~] **PR-2** — Embed HTTP MCP server + status file — branch `claude/jolly-swanson-c3b04c`. Embed + status file already landed in PR-1a; this PR makes `tools/list` work (server advertises the tools capability via the `EmbeddedMcpServer.start(..., tools)` overload) and lands the engine-thread serialization executor (`lxmcp.engine.EngineExecutor`, the #1-risk mechanism the spike docs assign here) + its concurrency regression test. No tools yet (PR-3). Jar slimming deferred — see follow-up below.
- [ ] **PR-3** — First read-only tool (`get_project_info`) —
- [ ] **PR-4** — First mutation (`add_macro_knob`) via LXCommand —
- [ ] **PR-5** — Tool-surface fan-out (channels / patterns / modulators / routing / MIDI / set_parameter) —
- [ ] **PR-6** — Install docs + multi-agent usage examples + README rewrite —
- [ ] *Follow-up (deferred from PR-2)* — slim the shaded jar: exclude unused Tomcat submodules / optional deps to drop the non-fatal `ClassNotFoundException: jakarta.mail.Authenticator` at load and shrink the ~9 MB artifact —

Legend: `[ ]` not started · `[~]` in progress · `[x]` merged. When you pick up a PR, set it to `[~]` and put your branch name after the dash so parallel sessions don't collide.

## PR-0 — Java/Maven scaffold (pre-step)

Mirrors the Apotheneum convention (`/Users/danoved/Source/Apotheneum`) so contributors familiar with one project recognize the other.

**Files to create**:

- `package/pom.xml` — derived from `Apotheneum/pom.xml`. Standalone (no parent POM), Java 21 (`maven.compiler.release=21`), `com.heronarts:lx:1.2.1` as `provided`, resource filtering enabled for `src/main/resources/lx.package`, install profile that copies the built jar to `~/Chromatik/Packages/`. Update coordinates to `groupId=co.lxmcp`, `artifactId=lx-mcp`, `version=0.0.1-SNAPSHOT`.
- `package/src/main/resources/lx.package` — JSON descriptor with the same `name`/`mediaDir`/`author`/`url`/`build` shape as Apotheneum. `name: "LX-MCP"`.
- `package/src/main/java/lxmcp/LxMcpPlugin.java` — stub `implements LXPlugin` with `@LXPlugin.Name("LX-MCP")`. Empty `initialize(LX lx)`.
- `package/.gitignore` — Maven + IDE artifacts (`target/`, `*.class`, `.settings`, `.project`, `.classpath`, `.idea/`).

**Not in scope for PR-0**: no MCP SDK dependency yet (that lands as part of PR-1a once the version is pinned), no domain code, no tests beyond the build itself.

**Execution**: small enough to skip the 4-agent pipeline. One implementer pass + user review.

**Verification**:
- `cd package && mvn package` succeeds; produces `target/lx-mcp-0.0.1-SNAPSHOT.jar`.
- `mvn -Pinstall install` copies the jar to `~/Chromatik/Packages/`.
- Restart Chromatik; "LX-MCP" appears in the installed-packages list. Plugin contributes nothing yet — that's expected.

## Spike PRs

### PR-1a — Java MCP SDK feasibility (the go/no-go gate)

Smallest, most consequential. If this fails, the whole architecture pivot is wrong.

Open questions:
- Does the official Java MCP SDK (`modelcontextprotocol/java-sdk`) support streamable-HTTP transport at the maturity we need? Which version?
- Can the SDK be embedded inside a long-running JVM (LX) without taking over main?
- What's the embedding pattern — the rough shape of starting the MCP server from `LXPlugin.initialize(lx)`?
- Port discovery: confirm `~/.lx-mcp/status.json` with `{pid, port, projectPath, lxVersion}` is the right handshake.

Output: `docs/spike/sdk-feasibility.md` + a runnable embed test that accepts an MCP `initialize` request.

### PR-1b — LXCommand inventory + tool mapping

Read-only investigation of LX's command surface. Independent of PR-1a.

Open questions:
- Walk `heronarts/lx/command/LXCommand.java` (in the LX source repo) and enumerate every inner-class action (`LXCommand.<Category>.<Action>` + constructor signature).
- For each planned tool (`add_channel`, `set_parameter`, `add_modulator`, `wire_modulator`, `add_midi_mapping`, etc.), map to a concrete `LXCommand` action — or "needs direct in-memory edit, document undo skip."
- Tool granularity: do fine-grained primitives compose well for multi-agent fan-out, or do we also need a higher-level `compose_scene` tool?
- **Phase-2 capability skim** (light, not blocking): does LX expose pattern/effect source or class metadata so a future agent could reason about pattern algorithms? Just enumerate — the tool itself waits.

Output: `docs/spike/lxcommand-mapping.md` with the mapping table front and center.

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

Output: `docs/spike/qa-strategy.md` — concrete patterns + a verification template that PR-2 onwards fills in per-tool.

**Testability assumption + escalation rule**: the default expectation is that LX is testable from JUnit (it's a normal Java library); PR-1c's job is to confirm this and document the patterns. If PR-1c finds a real blocker — LX requires a display/GL context with no headless mode, the Java MCP SDK can't be tested in-process, etc. — that is an **architecture-level escalation**, not something the QA-strategy agent should quietly work around. The Writing Agent surfaces the blocker in `docs/spike/qa-strategy.md`'s TL;DR, and the Review Agent flags it as FAIL with the blocker description. We then re-plan before PR-2.

## Per-PR execution: 4-agent pipeline

Each spike PR runs the same pipeline. Sequential. Each agent writes one file under `docs/spike/<pr-id>/`; the next agent reads it. No other handoff state.

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

- PR-0's `mvn package` builds; LX-MCP appears in Chromatik's installed packages.
- All three spike deliverable files exist (`docs/spike/sdk-feasibility.md`, `lxcommand-mapping.md`, `qa-strategy.md`).
- All three Review agents returned PASS.
- The runnable embed test from PR-1a starts and accepts an MCP `initialize` request.

## Downstream PRs (high-level — to be planned after the spike phase)

For orientation only. Each will get its own focused plan once the spike findings constrain the specifics.

- **PR-2** — Embed HTTP MCP server in the plugin; write status file; `tools/list` works.
- **PR-3** — First read-only tool (`get_project_info`). Proves the tool-registration pattern end-to-end.
- **PR-4** — First mutation (`add_macro_knob`) via `LXCommand`. Live demo: knob appears in Chromatik, Cmd-Z undoes.
- **PR-5** — Fan-out: parallel sub-PRs for the rest of the tool surface (channels, patterns, modulators, modulation routing, MIDI, generic `set_parameter`).
- **PR-6** — Install docs for multiple agentic platforms + multi-agent usage examples + README rewrite.

**Phase 2 (post-MVP, not in the current PR list):**

- **Pattern/effect comprehension agent**: a runtime agent that reads a pattern or effect's source code to understand its algorithm, so an orchestrator can choose patterns by behavior. Requires an MCP tool that exposes pattern source / class metadata. PR-1b lightly skims LX's pattern-introspection surface so we know the tool is buildable; the tool itself waits for Phase 2.

**Future ideas (not yet planned):**

- **Visual-feedback agent (v2+)**: a runtime agent that grabs the current LX output and verifies the desired effect is actually happening. Requires a frame-grab tool. Captured as an idea — no PR allocation, no spike investigation.

```
PR-0 ──┬──> PR-1a ┐
       │   PR-1b ├──> PR-2 ──> PR-3 ──> PR-4 ──┬──> PR-5 (parallel)
       └──> PR-1c ┘                             └──> PR-6 (docs)
```
