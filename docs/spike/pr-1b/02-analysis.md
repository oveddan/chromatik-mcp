# PR-1b Analysis: Decisions

Input: `01-research-notes.md`. Source spot-checks against `LXCommand.java` cited inline.
Decisions are structured, not prose. Each carries the research citation through.

---

## Q1 — The mapping (tool → LXCommand → domain primitive)

### Decision table

| Tool | LXCommand action | Domain primitive signature | Undo? | Notes |
|---|---|---|---|---|
| `add_channel` | `Mixer.AddChannel(Class<? extends LXPattern>)` or `AddChannel()` | `LXChannel addChannel(LX lx, Class<? extends LXPattern> initialPattern)` | yes | Primitive defaults `initialPattern` to `null` → empty-channel ctor `AddChannel()`. Tool supplies only optional pattern class. Side effect: sets focus+selection (LXCommand.java:1754-1819). Return value: read back `lx.engine.mixer.channels.get(size-1)`. |
| `remove_channel` | `Mixer.RemoveChannel(LXAbstractChannel)` | `void removeChannel(LX lx, LXAbstractChannel channel)` | yes | Tool resolves channel by id/index → `LXAbstractChannel` before calling. Recursive group child removal + modulation/MIDI/snapshot cleanup are internal (LXCommand.java:1890-1950). Batch variant `RemoveSelectedChannels` is out of scope for v1 (no selection concept over MCP). |
| `set_parameter` | `Parameter.SetValue` / `SetNormalized` / `SetString` / `SetColor` (polymorphic) | `void setParameter(LX lx, LXParameter p, double value)` + overloads `(…, String)`, `(…, boolean)` | yes | **One primitive, dispatched on runtime parameter type** — this is the single swap point. Dispatch: `StringParameter`→`SetString`; `ColorParameter`→`SetColor`; `BooleanParameter`→`SetNormalized(bool)`; `DiscreteParameter`→`SetValue(int)`; else `LXParameter`→`SetValue(double)`. Tool resolves the parameter by OSC/canonical path first (see path-resolution risk below). Cite: LXCommand.java:432-836. |
| `add_modulator` | `Modulation.AddModulator(LXModulationEngine, Class<? extends LXModulator>)` | `LXModulator addGlobalModulator(LX lx, Class<? extends LXModulator> kind)` | yes | **Ctor takes the modulation *engine*, not `LX`.** Primitive defaults engine to `lx.engine.modulation` (global). A second overload `addModulator(LXModulationEngine engine, Class<…> kind)` covers per-device engines when that tool appears (rule-of-three: don't add until a 2nd caller exists). Command auto-labels + `autostart()`s the instance and reads back via `ComponentReference` (LXCommand.java:2128-2188, confirmed). Return: `engine.getModulators().get(size-1)`. Throws `LX.InstantiationException` → wrapped `InvalidCommandException`; map to `Result.error`. |
| `wire_modulator` | **Split:** `Modulation.AddModulation(engine, LXNormalizedParameter source, LXCompoundModulation.Target target)` for continuous; `Modulation.AddTrigger(engine, BooleanParameter source, BooleanParameter target)` for boolean triggers | `LXCompoundModulation wireModulation(LX lx, LXNormalizedParameter src, LXCompoundModulation.Target dst)` and `LXTriggerModulation wireTrigger(LX lx, BooleanParameter src, BooleanParameter dst)` | yes | Source for `AddModulation` may be a parameter **or** a component implementing `LXNormalizedParameter` — the command's internal `ModulationSourceReference` handles both (LXCommand.java:2264-2305, confirmed). Both ctors throw `LXParameterModulation.ModulationException` → `InvalidCommandException` on invalid wiring (e.g. cyclic, type mismatch). Map at the seam to `Result.error`. **Two primitives, not one** — the source/target types are genuinely different (normalized vs boolean); a single primitive would need an unsafe cast. Engine defaults to `lx.engine.modulation`. |
| `add_midi_mapping` | `Midi.AddMapping(LXShortMessage message, LXNormalizedParameter parameter)` | `LXMidiMapping addMidiMapping(LX lx, LXShortMessage message, LXNormalizedParameter parameter)` | yes | **Wrinkle:** ctor wants a constructed `LXShortMessage`, not raw bytes. The tool layer must synthesize the message (note/CC, channel, number) from MCP args before calling the primitive — OR the primitive grows a convenience overload `addMidiMapping(LX, MidiMappingSpec)` that builds the `LXShortMessage`. Recommend the convenience overload so tool args stay declarative. Delegates to `LXMidiMapping.create(lx, message, parameter)` (LXCommand.java:4911-4937, confirmed). |
| `add_pattern` | `Channel.AddPattern(LXPatternEngine, Class<? extends LXPattern>)` | `LXPattern addPattern(LX lx, LXPatternEngine engine, Class<? extends LXPattern> kind)` | yes | Tool resolves target channel → its `LXPatternEngine` (`channel.getPatternEngine()` / equivalent). Optional `int index` overload deferred until needed. Instantiates via `lx.instantiatePattern` internally; can throw instantiation error → `InvalidCommandException` (LXCommand.java:868-944). |
| `add_effect` | `Channel.AddEffect(LXComponent parent, Class<? extends LXEffect>)` | `LXEffect addEffect(LX lx, LXComponent container, Class<? extends LXEffect> kind)` | yes | `container` must implement `LXEffect.Container` (channel, pattern, …); command runs `validateEffectContainer()`. Tool resolves container by path; primitive passes through (LXCommand.java:1358-1403). |
| `remove_effect` (adjacent) | `Channel.RemoveEffect(LXComponent container, LXEffect effect)` | `void removeEffect(LX lx, LXComponent container, LXEffect effect)` | yes | Cascades to modulations/MIDI/snapshots/clip lanes. Respects `effect.locked` (cannot remove locked). Surface `locked`→`Result.error` cleanly (LXCommand.java:1405-1451). |
| `remove_pattern` (adjacent) | `Channel.RemovePattern(LXPatternEngine, LXPattern)` | `void removePattern(LX lx, LXPatternEngine engine, LXPattern pattern)` | yes | Symmetric to `add_pattern`; full cleanup internal (LXCommand.java:946-1012). |
| `remove_modulation` (adjacent) | `Modulation.RemoveModulation` / `RemoveTrigger` | `void removeModulation(LX lx, LXCompoundModulation m)` / `void removeTrigger(LX lx, LXTriggerModulation t)` | yes | Split mirrors `wire_modulator` (LXCommand.java:2341-2425, 2464-2517). |

### Constructor args: tool-supplied vs primitive-defaulted
- **Tool supplies (always):** target component/parameter (resolved from a path/id arg) + the new value or class.
- **Primitive defaults:** engine references (`lx.engine.modulation`, the channel's pattern engine), `index` (append at end), `modulationColor` (-1 = auto), and all `JsonObject` restore args (null = fresh instantiation, only used on undo/redo internally).
- **Never exposed to tools:** `JsonObject` ctor variants, `ComponentReference` / `ParameterReference` — those are LXCommand-internal undo machinery.

---

## Q2 — Undo coverage

**Decision: every v1 mutation primitive is LXCommand-backed → all get `do→undo→assert` correctness verification for free.** No direct-edit fallback is required for the planned v1 tool set.

| Primitive | Backing | Verification in PR-1c |
|---|---|---|
| addChannel / removeChannel | `Mixer.*` | do→undo→assert channel count + identity |
| setParameter (all 5 dispatch arms) | `Parameter.*` | do→undo→assert prior value restored, per type |
| addModulator | `Modulation.AddModulator` | do→undo→assert modulator list + autostart side effect reverted |
| wireModulation / wireTrigger | `Modulation.AddModulation` / `AddTrigger` | do→undo→assert modulation removed; plus a negative test: invalid wiring → `ModulationException` → `Result.error` (no command committed) |
| addMidiMapping | `Midi.AddMapping` | do→undo→assert mapping list |
| addPattern / removePattern | `Channel.AddPattern` / `RemovePattern` | do→undo→assert pattern list + active pattern |
| addEffect / removeEffect | `Channel.AddEffect` / `RemoveEffect` | do→undo→assert effect list; plus negative test: locked effect → `Result.error` |

**Why no fallback needed now:** the research surface shows a 1:1 LXCommand for every planned intent. The composability rule's "direct in-memory edit, document the undo skip" branch is real but **unexercised by v1** — it only appears when a future tool has no command (e.g. toggling a transient runtime flag). Flag for the Writing Agent: state explicitly that v1 has zero direct-edit primitives, so the "document the undo skip" convention is defined but not yet used.

**Risk:** `ModulationException` and `InstantiationException` are thrown from `perform()`. `lx.command.perform()` swallows/wraps these as `InvalidCommandException`. The primitive must catch at the seam and return `Result.error` rather than let it cross the MCP boundary. This is the one place where "command-backed" does not mean "cannot fail."

---

## Q3 — Tool granularity (fine-grained vs `compose_scene`)

**Decision: ship fine-grained primitives only for v1. Do NOT build a higher-level `compose_scene` tool.**

### Reasoning
1. **Fan-out composes at the agent layer, not the tool layer.** Several agents each building part of a scene in parallel is exactly the case fine-grained tools serve best: each agent issues `add_channel` / `add_pattern` / `wire_modulator` calls against shared in-process LX state. A coarse `compose_scene(spec)` tool would force agents to serialize their intent into one monolithic spec and hand it to a single executor — re-introducing the coordination bottleneck that multi-agent fan-out exists to avoid.
2. **LX state is the shared substrate.** Because mutations run in-process against one `LX` instance, parallel agents already see each other's results by reading engine state. They don't need a composition tool to merge work; they need atomic, well-scoped mutations. The primitives provide exactly that.
3. **Undo granularity matches.** Each LXCommand is one undo step. Fine-grained tools give the human one undo per agent action — legible. A `compose_scene` that fires N commands either pollutes the undo stack with N opaque steps or needs a batch-command wrapper LX does not provide for arbitrary mixes (the existing batch commands — `RemoveSelectedChannels`, `MultiSetValue` — are type-specific, not general).
4. **Composability prime directive.** A `compose_scene` tool would inevitably orchestrate primitives and start encoding scene-assembly policy in the tool layer — drift from "tool handler = parse, call one primitive, format."

### What would change the answer
- **Concurrency hazards.** If parallel agents racing on the LX engine thread produce corrupt/interleaved state (LX mutations are expected on the engine thread), the fix is a **server-side serialization queue** in the MCP plumbing layer — not a `compose_scene` tool. Flag for PR-1c: add a concurrency/thread-affinity test (N parallel mutations → consistent final state). This is the highest-risk open item.
- **Transactional grouping.** If a real use case needs "all-or-nothing" multi-mutation (build N things, roll back all on any failure), revisit — but as a *batch/transaction* primitive (begin/commit/rollback around `lx.command`), not a semantic `compose_scene`.

---

## Q4 — Phase-2 introspection buildability

**Verdict: YES, the pattern/effect-comprehension tool is buildable for metadata + parameter-level comprehension on the found surface. NO for source-algorithm comprehension — that requires decompilation outside LX's surface.**

### Sufficient (buildable now)
- **Class discovery:** `LXRegistry` enumerates registered pattern/effect/modulator/fixture classes (LXRegistry.java:78-150+). `LXClassLoader` adds JAR-loaded extension classes with type/name/category/author/version (LXClassLoader.java:45-100+).
- **Class-level metadata, all RUNTIME-retained:** `@LXComponent.Name` / `.Description` / `.Author` / `.Tags` / `.Hidden` / `.PluginRequired` (LXComponent.java:80-128) and `@LXCategory` (CORE/FORM/COLOR/MIDI/STRIP/TEXTURE/TRIGGER/TEST/OTHER/AUDIO/MACRO/DMX). Readable by reflection without instantiation.
- **Parameter-level comprehension:** instantiate (or inspect a live instance) and walk the parameter tree — each `LXParameter` exposes `label`, `description`, `min`/`max`, value, normalized value. This is enough for "what knobs does this pattern have and what do they do."

### Missing / not on the surface
- **Algorithm/source comprehension.** Patterns/effects ship as `.class` bytecode; LX exposes no AST, no source, no structured algorithm description (research §5, confirmed by absence). A "what does this pattern *do* visually/algorithmically" tool would need a bundled decompiler (CFR/Procyon) or an out-of-band source index — explicitly outside LX's introspection API.
- **Instantiation cost for parameter walk.** Parameter metadata generally requires a constructed instance (annotations alone don't list parameters). Need to confirm whether a throwaway instantiation is safe/cheap for every registered class, or whether some require a model/GL context. **Unresolved — needs Writing Agent to flag as a phase-2 spike item:** "can we instantiate-and-introspect every registered pattern/effect cheaply, or only those already live in the project?"

### Recommendation for the buildability note
Phase-2 tool v1 should comprehend **metadata + parameters** (registry + annotations + parameter tree). Defer algorithm comprehension; if needed, treat decompilation as a separate, clearly-fenced subsystem, not part of the core introspection primitive.

---

## Carried-forward risks for the Writing Agent
1. **Parameter path resolution is undefined here.** `set_parameter`, `add_pattern`, `add_effect`, `wire_modulator` all assume a primitive that resolves an MCP-supplied path/id to a live `LXParameter`/`LXComponent`. Research gap #5 (canonical path syntax via `LXPath`) is unresolved. This resolver is itself a domain primitive (`resolveParameter(lx, path)` / `resolveComponent(lx, path)`) and is a prerequisite for half the tool set — call it out as its own slice.
2. **`LXShortMessage` synthesis** for `add_midi_mapping` — tool/primitive must build the MIDI message from declarative args.
3. **Exceptions cross `perform()`** (`ModulationException`, `InstantiationException`, locked-effect): primitives must map to `Result.error` at the seam; PR-1c needs negative tests.
4. **Engine-thread concurrency** under multi-agent fan-out is the top open risk (Q3) — needs a serialization-queue decision + test in PR-1c.
5. **v1 has no direct-edit primitives** — the "document undo skip" convention is defined but unused; say so explicitly.
