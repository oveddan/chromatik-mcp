# LXCommand → MCP Tool Mapping (PR-1b)

Canonical deliverable. Maps every planned v1 MCP tool to a concrete `LXCommand` action and the domain primitive that wraps it. Citations are `LXCommand.java:<line>` against the LX source at `/Users/danoved/Source/LX/`.

Upstream artifacts: `docs/spike/pr-1b/01-research-notes.md` (enumeration + citations), `docs/spike/pr-1b/02-analysis.md` (decisions).

## TL;DR

- **Every planned v1 tool maps 1:1 to an existing `LXCommand`.** No direct-edit fallback is needed in v1 — the composability rule's "direct in-memory edit, document the undo skip" branch is defined but unexercised.
- **Fine-grained primitives only. No `compose_scene`.** Scene assembly composes at the *agent* layer against shared in-process LX state, not at the tool layer.
- **Two tools split into two primitives each:** `wire_modulator` → `wireModulation` (continuous) + `wireTrigger` (boolean); `remove_modulation` → `removeModulation` + `removeTrigger`. The source/target types are genuinely different and a single primitive would need an unsafe cast.
- **Engine-thread concurrency is the real risk.** LX mutations are expected on the engine thread; parallel agents hitting one `LX` instance need a server-side serialization queue (PR-1c / PR-2), not a coarser tool.
- **Exceptions cross `perform()`.** `ModulationException` and `InstantiationException` (and locked-effect) surface from `lx.command.perform()`; primitives must map them to `Result.error` at the seam.
- **Phase-2 introspection: metadata + parameters YES, source/algorithm NO.** Registry + RUNTIME annotations + parameter-tree walk are sufficient for comprehension of *what knobs exist*; *what the pattern does algorithmically* requires decompilation outside LX's surface.

## Mapping table

| MCP tool | `LXCommand.<Category>.<Action>` (cite) | Proposed domain primitive | Undo (do→undo→assert) | Notes / risks |
|---|---|---|---|---|
| `add_channel` | `Mixer.AddChannel(Class<? extends LXPattern>)` / `AddChannel()` (LXCommand.java:1754-1819) | `LXChannel addChannel(LX lx, Class<? extends LXPattern> initialPattern)` | yes — assert channel count + identity restored | `initialPattern` defaults to `null` → empty-channel ctor. Side effect: sets focus + selection. Read back `lx.engine.mixer.channels.get(size-1)`. |
| `remove_channel` | `Mixer.RemoveChannel(LXAbstractChannel)` (LXCommand.java:1890-1950) | `void removeChannel(LX lx, LXAbstractChannel channel)` | yes — assert channel restored | Tool resolves channel by id/index first. Recursive group-child + modulation/MIDI/snapshot cleanup is internal. Batch `RemoveSelectedChannels` out of v1 scope (no selection concept over MCP). |
| `set_parameter` | `Parameter.SetValue` / `SetNormalized` / `SetString` / `SetColor` (polymorphic) (LXCommand.java:432-836) | `void setParameter(LX lx, LXParameter p, double value)` + overloads `(…, String)`, `(…, boolean)` | yes — assert prior value restored, per type | **One primitive, dispatched on runtime parameter type** — single swap point. Dispatch: `StringParameter`→`SetString`; `ColorParameter`→`SetColor`; `BooleanParameter`→`SetNormalized(bool)`; `DiscreteParameter`→`SetValue(int)`; else `LXParameter`→`SetValue(double)`. Depends on parameter-path resolution (see prerequisites). |
| `add_modulator` | `Modulation.AddModulator(LXModulationEngine, Class<? extends LXModulator>)` (LXCommand.java:2128-2188) | `LXModulator addGlobalModulator(LX lx, Class<? extends LXModulator> kind)` | yes — assert modulator list + autostart side effect reverted | **Ctor takes the modulation *engine*, not `LX`.** Primitive defaults engine to `lx.engine.modulation`. Command auto-labels + `autostart()`s. Read back `engine.getModulators().get(size-1)`. Throws `LX.InstantiationException` → wrapped `InvalidCommandException` → map to `Result.error`. Per-device-engine overload deferred until a 2nd caller (rule-of-three). |
| `wire_modulator` (continuous) | `Modulation.AddModulation(engine, LXNormalizedParameter source, LXCompoundModulation.Target target)` (LXCommand.java:2264-2338) | `LXCompoundModulation wireModulation(LX lx, LXNormalizedParameter src, LXCompoundModulation.Target dst)` | yes — assert modulation removed; **negative test**: invalid wiring → `ModulationException` → `Result.error` (no command committed) | Source may be a parameter **or** a component implementing `LXNormalizedParameter` (internal `ModulationSourceReference` handles both). Engine defaults to `lx.engine.modulation`. |
| `wire_modulator` (trigger) | `Modulation.AddTrigger(engine, BooleanParameter source, BooleanParameter target)` (LXCommand.java:2427-2462) | `LXTriggerModulation wireTrigger(LX lx, BooleanParameter src, BooleanParameter dst)` | yes — same as above | **Split from `wireModulation`** — boolean source/target are a genuinely different type; one primitive would need an unsafe cast. Throws `ModulationException` → `Result.error`. |
| `add_midi_mapping` | `Midi.AddMapping(LXShortMessage message, LXNormalizedParameter parameter)` (LXCommand.java:4911-4937) | `LXMidiMapping addMidiMapping(LX lx, LXShortMessage message, LXNormalizedParameter parameter)` + convenience overload `addMidiMapping(LX, MidiMappingSpec)` | yes — assert mapping list | **Wrinkle:** ctor wants a constructed `LXShortMessage`, not raw bytes. Recommend the convenience overload that synthesizes the message (note/CC, channel, number) so tool args stay declarative. Delegates to `LXMidiMapping.create(lx, message, parameter)`. |
| `add_pattern` | `Channel.AddPattern(LXPatternEngine, Class<? extends LXPattern>)` (LXCommand.java:868-944) | `LXPattern addPattern(LX lx, LXPatternEngine engine, Class<? extends LXPattern> kind)` | yes — assert pattern list + active pattern | Tool resolves target channel → its `LXPatternEngine`. Instantiates via `lx.instantiatePattern` internally; can throw instantiation error → `InvalidCommandException` → `Result.error`. Optional `int index` overload deferred. |
| `add_effect` | `Channel.AddEffect(LXComponent parent, Class<? extends LXEffect>)` (LXCommand.java:1358-1403) | `LXEffect addEffect(LX lx, LXComponent container, Class<? extends LXEffect> kind)` | yes — assert effect list | `container` must implement `LXEffect.Container` (channel, pattern, …); command runs `validateEffectContainer()`. Tool resolves container by path; primitive passes through. |
| `remove_effect` (adjacent) | `Channel.RemoveEffect(LXComponent container, LXEffect effect)` (LXCommand.java:1405-1451) | `void removeEffect(LX lx, LXComponent container, LXEffect effect)` | yes — assert effect list; **negative test**: locked effect → `Result.error` | Cascades to modulations/MIDI/snapshots/clip lanes. Respects `effect.locked` (cannot remove locked) — surface cleanly. |
| `remove_pattern` (adjacent) | `Channel.RemovePattern(LXPatternEngine, LXPattern)` (LXCommand.java:946-1012) | `void removePattern(LX lx, LXPatternEngine engine, LXPattern pattern)` | yes — assert pattern list | Symmetric to `add_pattern`; full cleanup internal. |
| `remove_modulation` (adjacent, continuous) | `Modulation.RemoveModulation` (LXCommand.java:2341-2395) | `void removeModulation(LX lx, LXCompoundModulation m)` | yes | Split mirrors `wire_modulator`. |
| `remove_modulation` (adjacent, trigger) | `Modulation.RemoveTrigger` (LXCommand.java:2464-2517) | `void removeTrigger(LX lx, LXTriggerModulation t)` | yes | Split mirrors `wire_modulator`. |

### Constructor args: tool-supplied vs primitive-defaulted

- **Tool supplies (always):** the target component/parameter (resolved from a path/id arg) + the new value or class.
- **Primitive defaults:** engine references (`lx.engine.modulation`, the channel's pattern engine), `index` (append at end), `modulationColor` (`-1` = auto), and all `JsonObject` restore args (`null` = fresh instantiation; only used internally on undo/redo).
- **Never exposed to tools:** `JsonObject` ctor variants, `ComponentReference` / `ParameterReference` — these are LXCommand-internal undo machinery.

## Prerequisite primitives

These are not tools themselves but are required by the table above. Build them as domain primitives.

- **Parameter-path resolution — `resolveParameter(lx, path)` / `resolveComponent(lx, path)`.** Required by `set_parameter`, `add_pattern`, `add_effect`, `wire_modulator` (half the v1 tool set). Resolves an MCP-supplied canonical/OSC path or id to a live `LXParameter` / `LXComponent`. **Unresolved prerequisite:** the canonical path-string syntax (e.g. `channel/3/pattern/2/brightness`) is used throughout LX via `LXPath` (imported at LXCommand.java:34) but was **not formally enumerated** by the research (research gap #5). This resolver is its own slice and a hard dependency — call it out before the dependent tools land.
- **`LXShortMessage` synthesis for MIDI.** `add_midi_mapping`'s LXCommand ctor wants a constructed `LXShortMessage`, not raw bytes. The primitive (or a convenience overload) builds the message from declarative args (note/CC, channel, number).
- **Exception → `Result.error` mapping.** `lx.command.perform()` lets `LXParameterModulation.ModulationException` and `LX.InstantiationException` cross as / wrapped in `InvalidCommandException`, and `RemoveEffect` refuses locked effects. Each affected primitive catches at the seam and returns `Result.error` rather than throwing across the MCP boundary. This is the one place where "command-backed" does not mean "cannot fail."

## Tool granularity

**Decision: ship fine-grained primitives only for v1. Do NOT build a higher-level `compose_scene` tool.**

Defense against the multi-agent fan-out use case (the strongest argument *for* a coarse tool):

1. **Fan-out composes at the agent layer, not the tool layer.** Several agents each building part of a scene in parallel is exactly what fine-grained tools serve best: each issues `add_channel` / `add_pattern` / `wire_modulator` calls against shared in-process LX state. A coarse `compose_scene(spec)` would force agents to serialize intent into one monolithic spec handed to a single executor — re-introducing the coordination bottleneck fan-out exists to avoid.
2. **LX state is the shared substrate.** Mutations run in-process against one `LX` instance, so parallel agents already see each other's results by reading engine state. They need atomic, well-scoped mutations, not a merge tool.
3. **Undo granularity matches.** Each LXCommand is one undo step — one undo per agent action, legible to the human. A `compose_scene` firing N commands either pollutes the undo stack with N opaque steps or needs a general batch-command wrapper LX does not provide (existing batch commands like `RemoveSelectedChannels`, `MultiSetValue` are type-specific).
4. **Composability prime directive.** A `compose_scene` tool would inevitably orchestrate primitives and encode scene-assembly policy in the tool layer — drift from "tool handler = parse, call one primitive, format."

**What would change the answer:** a real "all-or-nothing" transactional use case — but the fix is a *batch/transaction* primitive (begin/commit/rollback around `lx.command`), not a semantic `compose_scene`.

## Engine-thread concurrency

**This is the top implementation risk to address in PR-1c / PR-2.**

LX mutations are expected to run on the LX engine thread. Under multi-agent fan-out, parallel MCP requests will hit one `LX` instance concurrently. If those mutations race on the engine thread, the result can be corrupt or interleaved state. The fix is **not** a coarser tool — it is a **server-side serialization queue in the MCP plumbing layer** that marshals all mutations onto the LX engine thread.

Action for PR-1c / PR-2:
- Decide and document the serialization mechanism (post mutations to the engine thread; await completion before returning the tool result).
- Add a concurrency / thread-affinity test: N parallel mutations → deterministic, consistent final state.

## Phase-2 introspection

**Verdict: metadata + parameter-level comprehension is buildable now. Source/algorithm comprehension is not — it requires decompilation outside LX's surface.**

### Buildable now (sufficient surface)

- **Class discovery:** `LXRegistry` enumerates registered pattern/effect/modulator/fixture classes (LXRegistry.java:78-150+). `LXClassLoader` adds JAR-loaded extension classes with type/name/category/author/version (LXClassLoader.java:45-100+).
- **Class-level metadata, all RUNTIME-retained:** `@LXComponent.Name` / `.Description` / `.Author` / `.Tags` / `.Hidden` / `.PluginRequired` (LXComponent.java:80-128) and `@LXCategory` (CORE/FORM/COLOR/MIDI/STRIP/TEXTURE/TRIGGER/TEST/OTHER/AUDIO/MACRO/DMX). Readable by reflection without instantiation.
- **Parameter-level comprehension:** walk the parameter tree of a live or freshly-instantiated instance — each `LXParameter` exposes `label`, `description`, `min`/`max`, value, normalized value. Enough for "what knobs does this pattern have and what do they do."

### Not on the surface

- **Algorithm / source comprehension.** Patterns/effects ship as `.class` bytecode; LX exposes no AST, no source, no structured algorithm description (research §5). A "what does this pattern *do* visually/algorithmically" tool would need a bundled decompiler (CFR/Procyon) or an out-of-band source index — a separate, clearly-fenced subsystem, not part of the core introspection primitive.

### Recommendation

Phase-2 tool v1 comprehends **metadata + parameters** (registry + annotations + parameter tree). Defer algorithm comprehension.

## Open questions / unresolved

1. **Parameter-path resolution syntax (highest-priority prerequisite).** The canonical/OSC path string syntax that `resolveParameter` / `resolveComponent` must parse is unenumerated (research gap #5; `LXPath` at LXCommand.java:34). Half the v1 tool set depends on this resolver. Needs its own investigation slice before the dependent tools land.
2. **Engine-thread concurrency mechanism (highest-priority risk).** Serialization-queue design + a concurrency test are open for PR-1c / PR-2.
3. **Cheap instantiate-and-introspect for Phase-2 parameter walks.** Parameter metadata generally requires a constructed instance (annotations alone don't list parameters). Unconfirmed whether a throwaway instantiation is safe/cheap for *every* registered class, or whether some require a model/GL context. Phase-2 spike item: "can we instantiate-and-introspect every registered pattern/effect cheaply, or only those already live in the project?"
4. **`v1` has zero direct-edit primitives.** The composability rule's "direct in-memory edit, document the undo skip" branch is defined but unused in v1 — it only appears when a future tool has no backing command (e.g. toggling a transient runtime flag). Stated explicitly so reviewers don't expect it.
