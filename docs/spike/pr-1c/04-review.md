# PR-1c review — Automated QA strategy

**Verdict: PASS.**

Independent pass over `docs/spike/qa-strategy.md` against the PR-1c open-question list (`build-plan.md`), the executable proof, and the docs-sync audit. Every question has a defended, sourced answer; the central claims are backed by tests that pass under `mvn package`.

## Open questions — coverage

| Open question (build-plan.md) | Answered? | Defense |
| --- | --- | --- |
| Can `LX` run headless in tests? | **PASS** | `new LX(model)` needs no GUI/GL (`LX.java:435-521`); engine thread never started. Proven by `HeadlessLxHarnessTest.constructsAndTicksWithoutAThread`, green under `mvn package`. Patterns cited from LX's own `LXHeadless` / `BlendingHarness`. |
| Default per-tool test shape | **PASS** | Two layers: domain-primitive unit test + MCP-handler integration test. Concrete skeletons given; verification template is copy-per-tool. |
| do → undo → assert for LXCommand mutations | **PASS** | Documented as the built-in correctness check for all v1 mutations; doubles as "is this a real LXCommand?". Proven by `HeadlessLxHarnessTest.commandRoundTripsThroughUndo` (perform `SetValue` → assert → `undo` → assert restored). Per-tool assertions delegated to the `lxcommand-mapping.md` table, not duplicated. |
| Direct-edit fallback verification | **PASS** | Defined (snapshot diff + teardown rollback) and explicitly flagged **unused in v1** (zero direct-edit primitives), matching `lxcommand-mapping.md`. No overclaim. |
| Embedded MCP server in-process testable? | **PASS** | Cross-checks PR-1a: reuses `EmbeddedMcpServerTest` as the integration harness. Consistent with `sdk-feasibility.md`. |
| CI (headless) vs local-only (live LX) | **PASS** | CI = full JUnit suite + load gate via `.github/workflows/build.yml`; local-only = Chromatik-UI visual demos. Multi-agent workflow tests scoped out to PR-7. |
| Multi-agent workflow tests | **PASS** | Out of scope for v1, flagged for manual/recorded verification in PR-7 — matches build-plan intent. |
| Engine-thread concurrency (top risk, carried from PR-1b) | **PASS** | Test shape given using `lx.engine.addTask` (`LXEngine.java:846`) drained at `:1090-1092`. Asserts consistent final state, not a fixed order. Seeded here; mechanism lands with PR-2. |

## Claim spot-checks

- `lx.engine.run()` is the synchronous single-frame advance — confirmed `LXEngine.java:981`.
- `lx.command` is the `LXCommandEngine` — confirmed `LX.java:420`; `perform`/`undo` at `LXCommandEngine.java:58/135`.
- `LXCommand.Parameter.SetValue(LXParameter, double)` exists — confirmed `LXCommand.java:489`; used in the passing harness test.
- `com.heronarts:lx:1.2.1` resolves from Maven Central (`_remote.repositories` → `central`), so the CI workflow builds on a clean runner without a pre-install step.
- Full suite green locally: `HeadlessLxHarnessTest` (2 tests) + `EmbeddedMcpServerTest` pass under `mvn package`.

## Docs-sync audit

- `docs/build-plan.md` — **updated**: PR-1b marked `[x]` (merged #5, stale "pending review" note dropped); PR-1c set `[~]` with deliverable/branch note. Accurate.
- `CLAUDE.md:78` — already references `docs/spike/qa-strategy.md`; the file now exists, so the statement is true, not stale. **No change needed.**
- `README.md` — no test/CI statements present; nothing made stale. **No change needed** (stated explicitly, not silently skipped).

## Gaps / follow-ups (none blocking)

- The concurrency test is a documented shape, not yet an executable test — correct, since the serialization mechanism it guards lands in PR-2. Tracked there.
- `verify-load.sh` was authored for macOS JDK resolution; on Linux CI `setup-java` exports `JAVA_HOME`, which the script resolves first. Confirm green on the first Actions run (verification step 3).
