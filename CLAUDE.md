# CLAUDE.md

Project context for AI assistants working in this repo. See [README.md](README.md) for the architecture and [docs/build-plan.md](docs/build-plan.md) for the PR breakdown.

## Shape of the project

A single Java package (`package/`) — drop-in LX jar (Maven). The jar embeds an HTTP MCP server inside the LX runtime, so AI clients (any MCP-speaking agentic platform — Claude Code, Claude Desktop, Cursor, Codex, custom orchestrators) connect to it directly and call tools that mutate LX state in-process. No separate Node server, no `.lxp` file editing, no file watcher.

The only filesystem touchpoint is `~/.lx-mcp/status.json`, which the plugin writes on startup so clients can discover the HTTP port.

Reference LX source at `/Users/danoved/Source/LX/`. The scaffolding convention mirrors `/Users/danoved/Source/Apotheneum/` (Java 21, `com.heronarts:lx:1.2.1` as `provided`, `lx.package` JSON descriptor in `src/main/resources/` with Maven token filtering, install profile copies the jar to `~/Chromatik/Packages/`).

## Composability is the prime directive

Every mutation operation lives in its own small, focused Java function with a narrow signature. Tool handlers compose these primitives; they do not inline `LXCommand` construction or model edits.

**Rule of thumb**: if you are about to write a tool handler that calls `lx.command.perform(...)` directly or reaches into `lx.engine.*` to mutate it, stop. Extract a function with a name that describes the intent (`addGlobalModulator`, `setParameterValue`, `addMidiMapping`, …), put it in a `domain/` module that the tool handler imports, and call it from the handler.

Why this matters here specifically: some mutations will route through `LXCommand` (gets undo support); others will edit the in-memory model directly (where no command exists, or undo isn't worth wrapping). That choice should live inside one composable primitive per intent, not be smeared across tool handlers. If the implementation strategy for an operation changes later (e.g., a new `LXCommand` lands upstream), exactly one function needs to change.

### Layering

```
tool handler  ──> domain primitive  ──> LXCommand.perform(...)   (mutation with undo)
(MCP-shaped)     (intent, narrow)   ──> direct lx.engine.* edit  (mutation without undo)
                                    ──> read lx.engine.*         (read-only)
```

- **Tool handlers** (`package/src/main/java/lxmcp/tools/*.java`): parse args, call a domain primitive, format the result. No `LXCommand` construction. No direct engine mutation.
- **Domain primitives** (`package/src/main/java/lxmcp/domain/*.java`): the only place that knows how the mutation is actually applied. Each is one focused function.
- **MCP plumbing** (`package/src/main/java/lxmcp/mcp/*.java`): server lifecycle, HTTP transport, status-file writing. Tool handlers and domain primitives never reach into MCP plumbing.

### Concrete example — "add a global modulator"

Bad (inline, not swappable):
```java
// tool handler
public Result<ModulatorInfo> handle(AddMacroKnobArgs args) {
  lx.command.perform(new LXCommand.Modulation.AddModulator(lx.engine.modulation, MacroKnobs.class));
  var mods = lx.engine.modulation.modulators;
  return Result.ok(ModulatorInfo.from(mods.get(mods.size() - 1)));
}
```

Good (composed primitive, single point of swap):
```java
// domain/Modulators.java
public static LXModulator addGlobalModulator(LX lx, Class<? extends LXModulator> kind) {
  lx.command.perform(new LXCommand.Modulation.AddModulator(lx.engine.modulation, kind));
  var mods = lx.engine.modulation.modulators;
  return mods.get(mods.size() - 1);
}

// tools/AddMacroKnob.java
protected Result<ModulatorInfo> handle(AddMacroKnobArgs args) {
  LXModulator m = Modulators.addGlobalModulator(lx, MacroKnobs.class);
  return Result.ok(ModulatorInfo.from(m));
}
```

`addGlobalModulator` is reused by every tool that adds a global modulator (MacroKnobs, MacroSwitches, MacroTriggers, LFOs, envelopes). The handler is a one-liner with no `LXCommand` knowledge. If we ever need to swap the implementation (e.g., add validation, switch from `LXCommand` to direct edit, fan out a notification), only the primitive changes.

### When primitives multiply

If three tools each need to "find the channel by id, then walk to a parameter, then set it," extract a `setParameterByPath(lx, oscPath, value)` primitive. Don't duplicate. But: only extract when the third caller appears — two callers is coincidence, three is a pattern.

### What this does **not** mean

- Don't pre-build abstraction layers that aren't used. No factories, registries, or strategy interfaces until two real implementations exist.
- Don't wrap every two-line operation in a function. Composability is about *mutation primitives* — not formatting helpers or one-shot string assembly.
- Don't introduce dependency injection containers. Plain static methods plus the `LX` reference passed at server-start time are enough.

## Code style

- Java: standard Maven layout, target the LX version pinned in `lx.package`. Keep modulator/plugin lifecycle clean — register/unregister listeners symmetrically.
- Result-shaped errors at tool boundaries — return a tagged `Result<T>` (or equivalent sealed type) rather than throwing across the MCP handler boundary. Map exceptions to `Result.error(...)` at the seam.
- Comments: only when the *why* is non-obvious. Don't narrate the *what*.
- Tests: every domain primitive gets a JUnit test against a constructed `LX` instance or a fixture. Tool handlers get an integration test that exercises the MCP schema + the primitive. The detailed QA strategy lives in `docs/spike/qa-strategy.md` (produced by PR-1c).
- LX idioms: follow [docs/lx-coding-guidelines.md](docs/lx-coding-guidelines.md) — model variants with `enum`s (not maps/magic constants), share an `interface` across implementations, use framework helpers (`setColors`, `EnumParameter` labels) instead of reinventing them, don't allocate in render loops, and keep diffs minimal. Distilled from upstream review feedback so we don't relearn it per PR.
- Tool surface: follow [docs/tool-conventions.md](docs/tool-conventions.md) — naming, canonical-path addressing, `Result` wire shape, engine-thread rule. Decided once in PR-3; don't re-decide per tool.

## References

- LX source: `/Users/danoved/Source/LX/` (LXCommand categories, LXPlugin interface, modulator base classes, project serialization, OSC engine).
- Apotheneum (reference plugin layout): `/Users/danoved/Source/Apotheneum/` (pom.xml, lx.package descriptor, install profile).
- Sample project for fixtures: `/Users/danoved/Source/Apotheneum/target/classes/projects/Apotheneum-Test.lxp`.
- Java MCP SDK: `modelcontextprotocol/java-sdk` (HTTP transport, tool registration).

## Scope guard

Any PR larger than the slices in [docs/build-plan.md](docs/build-plan.md) is too big. The whole point of the small-PR plan is that each PR is independently demoable — if yours isn't, split it.
