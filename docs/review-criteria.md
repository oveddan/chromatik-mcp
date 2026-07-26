# Review criteria for chromatik-mcp PRs

Loaded by the [`chromatik-mcp-review`](../.claude/skills/chromatik-mcp-review/SKILL.md)
skill alongside the built-in `/code-review`. Part A is the checklist; Part B is
calibration — real findings from this project's history, so "is this a real finding"
has a concrete bar instead of being re-derived per PR.

## Part A — checkpoints

Each checkpoint below is a place a generic code review tends to miss because the rule is
this project's convention, not general practice. Quoted/pointed, not duplicated at
length — read the source doc for the full rationale.

- **Layering.** Tool handlers must not construct `LXCommand` or mutate `lx.engine.*`
  directly — that belongs in a `domain/` primitive. *Why:* the composability prime
  directive (`CLAUDE.md`) — one primitive per intent means one place to change if the
  implementation strategy (undo-backed vs. direct edit) ever swaps.
- **`perform()` failure verification.** `lx.command.perform()` swallows command failures
  (pushes a UI error, returns normally) — every mutating primitive must verify the
  mutation applied by reading engine state back, and throw if it didn't.
  *Why:* [`docs/tool-conventions.md`](tool-conventions.md) "Mutations" — "command-backed"
  does not mean "cannot fail."
- **Result-shaped errors at the MCP boundary.** Exceptions are mapped to `Result.error(...)`
  at the seam (`chromatikmcp.tools.Tools`); they never cross a handler as a raw
  exception/stack trace. *Why:* `CLAUDE.md` code style + `tool-conventions.md` wire shape
  — clients dispatch on stable error codes, not exception messages.
- **LX idioms.** Per [`docs/lx-coding-guidelines.md`](lx-coding-guidelines.md): model
  variants with `enum`s, not maps/magic constants/parallel classes; share an `interface`
  across implementations; use framework helpers (`setColors`, `EnumParameter` labels)
  instead of reinventing them; no allocation in render/per-frame loops; keep diffs
  minimal and history clean.
- **Symmetric listener lifecycle.** Every `addListener`/container-attach has a matching
  `removeListener`/detach in `dispose()`, in the right order (detach vs. resource
  release are distinct calls — both are usually needed). *Why:* `CLAUDE.md` code style
  ("register/unregister listeners symmetrically"); orphaned listeners leak on
  disable/re-enable.
- **Tool surface conventions.** Per [`docs/tool-conventions.md`](tool-conventions.md):
  `verb_noun` naming, canonical-path addressing via `chromatikmcp.domain.Resolve`, the
  `Result<T>` wire shape (structuredContent + text mirror, stable error codes), and the
  engine-thread rule (every handler marshals through `EngineExecutor.call(...)`; Tomcat
  worker threads never touch `lx.*` directly).
- **Tests.** Every new domain primitive has a JUnit test against headless LX
  (`package/src/test/java/chromatikmcp/domain/`); every new tool handler has an
  integration test in `ToolsIntegrationTest`, including the tool-registry name set.
  Mutations additionally get do → undo → assert (the undo assertion is the proof a real
  `LXCommand` was used).
- **Scope guard.** The diff is no larger than its build-plan slice
  (`docs/build-plan.md`). *Why:* `CLAUDE.md` — a PR that isn't independently demoable is
  too big; split it instead of flagging-and-shipping.

## Part B — calibration examples

Mined from merged PR history (`gh pr list --state merged`, then `gh pr view <n>`) —
dedicated "review fixes" PRs that followed an earlier repo-wide review pass. These are
concrete instances of the checkpoints above catching real bugs, not style nits.

1. **Context:** `Modulators.setModulationRange` verified a mutation by exact-equality
   read-back on a `CompoundParameter` bounded to `[-1, 1]`.
   **Finding:** the read-back check false-failed whenever the requested value fell
   outside the bound and got silently clamped (e.g. `range: 1.5` applied as `1.0` but
   the `!=` check threw `IllegalStateException`, surfacing as `internal` to the client)
   — the verification logic didn't account for parameter clamping.
   **Why it mattered:** a correct mutation was reported as a failure to the caller; the
   fix (PR #52) removed the redundant strict check and added a clamp-behavior test.

2. **Context:** `addModulator`/`removeModulator` called `lx.command.perform` directly
   with bespoke post-condition checks, while every other mutation primitive routed
   through a shared `Commands.perform` helper.
   **Finding:** verification-logic drift — two primitives re-implemented (and could
   silently diverge from) the shared failure-detection convention.
   **Why it mattered:** the fix (PR #52) folded both into the shared helper, so the
   `perform()`-swallows-failures rule has exactly one implementation instead of three.

3. **Context:** the ParameterInfo→Map wire serialization was copy-pasted across
   `get_parameter`, `list_parameters`, and `set_parameter`.
   **Finding:** the copies had already drifted — `set_parameter`'s response emitted only
   a subset of the fields `get_parameter`/`list_parameters` returned for the same
   underlying object.
   **Why it mattered:** violates the "shared wire formatter" pattern implicitly required
   by tool-conventions' consistency expectations; the fix (PR #53) extracted one
   `ParameterInfo.toMap()` and made all three handlers call it, closing the gap.

4. **Context:** `remove_channel`/`remove_effect`/`remove_pattern` payloads omitted the
   `kind` field that `remove_modulation`/`remove_modulator`/`remove_view` already
   returned.
   **Finding:** inconsistent `remove_*` payload shape across otherwise-parallel tools.
   **Why it mattered:** breaks an agent's ability to generically dispatch on `kind`
   across all remove tools; fixed in PR #53 by aligning all six.

5. **Context:** `ChromatikMcpUiPlugin` added a status section to `ui.leftPane.global` on enable
   but had no matching teardown.
   **Finding:** missing symmetric dispose — the UI section and its `ServerStatus`
   parameter listener were orphaned on plugin disable/re-enable, verified against glx
   bytecode to require both `removeFromContainer()` and `dispose()` (detach vs. resource
   release are distinct, neither subsumes the other).
   **Why it mattered:** a real resource/listener leak on a common lifecycle path (LX
   plugin disable/re-enable); caught by the "symmetric listener lifecycle" checkpoint,
   fixed in PR #54.

Seed list — append future findings the maintainer confirms, one per loop iteration.
