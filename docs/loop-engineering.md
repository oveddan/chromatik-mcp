# Loop engineering for chromatik-mcp

How we use coding agents to build chromatik-mcp — the loop we actually run — plus the
self-improvement loops we've scoped but not yet built. This is the durable home for that
process; the throwaway plan files under `~/.claude/plans/` are not.

There are **two** loops in this project, and they should not be confused:

1. **The dev loop** — how *we* build chromatik-mcp (implement → review → merge). Documented and
   in use today. See below.
2. **The runtime loop** — how an *agent driving a finished chromatik-mcp* would self-correct a
   light show against a visual goal. Scoped and de-risked, not built. See
   [Future explorations](#future-explorations).

---

## The dev loop (in use)

One PR per iteration, run by `/loop` or by hand. The pipeline:

1. **Sync + claim.** `git checkout main && git pull`. Pick the next `[ ]` PR from
   `docs/build-plan.md` in order; mark it `[~]` with the branch name (the tracker edit
   travels in the PR, not on `main`).
2. **Branch.** Off `main`, or off the previous PR's branch when stacking. Each branch is
   exactly **one squashed commit** on its base, so rebases after an upstream merge are
   clean cherry-picks.
3. **Implement.** Per the layering in [`../CLAUDE.md`](../CLAUDE.md) (tool handler →
   domain primitive → `LXCommand`/engine) and the conventions in
   [`tool-conventions.md`](tool-conventions.md) and
   [`lx-coding-guidelines.md`](lx-coding-guidelines.md). Stay inside the PR's build-plan
   slice — anything bigger is too big (the scope guard in `CLAUDE.md`).
4. **Gate (objective).** `cd package && mvn package` — compiles and runs the full JUnit
   suite, including the headless harness and any do→undo→assert tests. Green is the bar.
5. **Catalog freshness.** Run the `lx-mcp-catalog` skill's incremental pass every
   iteration. It is keyed on source-content hashes, so an unchanged codebase no-ops in
   seconds; when a pattern/effect/modulator source did change, the regenerated entries
   ride in the same PR. Rationale: the semantic catalog
   ([`catalog-format.md`](catalog-format.md)) is a cache of source understanding — the
   loop is the cache-refresh trigger, so staleness is fixed at the PR that caused it
   rather than discovered later by a confused agent.
6. **Review (recommended).** Spawn a **fresh-context** review agent on the diff vs. the
   branch base, briefed with the PR's spec, the `CLAUDE.md` layering rules, and
   [`qa-strategy.md`](qa-strategy.md). Fix real findings; re-run the gate.
   This is deliberately **ad hoc** for now — judgment call per PR, not a mandated spec
   (formalizing it is a [future exploration](#future-explorations)).
7. **Open PR.** Squash to one commit, push, `gh pr create` with base = the stack parent.
   The body carries the gate result and the review summary. The user merges (no
   auto-merge).
8. **Maintain the stack.** After a squash-merge of a base PR, rebase the remaining
   single-commit branches onto the new base and retarget with `gh pr edit --base`.

### Why this passes the 4-condition test

A loop only earns its cost when all four hold; chromatik-mcp clears them:

- **Repeats** — the PR-5 fan-out (`set_parameter`, channels/patterns/effects,
  modulators/routing, MIDI) is the same shape of work, many times.
- **Verification is automated** — `mvn package` + the headless harness +
  do→undo→assert + `verify-load.sh` fail bad work without a human in the room.
- **Budget absorbs the waste** — loops re-read context and retry; that's accepted here.
- **Senior-engineer tools** — the agent compiles, runs the tests it writes, reads the
  failures, and (via the embedded server) calls its own tools against a live Chromatik.

### The objective gates

These are what make "done" a fact, not an opinion. Never weaken them to make a loop pass.

| Gate | Proves | Where |
| --- | --- | --- |
| `mvn package` | compiles + full JUnit suite green | `package/` |
| domain-primitive suite | LX runs headless; each mutation test round-trips its `LXCommand` through undo | `package/src/test/java/chromatikmcp/domain/` |
| do → undo → assert | the mutation used a *real* `LXCommand` (undo restores state) | per-tool tests, [`qa-strategy.md`](qa-strategy.md) |
| `EmbeddedMcpServerTest` / `ToolsIntegrationTest` | tools answer over in-process streamable-HTTP | `package/src/test/java/chromatikmcp/` |
| `verify-load.sh` | the shaded jar loads inside real LX from a deployment-faithful classpath | `package/scripts/` |

### Failure modes to avoid

Named so we recognize them in the act:

- **Ralph Wiggum loop** — the loop "completes" on a half-done job because nothing
  objective failed it. Mitigation: the gates above; never let a review agent's opinion
  substitute for `mvn package`.
- **Self-preferential bias** — the implementer grades its own work too kindly.
  Mitigation: the review agent runs in a *fresh context*, not the implementing session.
- **Goal drift** — long sessions lose earlier constraints. Mitigation: re-read
  `CLAUDE.md` + the conventions docs each iteration (the `/lx-mcp-loop` skill loads them).
- **Comprehension debt** — code ships faster than anyone reads it. Mitigation: the user
  reads every diff before merge; PRs stay small (the scope guard).

### State and memory

- **State** lives in the `docs/build-plan.md` tracker (what's merged / in-progress /
  next) — the agent forgets between sessions, the repo doesn't.
- **Lessons learned** get distilled into the conventions docs and into file-based memory
  (`~/.claude/projects/.../memory/`), not left in chat. The progression that's worth the
  effort: *fail → investigate → verify → distill → consult* — turn a bug into a checked
  rule, then read the rule next time instead of re-deriving it. The PR-4 finding that
  `lx.command.perform()` swallows failures (now in `tool-conventions.md`) is the model.

---

## Future explorations

Scoped, feasibility-checked, **not built**. Captured here so we don't re-derive the
findings when we pick them up.

### Runtime visual self-correction loop

The product's reason for being: let an agent composing a show *see* its output and
hillclimb to a visual goal — the "a well-designed rubric adds feedback to the
environment" pattern.

- **Keystone — a read-only `get_frame` / render-summary tool. Shipped in PR-8.**
  `lx.engine.copyFrameThreadSafe(LXEngine.Frame)` (`LXEngine.java:1346`) hands back a
  thread-safe copy of the rendered `int[] main`/`cue`/`aux` buffers, indexed by point;
  `LXPoint.xn/yn/zn` give normalized positions. `get_frame` returns a compact summary
  by default (non-black fraction, mean brightness, dominant colors, NxN mean-color
  grid); `include_image=true` opts into a PNG rendering of the point cloud too (MCP
  ImageContent — the model literally sees the frame), used sparingly since image
  content is token-expensive. `grid` / `width` further control token cost inside tight
  loops. All CPU-side and headless-testable, as predicted.
- **The loop.** Agent mutates (`set_parameter`, `add_pattern`, …) → reads the frame
  summary → a **verifier sub-agent grades it against a written visual rubric in its own
  context window** → self-correct until the rubric holds. The independent grader matters:
  models grade their own output too kindly.
- **Not on the surface.** No screenshot/recording API in LX; the frame buffer (or a
  custom `LXOutput` tap) is the path. Raw per-point colors are never returned — PR-8
  settled the reduction strategy as PNG + summary with client-tunable resolution.

### Formal verifier + objective `/goal` gate for the dev loop

Today the review agent is ad hoc and "done" is a human reading the PR. The hardening:
bind "done" to an objective `/goal` condition (`mvn package` + headless harness +
do→undo→assert all green) checked by an **independent verifier** separate from the
implementer — the maker/checker split, so the maker never grades its own homework. This
would let the dev loop run further unattended. Deferred by choice; revisit when the
fan-out volume makes hand-spawning a reviewer per PR the bottleneck.

### Pattern/effect comprehension agent

A runtime agent that understands what a pattern *does* so an orchestrator can pick by
behavior. **Constraint recorded:** LX exposes no source or bytecode surface — only
registry metadata, `@Description`/`@Tags` annotations, and the parameter tree (what knobs
exist, not what the algorithm does). True source-level comprehension needs filesystem
source-read or a bundled decompiler, outside LX's API. The metadata-level version is
buildable now; the algorithm-level version is the open problem.
