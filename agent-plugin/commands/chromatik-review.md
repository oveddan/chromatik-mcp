---
description: Lint the currently-open Chromatik project against a fixed twelve-check list (dead modulation, unreachable clock sources, colliding OSC addresses, unmatched view selectors, MIDI mapping problems, no-op snapshots, per-class no-op conditions) and report defects, questions, and coverage. Never touches the project's .lxp file or LX source, and never mutates anything.
argument-hint: "[path to a project-conventions file] (optional — promotes a stated rule from question to defect)"
---

Run the lint-tier review from the `chromatik-reviewer` agent against the live Chromatik
instance. Read that agent file first — it defines the twelve checks, their downgrade
conditions, and the output format this command surfaces unchanged. Everything below is
orchestration; the agent file is the check-list authority.

## 1. Identify the project and confirm

Call `get_status` and `get_project_info`. This step's own `get_status` call succeeding is
what lets step 3 tell apart the two causes of the reviewer's self-check failure, the same
way it does for `/chromatik-learn`.

**Show the user, before doing anything else:**
- the project path `get_project_info` reports (or that it reports none, if unsaved)
- the LX version and server version from `get_status`
- whether `$ARGUMENTS` names a project-conventions file, and its path if so

**Wait for explicit confirmation before dispatching.** Reviewing the wrong open project
silently is worse than not running at all — if `get_project_info` reports no project path,
or a path the user doesn't recognize, stop and say so instead of guessing.

## 2. Read the optional conventions file

If `$ARGUMENTS` names a path, read it now. This file is free-form: whatever downgrade
conditions in the agent file's twelve checks the project wants promoted from a question to
a defect, in the project's own words — it cannot add a thirteenth check or promote a rule
outside that list, per the agent file's "Convention promotion" section. Pass its contents
into the reviewer's dispatch as part of its task. If `$ARGUMENTS` is empty, dispatch
without one — every downgrade condition in the agent file applies as written, nothing gets
promoted.

## 3. Dispatch the reviewer

Dispatch the `chromatik-reviewer` agent once, with:
- confirmation that the project identified in step 1 is the one to review
- the conventions file's contents, if step 2 read one

If it comes back reporting its `get_status` self-check failed, don't treat that as an
empty review — diagnose before surfacing anything, the same way `/chromatik-learn` does:
- if this command's own step-1 `get_status` succeeded and the reviewer's still failed, the
  reviewer's tool allowlist doesn't match the plugin's actual MCP server name — tell the
  user that.
- if step 1 also would have failed (or Chromatik has since gone away), Chromatik isn't
  running or its port isn't pinned in `~/.chromatik-mcp/status.json` — tell the user that
  instead.

## 4. Surface the report

Return the reviewer's four sections — Live state, DEFECTS, QUESTIONS, Not inspected —
unchanged. Don't summarize DEFECTS or QUESTIONS away; a short summary belongs only in your
closing line, after the full sections, and only as a count (how many defects, how many
questions, how many checks Not inspected flagged as unsupported), never as a replacement
for the sections themselves.

This command never writes anywhere — no profile file, no project edit. It only reads live
state and reports.
