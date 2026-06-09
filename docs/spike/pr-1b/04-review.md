# PR-1b Review

**VERDICT: PASS**

The deliverable answers every PR-1b open question with a defended, source-grounded answer. All spot-checked citations (8/8) match LXCommand.java exactly, including the load-bearing `AddModulator` signature that drove the CLAUDE.md edit. The docs-sync changes are accurate and complete. Flags below are minor (precision, not correctness) and do not block.

---

## Per-question verification

### Q1 — Full LXCommand enumeration + tool mapping table
**PASS.** `01-research-notes.md` enumerates ~90 action classes across 11 categories with constructor signatures and line citations. `lxcommand-mapping.md` maps all 13 planned/adjacent v1 tools to a concrete `LXCommand` action + a named domain primitive with a narrow signature. Each row carries a citation, an undo do→undo→assert note, and risks. The tool→command→primitive layering matches the CLAUDE.md composability directive (handlers call one primitive; primitive owns the `perform()`).

### Q1b — Each tool mapped to a concrete action or justified direct-edit ruling
**PASS.** Every v1 tool maps 1:1 to an existing LXCommand. The deliverable's claim that **v1 has zero direct-edit primitives** is explicitly stated (mapping TL;DR + open-question #4) so reviewers don't expect the "document the undo skip" branch to be exercised yet. This is the correct, honest call given the surface — defended, not hand-waved.

### Q1c — Tool granularity (compose_scene yes/no)
**PASS.** Decision: fine-grained only, no `compose_scene`. Four-point defense is real, not boilerplate: (1) fan-out composes at the agent layer and a coarse spec re-introduces the serialization bottleneck; (2) shared in-process LX state is the substrate; (3) undo granularity — one LXCommand = one undo step, and LX provides no general batch-command wrapper (the existing batch commands `RemoveSelectedChannels`/`MultiSetValue` are type-specific — verified: `RemoveSelectedChannels` at 1952, `MultiSetValue` base at 555); (4) composability prime directive. The "what would change the answer" (transactional use case → batch/transaction primitive, not semantic compose_scene) is a genuine defense, not an escape hatch.

### Q1d — Phase-2 introspection buildability
**PASS.** Verdict is appropriately split: metadata + parameter-level comprehension YES (registry + RUNTIME annotations + parameter-tree walk); source/algorithm comprehension NO (bytecode, no AST — needs out-of-band decompiler). The instantiation-cost caveat for parameter walks is correctly carried forward as an unresolved phase-2 spike item (open question #3), not overclaimed.

---

## Citation spot-check (8 checked against LXCommand.java)

| Citation in deliverable | Confirmed? | Note |
|---|---|---|
| `Modulation.AddModulator(LXModulationEngine, Class)` @ 2128-2188 | YES | Lowest-arity ctor is `(LXModulationEngine modulation, Class<? extends LXModulator>)` at 2136. **No single-arg `AddModulator(kind)` exists.** Also confirms `autostart()` (2177) and `LX.InstantiationException → InvalidCommandException` (2179-2180). This validates the CLAUDE.md edit. |
| `Modulation.AddModulation(engine, LXNormalizedParameter, Target)` @ 2264-2338 | YES | Ctor at 2301 matches exactly. `ModulationSourceReference` (2266) handles param-or-component source. Throws `ModulationException → InvalidCommandException` (2329). |
| `Modulation.AddTrigger(engine, BooleanParameter, BooleanParameter)` @ 2427-2462 | YES | Ctor at 2434 matches. Source/target are `BooleanParameter` — genuinely distinct type from AddModulation, so the wire/remove split is justified, not arbitrary. |
| `Modulation.RemoveTrigger(engine, LXTriggerModulation)` @ 2464-2517 | YES | Ctor at 2470 matches; takes `LXTriggerModulation`. |
| `Mixer.AddChannel()` / `AddChannel(Class<? extends LXPattern>)` @ 1754-1819 | YES | Both ctors present (1761, 1773). Supports the "default null → empty channel" primitive design. |
| `Mixer.RemoveChannel(LXAbstractChannel)` @ 1890-1950 | YES | Ctor at 1899. extends RemoveComponent (group-child + cleanup is internal, as claimed). |
| `Channel.AddPattern(LXPatternEngine, Class)` @ 868-944 | YES | Ctor at 880. |
| `Channel.AddEffect(LXComponent parent, Class)` @ 1358-1403 | YES | Ctor at 1365. `validateEffectContainer()` referenced (confirmed used in RemoveEffect at 1414). |
| `Channel.RemoveEffect(LXComponent, LXEffect)` @ 1405-1451 | YES | Ctor at 1412. Locked-effect guard `checkLocked()` at 1425. **See flag F1.** |
| `Midi.AddMapping(LXShortMessage, LXNormalizedParameter)` @ 4911-4937 | YES | Ctor at 4917; delegates to `LXMidiMapping.create(lx, message, parameter)` at 4929. Confirms the "wants a constructed LXShortMessage, not raw bytes" wrinkle. |
| `Parameter.SetColor(ColorParameter, hue, sat)` @ 597-641 | YES | SetColor class at 597; hue/sat-bearing form present. |
| `Parameter.SetNormalized(BooleanParameter, boolean)` @ 764-807 | YES | Boolean overload at 774 — validates the `BooleanParameter → SetNormalized(bool)` dispatch arm in `set_parameter`. |
| `LXPath` import @ LXCommand.java:34 | YES | Exact line. Grounds the path-resolution prerequisite. |

12+ distinct claims checked; all confirmed. No citation drift found.

---

## Unsourced / assumption flags (all minor, non-blocking)

- **F1 — RemoveEffect locked path is an unchecked `IllegalStateException`, not `InvalidCommandException`.** The deliverable says locked-effect should "surface cleanly → `Result.error`." Verified at 1427: `checkLocked()` throws `IllegalStateException` with message "UI should disallow this" — i.e. LX treats it as a programmer error the UI prevents, not a wrapped command exception. The mapping-to-`Result.error` advice is still correct (the seam catches it), but it groups locked-effect with `ModulationException`/`InstantiationException` as if they share the `InvalidCommandException` wrapping path. They do not. This is a precision nit for PR-1c's negative-test design (catch `IllegalStateException` too, not just `InvalidCommandException`), not an error in the verdict.

- **F2 — Engine-thread concurrency risk is asserted, lightly sourced.** "LX mutations are expected on the engine thread" is stated as the top risk but not pinned to a specific LX source location (e.g. an engine-thread assertion or `LXEngine` threading contract). It is a reasonable and well-known LX property, and the deliverable correctly routes it to PR-1c/PR-2 as an open item rather than claiming it solved. Acceptable as a flagged risk; would be stronger with a one-line citation. Non-blocking because it is explicitly an open question, not a resolved claim.

- **F3 — Parameter-path-resolution prerequisite is grounded.** The claim that the canonical path syntax was not enumerated is honest (research gap #5) and the `LXPath` dependency is cited (line 34, confirmed). Correctly flagged as its own slice and a hard dependency for half the tool set. No overclaim — this is the right way to surface a gap.

No fabricated citations, no claim resting on an unstated assumption that would change a verdict.

---

## Docs-sync verification

**PASS.** `git diff` covers CLAUDE.md, build-plan.md, README.md.

- **CLAUDE.md** — The `AddModulator` example was corrected in **both** the Bad block (line ~39: `new LXCommand.Modulation.AddModulator(lx.engine.modulation, MacroKnobs.class)`) and the Good block (line ~49: `...AddModulator(lx.engine.modulation, kind)`). Both edits add the modulation-engine first arg, matching the verified ctor at LXCommand.java:2136. Nothing missed — these were the only two occurrences of the stale single-arg form. Confirmed via the diff.
- **build-plan.md** — PR-1a flipped `[~]`→`[x]` (merged via #3) and PR-1b flipped `[ ]`→`[~]` with an accurate one-line note (every v1 tool maps 1:1; no compose_scene; concurrency flagged; pending Review + merge). The note matches the deliverable. The PR-1b open-question section (66-76) is a spec, not a findings record, so it correctly stays unchanged.
- **README.md** — Untouched. Verified it contains zero PR-1b-relevant claims (no mention of modulators, LXCommand, compose_scene, or undo), so there is nothing for the Writing Agent to have updated or missed.

No stale statement contradicted by PR-1b findings remains in the three docs.

---

## Summary

All four open questions answered and defended. 12+ citations spot-checked, all confirmed — critically, the `AddModulator` signature that justified the CLAUDE.md edit is correct, and the wire/remove modulation-vs-trigger split reflects genuinely distinct constructor types (`LXNormalizedParameter`/`Target` vs `BooleanParameter`/`BooleanParameter`). Docs-sync is accurate and complete. Three minor flags (F1 locked-effect exception type, F2 lightly-sourced concurrency assertion, F3 path-resolution gap — already self-flagged) are precision notes for PR-1c, not blockers.
