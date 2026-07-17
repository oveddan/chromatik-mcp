---
name: lx-mcp-fix
description: Dispatch a fix for a gap found while live-testing the MCP server — root-cause now, propose a one-line dispatch, spawn a background pr-implementer in a worktree on confirmation, return to live testing. Use whenever live driving surfaces a missing tool, wrong payload, misleading description, or client-etiquette fault.
---

# lx-mcp fix dispatch

Turns a live-testing finding into an in-flight PR without pausing the testing
session. The main session is the **dispatcher**: it stays connected to the live
Chromatik instance and keeps testing with the user while fix agents run in
background worktrees. The live jar is untouched by any fix work — testing never
blocks on fixes, and fixes never wait on testing.

## Pipeline (per finding)

1. **Root-cause NOW, while the live evidence is fresh.** Reproduce against the
   live instance (never from cached responses). Identify: which tool/payload is
   wrong, the exact LX ground truth (`/Users/danoved/Source/LX/` — file:line),
   and which repo files change. Scouting is the dispatcher's job; the fix agent
   must re-discover nothing.
2. **Classify the fix:**
   - **Server gap** (payload field, tool description, new tool, INSTRUCTIONS
     text, catalog entry) → code PR, go to step 3.
   - **Client etiquette** (how the AI client should behave) → edit `CLAUDE.md`
     "Driving a live instance" + memory directly; no agent.
   - **Too small for its own PR** (a sentence in a description, one field) →
     queue it (step 5); batch later.
3. **Propose, then dispatch on confirmation.** Give the user a one-line
   summary: symptom → fix → files → branch `pr-<slug>`. Never spawn a fix
   agent unprompted. On "go": spawn `pr-implementer` (background,
   `isolation: "worktree"`, branch off `main`). Front-load the prompt with:
   the live symptom, LX file:line facts, exact files to touch, the wire-shape
   change with **verified** example JSON (never fabricated), test
   expectations, gate via `package/scripts/build-gate.sh`, single squashed
   commit, push + `gh pr create`.
4. **Return to testing immediately.** Do not wait on agents. Batch completed
   PRs; at a natural testing pause: review each diff (session model), route
   prescribed fixes to `pr-fixer` (or SendMessage the same implementer for
   same-branch follow-ups), re-gate, squash-merge. The dispatcher merges.
5. **Findings queue: `docs/live-findings.md`** (create if absent). One line
   per queued small finding: date, symptom, proposed fix, LX ref. When 3+
   related items accumulate, propose them as one batched slice. Delete lines
   when their fix merges.
6. **Batch boundary.** Merged fixes reach the live instance only when the
   user rebuilds/installs the jar and restarts Chromatik. On reconnect:
   re-read `~/.chromatik-mcp/status.json`, re-initialize, re-list, then
   **live-verify every fix in the batch first** before resuming exploratory
   testing.

## Rules

- One finding = one branch = one squashed commit, off `main`; never stack fix
  branches on each other.
- Parallel dispatches are fine (proven: 5 concurrent), but each in its own
  worktree — never two builds sharing one worktree's `target/` (deadlocks).
- The gate is fast (~12s offline). Run trivial gates directly in the
  dispatcher; use `pr-verifier` only for independent post-rebase re-checks —
  never inside an implementer's fix loop.
- Any need to read LX source to answer a *live* question is itself a finding —
  the knowledge must ship in the server (payload/description/catalog), because
  end consumers have no LX source.
- If a dispatched fix uncovers a bigger design question, the agent stops and
  reports (per its definition); the dispatcher decides with the user.

## Cost posture

Tiering per memory `[[subagent-model-policy]]`: implementers Sonnet, fixers
Haiku, reviews on the session model. Recommended dispatcher session model:
**Opus** (/fast is fine — live driving is tool-call-heavy, not judgment-heavy);
switch to Fable only for design-heavy review passes. Fresh agent per
independent finding; resume (SendMessage) only for follow-ups on the same
branch.
