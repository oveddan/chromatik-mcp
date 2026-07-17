---
title: How it was built
description: lx-mcp was built by a fleet of coding agents running a formalized dev loop — small PRs, objective gates, fresh-context review.
---

lx-mcp is itself a product of the workflow it enables: it was built almost entirely by
coding agents (Claude Code) running a formalized loop, with a human reading every diff
before merge. Agents built the tool that lets agents drive light art.

This page is a condensed account; the full engineering writeup lives in
[`docs/loop-engineering.md`](https://github.com/oveddan/lx-mcp/blob/main/docs/loop-engineering.md).

## The dev loop

One PR per iteration:

1. **Sync + claim** — pick the next slice from a build-plan tracker checked into the
   repo. The agent forgets between sessions; the repo doesn't.
2. **Branch** — each branch is exactly one squashed commit on its base, so stacked PRs
   rebase as clean cherry-picks.
3. **Implement** — inside a strict layering contract (tool handler → domain primitive
   → `LXCommand`), with conventions docs the loop re-reads every iteration so long
   sessions don't drift.
4. **Gate (objective)** — `mvn package`: full JUnit suite, a headless LX harness, and
   do→undo→assert tests proving each mutation used a *real* undoable command. Green is
   the bar; a review agent's opinion never substitutes for it.
5. **Review (fresh context)** — a separate review agent, spawned clean, reads the diff
   against the PR spec. The implementer never grades its own homework.
6. **Open PR** — one squashed commit; the human merges.

## Division of labor

Work is tiered by what it actually requires:

- **pr-implementer** (mid-tier model) — implements one fully-specified PR slice in an
  isolated git worktree; multiple slices run in parallel.
- **pr-verifier** (small model, throwaway context) — runs the build gate, returns
  PASS/FAIL.
- **pr-fixer** (small model) — applies exactly-specified review fixes; if a fix needs
  judgment, it stops and reports instead of improvising.
- The session's strongest model is reserved for design, review, and orchestration.

Prompts front-load everything the orchestrator already discovered — file paths, line
numbers, verified framework semantics — so subagents re-discover nothing.

## Failure modes, named

Naming them is how you catch them in the act:

- **The half-done "complete"** — a loop that finishes because nothing objective failed
  it. Mitigation: the gates.
- **Self-preferential bias** — implementers grade their own work too kindly.
  Mitigation: fresh-context reviewers.
- **Goal drift** — long sessions lose early constraints. Mitigation: re-read the
  conventions docs each iteration.
- **Comprehension debt** — code ships faster than anyone reads it. Mitigation: small
  PRs, human reads every diff.

## Lessons compound in the repo, not the chat

When a bug reveals a framework quirk (e.g. LX's `command.perform()` silently swallows
failures), the fix isn't just the patch — it's a rule distilled into the conventions
docs, consulted by every future loop iteration. *Fail → investigate → verify →
distill → consult.*

## The loop the tool itself closes

The dev loop built the product; the product enables a second loop at runtime: an
agent mutates the show, reads `get_frame`, grades the result against a visual rubric
in an independent context, and self-corrects toward a look. That's the thesis in one
sentence — give the agent hands (tools), eyes (`get_frame`), and knowledge
(the semantic catalog), and creative iteration becomes a conversation.
