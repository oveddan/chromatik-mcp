# CLAUDE.md

Project context for AI assistants working in this repo. See [README.md](README.md) for the architecture and [docs/build-plan.md](docs/build-plan.md) for the PR breakdown.

## Always work in a worktree — the primary checkout is shared

Multiple agent sessions run against this repo concurrently. The primary checkout
(the repo root) is shared state: at any moment it may be on another session's
branch, carrying another session's uncommitted edits.

- **All work — code, docs, site, even a one-line fix — happens in a dedicated git
  worktree** under `.claude/worktrees/<branch-name>`, on its own branch. Never edit,
  commit, switch branches, stash, or clean in the primary checkout. Read-only
  operations (Read, grep, `git log`) are fine anywhere.
- **`git fetch origin main` immediately before creating the worktree**, and branch
  from `origin/main` — merges land via the GitHub API, so local `origin/main` is
  routinely stale, and branching from it silently bases your PR on pre-merge
  history (this has caused a near-revert of merged work).
- Never remove, reuse, or commit inside another session's worktree. Leave
  `.claude/worktrees/` entries alone unless the branch is yours.
- If you notice you've dirtied the primary checkout, move the change into a
  worktree branch and restore the checkout (`git checkout -- <file>`) — restore
  only *your* files; other sessions' modifications stay.

## Shape of the project

A single Java package (`package/`) — drop-in LX jar (Maven). The jar embeds an HTTP MCP server inside the LX runtime, so AI clients (any MCP-speaking agentic platform — Claude Code, Claude Desktop, Cursor, Codex, custom orchestrators) connect to it directly and call tools that mutate LX state in-process. No separate Node server, no `.lxp` file editing, no file watcher.

The filesystem touchpoints are `~/.chromatik-mcp/status.json`, which the plugin writes on startup so clients can discover the HTTP port, and the optional `~/.chromatik-mcp/config.json` (fixed port / bind host).

Reference LX source at `/Users/danoved/Source/LX/`; the scaffolding convention mirrors `/Users/danoved/Source/Apotheneum/` (see its `pom.xml` and `lx.package`).

## Composability is the prime directive

Every mutation operation lives in its own small, focused Java function with a narrow signature. Tool handlers compose these primitives; they do not inline `LXCommand` construction or model edits.

**Rule of thumb**: if you are about to write a tool handler that calls `lx.command.perform(...)` directly or reaches into `lx.engine.*` to mutate it, stop. Extract a function with a name that describes the intent (`addGlobalModulator`, `setParameterValue`, `addMidiMapping`, …), put it in a `domain/` module that the tool handler imports, and call it from the handler.

Why this matters here specifically: some mutations will route through `LXCommand` (gets undo support); others will edit the in-memory model directly (where no command exists, or undo isn't worth wrapping). That choice should live inside one composable primitive per intent, not be smeared across tool handlers. If the implementation strategy for an operation changes later (e.g., a new `LXCommand` lands upstream), exactly one function needs to change.

### Layering

```
tool handler  ──> domain primitive  ──> LXCommand.perform(...)   (mutation with undo)
(MCP-shaped)     (intent, narrow)   ──> direct lx.engine.* edit  (mutation without undo)
                                    ──> read lx.engine.*         (read-only)

LX objects ──> typed domain result ──> shared wire serializer ──> Map<String, Object>
```

- **Tool handlers** (`package/src/main/java/chromatikmcp/tools/*.java`): parse args, call a domain primitive, format the result. No `LXCommand` construction. No direct engine mutation.
- **Domain primitives** (`package/src/main/java/chromatikmcp/domain/*.java`): the only place that knows how the mutation is actually applied. Each is one focused function. Stable result shapes are typed records or domain objects, not raw wire maps.
- **Wire serializers**: one shared serializer owns the string keys and map construction for each stable MCP payload shape. Prefer a helper under `tools/` for a new top-level shape; a serializer may live on its typed record when the shape is nested or reused by several tools (for example `ParameterInfo.toMap()`). Sibling tools emitting the same shape use the same serializer.
- **MCP plumbing** (`package/src/main/java/chromatikmcp/mcp/*.java`): server lifecycle, HTTP transport, status-file writing. Tool handlers and domain primitives never reach into MCP plumbing.

Raw maps remain appropriate at the MCP boundary (arguments, JSON Schema, and final
`structuredContent`), inside an implementation, and for genuinely dynamic/open-ended
domain state. A new map-shaped domain result must document why a typed result is
unsuitable. See `docs/tool-conventions.md` for the current exceptions and
wire-compatibility rules.

### When primitives multiply

If three tools each need to "find the channel by id, then walk to a parameter, then set it," extract a `setParameterByPath(lx, oscPath, value)` primitive. Don't duplicate. But: only extract when the third caller appears — two callers is coincidence, three is a pattern.

### What this does **not** mean

Scoped to *mutation primitives*: no speculative abstraction layers until two real implementations exist. The typed-result rule does not require wrapping genuinely one-shot formatting or string assembly, or introducing a serialization framework. No DI container — plain static methods plus the `LX` reference passed at server-start time are enough.

## Code style

- Keep modulator/plugin lifecycle clean — register/unregister listeners symmetrically.
- Tool handlers return `Result<T>` and don't catch — domain primitives throw, and `Tools` maps exceptions to `Result.error(...)` at the seam.
- Comments: only when the *why* is non-obvious. Don't narrate the *what*.
- Tests: every domain primitive gets a unit test against a headless `LX`; every tool handler gets an integration test. Template and do→undo→assert pattern in [docs/qa-strategy.md](docs/qa-strategy.md).
- Build gate: run `package/scripts/build-gate.sh` instead of raw `mvn -f package/pom.xml package` — it keeps the full log on disk and prints a one-line pass/fail summary, so agents don't flood their context with surefire output.
- Conventions decided once, not re-decided per PR: [docs/tool-conventions.md](docs/tool-conventions.md) (tool surface, canonical-path addressing, `Result` wire shape, engine-thread rule), [docs/lx-coding-guidelines.md](docs/lx-coding-guidelines.md) (LX idioms, distilled from upstream review).

## Driving a live instance

Port comes from `~/.chromatik-mcp/status.json` — one of two by-design filesystem touchpoints (the other being the optional `~/.chromatik-mcp/config.json`).

- **Never answer live-state questions from cached responses.** Re-query; a saved response file is for parsing one large payload, not a source of truth minutes later.
- **Connection failure ⇒ Chromatik restarted**: re-read `status.json`, re-initialize the session, re-list before reusing any held canonical path (indices shift and project state resets — e.g. `output/enabled` comes back off).
- **Consult `get_component_doc` before reasoning about a pattern/effect's behavior** — entries exist for most stock LX components and cover the semantics that otherwise get guessed wrong.
- **Reading LX source to answer a live question is a server-gap signal.** An end consumer can't do it: work around it once, then queue the gap (payload, description, or catalog entry) in `docs/live-findings.md` rather than leaving the knowledge in the session.

## References

- LX source: `/Users/danoved/Source/LX/` (LXCommand categories, LXPlugin interface, modulator base classes, project serialization, OSC engine).
- Apotheneum (reference plugin layout): `/Users/danoved/Source/Apotheneum/` (pom.xml, lx.package descriptor, install profile).
- Sample project for fixtures: `/Users/danoved/Source/Apotheneum/target/classes/projects/Apotheneum-Test.lxp`.
- Java MCP SDK: `modelcontextprotocol/java-sdk` (HTTP transport, tool registration).

## Scope guard

Any PR larger than the slices in [docs/build-plan.md](docs/build-plan.md) is too big. The whole point of the small-PR plan is that each PR is independently demoable — if yours isn't, split it.
