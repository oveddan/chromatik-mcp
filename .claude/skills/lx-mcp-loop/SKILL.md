---
name: lx-mcp-loop
description: Run one iteration of the lx-mcp dev loop — implement the next build-plan PR per the project's layering and conventions, gate on mvn package, review, and open a single-squashed-commit PR. Use when picking up lx-mcp build work (the PR-5 fan-out, PR-6, follow-ups).
---

# lx-mcp dev loop

Run one PR per invocation. The full rationale (4-condition test, objective gates,
failure modes, future explorations) lives in [`docs/loop-engineering.md`](../../../docs/loop-engineering.md) —
this skill is the operational checklist so the loop is repeatable, not re-derived.

## Read first (every run)

Load the project's persistent knowledge before touching code:

- [`CLAUDE.md`](../../../CLAUDE.md) — the composability prime directive and layering rule.
- [`docs/tool-conventions.md`](../../../docs/tool-conventions.md) — naming, canonical-path
  addressing, `Result` wire shape, the mutations contract (`lx.command.perform()` swallows
  failures — verify by state-read and throw), threading.
- [`docs/lx-coding-guidelines.md`](../../../docs/lx-coding-guidelines.md) — LX idioms from
  upstream review.
- [`docs/spike/qa-strategy.md`](../../../docs/spike/qa-strategy.md) — the per-tool test
  template (domain-primitive unit test + handler integration test + do→undo→assert).
- [`docs/build-plan.md`](../../../docs/build-plan.md) — the tracker (what's next) and the
  per-PR specs.

## Pipeline

1. **Sync + claim.** `git checkout main && git pull`. Pick the next `[ ]` PR in tracker
   order; mark it `[~]` with the branch name (the edit travels in the PR, not on `main`).
2. **Branch.** Off `main`, or off the previous PR's branch when stacking. Keep each branch
   to exactly **one squashed commit** on its base.
3. **Implement.** Tool handler → domain primitive → `LXCommand`/engine. Stay inside the
   PR's build-plan slice.
4. **Gate.** `cd package && mvn package` — must be green (compiles + full JUnit suite +
   headless harness). This is the objective bar; do not weaken it to pass.
5. **Review (recommended).** Spawn a fresh-context review agent on the diff vs. the branch
   base, briefed with the PR spec + `CLAUDE.md` + `qa-strategy.md`. Fix real findings;
   re-run the gate. (Ad hoc by design — your judgment, not a mandated step.)
6. **Open PR.** Squash to one commit, push, `gh pr create` with base = the stack parent.
   Body carries the gate result + review summary. The user merges.
7. **Maintain the stack.** After a base PR is squash-merged, rebase the remaining
   single-commit branches and retarget with `gh pr edit --base`.

## Never do

- Construct an `LXCommand` or mutate `lx.engine.*` inside a tool handler — that lives in a
  domain primitive (`CLAUDE.md` layering).
- Touch `lx.*` off the `EngineExecutor` thread — every handler marshals through
  `EngineExecutor.call(...)`.
- Disable, skip, or weaken a test to make the gate pass.
- Exceed the PR's build-plan slice — split it instead (the scope guard).

## Capture what you learn

Distill bugs and non-obvious findings into the conventions docs and file-based memory
(`~/.claude/projects/-Users-danoved-Source-lx-mcp/memory/`), not into chat. Fail →
investigate → verify → distill → consult.
