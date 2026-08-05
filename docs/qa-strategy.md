# Automated QA strategy (PR-1c)

## TL;DR — **no blocker, no escalation**

LX is a normal Java library and **is testable headless from JUnit** — confirmed, not assumed. The build plan's escalation rule (a missing headless mode would force a re-plan before PR-2) does **not** trigger.

Every tool gets a two-layer test, and every v1 mutation is checked for free by a **do → undo → assert state restored** round-trip — because [the LXCommand mapping](lxcommand-mapping.md) showed every v1 tool routes through a real `LXCommand`. The one open risk — concurrent mutations on the single LX engine thread — gets its own thread-affinity test shape.

This is backed by executable proof, mirroring how PR-1a left `EmbeddedMcpServerTest`:

| Gate | Proves | Where |
| --- | --- | --- |
| domain-primitive suite | LX constructs + ticks with no GUI/GL context and no engine thread; each mutation test round-trips its `LXCommand` through undo (originally proved by a dedicated `HeadlessLxHarnessTest`, since retired) | `package/src/test/java/chromatikmcp/domain/` |
| `EmbeddedMcpServerTest` (PR-1a) | the embedded HTTP MCP server is in-process testable | `package/src/test/java/chromatikmcp/mcp/EmbeddedMcpServerTest.java` |
| `verify-load.sh` (PR-0) | the shaded jar loads inside real LX and `initialize()` runs | `package/scripts/verify-load.sh` |

All three run on a clean runner via `.github/workflows/build.yml` (`mvn package` + the load gate).

## What was decided

| Question | Answer |
| --- | --- |
| Can `LX` run headless in tests? | **Yes.** `new LX(model)` needs no GUI/GL context. Never calling `lx.engine.start()` keeps everything on the test thread |
| Advance engine state in a test? | `lx.engine.run()` — one synchronous frame, no thread (`LXEngine.java:981`) |
| Default per-tool test shape | (1) domain-primitive unit test, (2) MCP-handler integration test |
| Built-in correctness check for v1 mutations | **do → undo → assert restored** — every v1 tool is `LXCommand`-backed |
| Direct-edit fallback | defined (snapshot + teardown rollback) but **unused in v1** — zero direct-edit primitives |
| Embedded MCP server in-process testable? | **Yes** — confirmed by PR-1a's `EmbeddedMcpServerTest`, reused as the integration harness |
| Engine-thread concurrency | marshal mutations via `lx.engine.addTask(...)`; dedicated thread-affinity test |
| CI vs local-only | CI = full JUnit suite + load gate, headless. Local-only = live-Chromatik visual demos |

## Headless LX harness

The canonical setup, taken from LX's own `heronarts.lx.headless.LXHeadless` and `benchmarks.BlendingHarness`:

```java
LXModel model = new GridModel(8, 8);   // any LXModel; small grid is enough
LX lx = new LX(model);                 // no GUI, no GL, no preferences load
// Do NOT call lx.engine.start().
lx.engine.run();                       // advance exactly one frame, on this thread
```

`new LX(model)` performs no graphics initialization (`LX.java:435-521`). Because the engine thread is never started, the test owns timing: call `run()` once per frame you need. This is the seed every tool test builds on (shared as the `chromatikmcp.HeadlessLxTest` fixture).

**Deferred structure regeneration (LX 1.2.2) never happens on its own here.** Fixture
adds/removals and metrics/tag/output writes set regeneration flags serviced by
`LXStructure.beforeEngineRun()`. A headless test that reads the derived model after such a
mutation must call `Fixtures.flushStructure(lx)` (or run the whole engine frame), or assert
only synchronous parameter/component state.

> If a future change makes LX require a display/GL context, every domain test fails at construction. That is an **architecture-level escalation** (re-plan before continuing), not something a tool test should work around.

## Per-tool test shape (the template PR-2+ fills in)

Each tool ships two tests. The split mirrors the [layering rule in `CLAUDE.md`](../CLAUDE.md) — primitives are tested directly, handlers are tested through the MCP seam.

**1. Domain-primitive unit test** — the mutation logic, no MCP.

```java
LX lx = newHeadlessLx();
LXModulator m = Modulators.addModulator(lx, lx.engine.modulation, MacroKnobs.class);   // the primitive
assertEquals(1, lx.engine.modulation.modulators.size());
assertSame(m, lx.engine.modulation.modulators.get(0));
```

**2. MCP-handler integration test** — the tool's schema + handler over the wire, reusing PR-1a's in-process server pattern (`EmbeddedMcpServerTest`): start `EmbeddedMcpServer` on an ephemeral port, connect a real `McpSyncClient`, `callTool(...)`, assert the Result-shaped payload. This confirms the JSON schema, argument parsing, and `Result.error` mapping — the parts the unit test skips.

## do → undo → assert (the built-in correctness check)

The default for **every v1 mutation**. It doubles as a "did we actually use a real `LXCommand`?" check — a primitive that quietly does a direct edit instead of `lx.command.perform(...)` fails the undo assertion.

```java
double before = lx.engine.speed.getValue();
lx.command.perform(new LXCommand.Parameter.SetValue(lx.engine.speed, before / 2));
assertEquals(before / 2, lx.engine.speed.getValue(), 1e-9);   // do
lx.command.undo();
assertEquals(before, lx.engine.speed.getValue(), 1e-9);       // undo restores
```

Pattern per tool: snapshot the relevant state → call the primitive (which performs the command) → assert mutated → `lx.command.undo()` → assert restored. The exact "relevant state" per tool is the **Undo assertion** column of the table in [`lxcommand-mapping.md`](lxcommand-mapping.md) (e.g. `add_channel` → channel count + identity; `add_modulator` → modulator list + autostart side effect). That table is the source of truth; this doc does not duplicate it.

API: `lx.command` is the `LXCommandEngine` (`LX.java:420`); `perform` / `undo` / `redo` at `LXCommandEngine.java:58/135/162`.

### Direct-edit fallback (defined, unused in v1)

For a future tool with no backing `LXCommand` (e.g. toggling a transient runtime flag), there is no undo step to assert against. The fallback is a **state-snapshot diff**: capture the touched fields before, assert the intended change after, and roll back manually in teardown. **v1 has zero direct-edit primitives** ([`lxcommand-mapping.md`](lxcommand-mapping.md)), so this branch is documented for completeness — reviewers should not expect a direct-edit test in v1.

## Engine-thread concurrency test (the top risk)

[`lxcommand-mapping.md`](lxcommand-mapping.md) flags this as the top implementation risk: under multi-agent fan-out, parallel MCP requests hit one `LX` instance, and uncoordinated mutations on the engine thread can interleave into corrupt state. The fix is a **server-side serialization queue** that marshals all mutations onto the engine thread before returning the tool result. LX already provides the dispatch primitive:

- `lx.engine.addTask(Runnable)` (`LXEngine.java:846`) — thread-safe enqueue.
- The queue is drained at the top of each engine cycle (`LXEngine.java:1090-1092`), so a task runs on the engine thread on the next `run()`.

**Test shape** (lands with the serialization mechanism in PR-2, seeded here):

```java
LX lx = newHeadlessLx();
int n = 50;
for (int i = 0; i < n; i++) {
  final int v = i;
  lx.engine.addTask(() -> someMutation(lx, v));   // enqueue from many callers
}
for (int f = 0; f < n; f++) lx.engine.run();       // drain on the engine thread
assertConsistentFinalState(lx);                    // deterministic, no interleave
```

The assertion is "all N mutations applied, in a consistent final state" — not a specific ordering. This is the regression guard for the serialization queue.

## What runs where

**CI (headless, every push/PR — `.github/workflows/build.yml`):**

- `mvn package` → the full JUnit suite: domain-primitive unit tests, MCP-handler integration tests, the headless harness gate, the embed test, and the concurrency test. No display, no live Chromatik.
- `scripts/verify-load.sh` → the live load gate: boots LX headless, confirms the shaded jar is discovered and `initialize()` runs.

**Local-only (live LX, manual):**

- Visual / UI-coupled demos that need the Chromatik GUI — e.g. "add a macro knob, see it appear, Cmd-Z visibly removes it." These confirm the human-facing payoff of undo but can't run on a headless runner. Recorded/flagged in PR-6.
- **Multi-agent workflow tests** — out of scope for v1; manual/recorded verification, flagged for PR-7.

## Verification template (copy per tool)

```java
class AddMacroKnobTest {
  private static LX newHeadlessLx() { return new LX(new GridModel(8, 8)); }

  @Test void primitive_mutatesEngineState() {
    LX lx = newHeadlessLx();
    LXModulator m = Modulators.addModulator(lx, lx.engine.modulation, MacroKnobs.class);
    assertEquals(1, lx.engine.modulation.modulators.size());
    // ... assert identity / side effects per the lxcommand-mapping table
  }

  @Test void primitive_undoRestoresState() {
    LX lx = newHeadlessLx();
    int before = lx.engine.modulation.modulators.size();
    Modulators.addModulator(lx, lx.engine.modulation, MacroKnobs.class);
    lx.command.undo();
    assertEquals(before, lx.engine.modulation.modulators.size());
  }

  @Test void handler_overMcp_returnsResult() {
    // start EmbeddedMcpServer (PR-1a pattern), connect McpSyncClient,
    // callTool("add_macro_knob", args), assert the Result-shaped payload.
  }
}
```

PR-2 onward instantiates this skeleton per tool, drawing each undo assertion from the table in [`lxcommand-mapping.md`](lxcommand-mapping.md).

## Composition/clip test recipes (foundation for the timeline tool families)

Extend `chromatikmcp.CompositionTestSupport` — a bare headless LX already carries a fully
initialized timeline (composition + master `BusClipLane` + `GlobalModulationClipLane` +
`ColorPaletteClipLane`; `addChannelWithPattern` adds bus/MIDI/pattern lanes). Four
recipes that otherwise cost a day each:

- **`playFrom` silently no-ops unless the clip `hasTimeline`**, and a fresh composition
  has none. The only public way to flip it without recording is
  `setMarker(PLAY_END, …)` / `setPlayEnd` past the current length, which grows the clip
  and sets the flag — that's `CompositionTestSupport.enableTimeline(clip, millis)`.
- **Snapshot cursors with `.immutable()` (or `.clone()`) before the "do"** —
  `clip.loopStart.cursor` and friends are fields LX rewrites in place, so capturing a
  live alias makes an undo assertion pass vacuously.
- **Never `assertEquals` on millis** — under TEMPO timeBase (the default for new clips),
  `Cursor.Operator.isEqual` ignores millis entirely. Compare through the clip's own
  `CursorOp()` — that's `CompositionTestSupport.assertCursorEqual(clip, expected, actual)`.
- **Every marker setter silently clamps** (`setLoopStart`/`setLoopEnd`/`setPlayStart`/
  `setInsertMarker`; `setPlayEnd` also *grows* length). Mutation payloads echo the cursor
  read back from the marker, never the requested one — and each family pins that with a
  clamping test (see `CompositionParametersTest.markerSettersClampAndTheMarkerIsTheEchoSource`).
