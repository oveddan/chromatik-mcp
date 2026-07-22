---
name: chromatik-mcp-review
description: Dispatch-agnostic skeptical code review of a chromatik-mcp PR branch — wraps the built-in /code-review skill with the project's review criteria. Use before every PR opens (mandated by the chromatik-mcp-loop skill) or during batch review in the chromatik-mcp-fix dispatcher.
---

# chromatik-mcp review

Wraps the built-in `/code-review` skill with the project-specific checkpoints in
[`docs/review-criteria.md`](../../../docs/review-criteria.md), so the generic review and
the layering/tool-convention rules this repo actually cares about both run every time —
no re-deriving what a reviewer should look for per PR.

## Inputs

- **Branch name** — required.
- **Base ref** — defaults to `origin/main`.
- **Effort level** — optional; if unset, the dispatching session picks one per the
  [Orchestrator knobs](#orchestrator-knobs) below.

## Procedure

1. **Operate in the branch's worktree** (`.claude/worktrees/<branch>`). Confirm the tree
   is clean (`git status`) and on the right branch before reviewing anything — a dirty
   or wrong-branch worktree invalidates the diff.
2. **Read `docs/review-criteria.md`** and hold its checkpoints alongside the built-in
   review's generic dimensions (correctness, security, style) for the rest of the pass.
3. **Run `/code-review <effort> <base>...<branch>`.**
4. **Additionally check the diff against each checkpoint in `docs/review-criteria.md`** —
   these are the things a generic review misses because they're this project's
   conventions, not general Java/security practice (layering, `perform()` swallowing
   failures, Result-shaped errors, LX idioms, listener symmetry, tool-surface
   conventions, test coverage, scope guard).
5. **Output ONE compact verdict**: either `PASS`, or a findings list. Nothing else — no
   summary of what was reviewed, no praise, no restating the diff. Each finding:
   `file:line — summary — why it's real (evidence)`.

## Skeptical stance

- Default-refute: assume the diff has a problem and try to find it, rather than scanning
  for confirmation that it's fine.
- Report only findings you could defend to the implementer with evidence (a line, a
  quoted doc rule, a reproducible failure) — not vibes.
- Do not praise the diff. An empty findings list must mean "I tried to break it and
  couldn't," not "looks good."
- Fixes are not this skill's job — findings route to `pr-fixer` (or the implementer for
  same-branch follow-ups). This skill only reports.

## Orchestrator knobs

The **dispatching session** decides, per slice:

- **Model tier** — which model runs the review.
- **Effort level** — the `/code-review` effort argument.
- **Isolation** — inline in the current session, or a fresh-context subagent.

Recommended defaults:

- **Fresh-context dispatch + high effort** for domain/Java slices, or anything touching
  `LXCommand`/engine mutation — highest payoff for an independent evaluator.
- **Inline + medium effort** for small mechanical diffs.
- **Skip entirely** only for docs-only or purely mechanical-rename PRs, and say so in the
  PR description.

Rationale: an independent evaluator pays off at the edge of the implementer's
capability, and is overhead inside it.
