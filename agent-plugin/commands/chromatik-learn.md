---
description: Survey the currently-open Chromatik project over live MCP tools and derive a local project profile (conventions, per-class practice, external control, confidence, open questions). Never touches the project's .lxp file or LX source, and never writes anywhere but ~/.chromatik-mcp/profiles/.
argument-hint: "[concern ...] (optional — defaults to all six: structure color effects pattern-modulation global-modulation external-control)"
---

Derive a project profile from the live Chromatik instance, per the `project-profile`
skill. Read that skill first — it defines the profile format, the six concerns, and the
per-instance table shape this command's output must use. Everything below is
orchestration; the skill is the format authority.

## 1. Identify the project and confirm

Call `get_status` and `get_project_info`. From `get_project_info`'s `projectPath`, derive
the profile slug per the skill's rule (lowercase basename, `.lxp` stripped,
non-alphanumeric runs collapsed to `-`). This step's own `get_status` call succeeding is
what lets step 2 tell apart the two causes of a surveyor's self-check failure.

**Show the user, before doing anything else:**
- the project path `get_project_info` reports
- the LX version and server version from `get_status`
- the profile path this run would write to (including whether a profile already exists
  there and would be archived)
- which concerns will run — all six, or the subset named in `$ARGUMENTS`

**Wait for explicit confirmation before proceeding.** Surveying the wrong open project
silently is worse than not running at all — if `get_project_info` reports no project
path, or a path the user doesn't recognize, stop and say so instead of guessing.

## 2. Fan out one surveyor per concern

For each confirmed concern, dispatch the `project-surveyor` agent with that concern as
its task (e.g. "Survey the `color` concern"). Run all dispatches concurrently — they're
independent read-only passes over the same live instance, and the skill's six concerns
exist precisely so this fan-out doesn't need coordination between them.

If a surveyor comes back reporting its `get_status` self-check failed, stop dispatching
and diagnose before surfacing anything — the surveyor can't tell you the cause, only the
symptom. You already know your own step-1 `get_status` succeeded, so:
- if step 1 succeeded and a surveyor's `get_status` still failed, the surveyor's own tool
  allowlist doesn't match the plugin's actual MCP server name — tell the user that, and
  that it affects every dispatch identically (a broken allowlist is either fully blocking
  every concern or none, so a working majority from the other five is impossible; don't
  treat them as a complete survey).
- if step 1 also would have failed (or Chromatik has since gone away), the cause is that
  Chromatik isn't running or its port isn't pinned in `~/.chromatik-mcp/status.json` —
  tell the user that instead, since fixing an allowlist that was never the problem wastes
  their time.

## 3. Synthesize into the profile format

Combine the six reports into one document, in the section order the `project-profile`
skill specifies: Project, Conventions, Practice, External control, Confidence, Open
questions, Not surveyed.

- Merge overlapping evidence rather than concatenating reports — e.g. a class mentioned
  by both the `color` pass (its palette mode) and the `pattern-modulation` pass (its
  per-instance table) gets one `## Practice` subsection, not two. `pattern-modulation` is
  the only concern that emits per-instance target tables, so there is never more than one
  such table to merge for a given instance.
- Carry every per-instance modulation table through unmodified into `## Practice` — this
  step aggregates and rolls up, it does not re-derive or drop rows.
- Every convention you write into `## Conventions` needs its evidence count, per the
  skill's rule. Don't estimate it and don't recompute it from the raw tables — each
  surveyor reports its own concern's convention candidates with counts already attached;
  pull those through.
- Populate `## Confidence` and `## Open questions` from what the surveyors flagged as
  uncertain, absent, or unanswerable — these two sections are required by the skill, not
  optional summaries you can skip if nothing came up short.
- `## Not surveyed` covers concerns that didn't run (if `$ARGUMENTS` narrowed the set),
  recursion depth any surveyor reported falling short on, and any question a surveyor
  named a tool gap for.

## 4. Archive and write

If the `profiles/` directory doesn't exist yet, create it and write a `.gitignore`
containing a single `*` into it before writing anything else — this is the mitigation
that makes the privacy claim in the skill true by construction even if `$HOME` is itself
a git repository.

If a profile already exists at the target path, archive it before writing the new one:
read its `## Project` section for the date it was surveyed, and rename it to
`<slug>-<that-date>.md` in the same directory — not today's date, the archived profile's
own recorded survey date. If a file already exists at that archive path (a same-day
re-survey being archived a second time), append `-2`, `-3`, etc. rather than overwriting
it. Never overwrite a profile — original or archived — silently. Then write the new
profile to `~/.chromatik-mcp/profiles/<slug>.md`.

Tell the user the exact path you wrote, and the exact archive path if one was created.

## 5. Report a short summary

Close with a compact summary, not a restatement of the whole profile:
- how many conventions were found (with a sense of their evidence range, e.g. "6, from 1
  instance up to 17 of 18")
- how many classes got a `## Practice` subsection
- how many open questions were raised
- what wasn't surveyed and why (skipped concerns, recursion limits, tool gaps)

Remind the user, briefly, that this profile stays on their machine at the path above —
this command never commits it, publishes it, or copies it into any repository.
