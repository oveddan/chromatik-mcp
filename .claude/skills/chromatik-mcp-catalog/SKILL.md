---
name: chromatik-mcp-catalog
description: Generate or refresh the hash-keyed semantic catalog (package/src/main/resources/catalog/) for LX patterns/effects/modulators — locate sources, skip fresh entries by source hash, summarize changed classes with Sonnet subagents, and record bytecode hashes for runtime staleness detection. Use when adding catalog coverage or after LX/content-repo code changes.
---

# chromatik-mcp catalog generation

Regenerates the semantic catalog defined by [`docs/catalog-format.md`](../../../docs/catalog-format.md)
— read that contract first; it owns the schema. Entries are a **cache of source
understanding keyed by `sourceSha256`**: the whole point of this skill is that re-running
it is cheap and safe.

## Inputs

- [`sources.json`](sources.json) (this directory, committed): logical repo →
  `{url, srcDir, classBytes, version, target}` — **canonical identity only, no local
  paths** (they differ per machine).
  - `url`: the GitHub repo — provenance, and where another machine clones from.
  - `classBytes`: `maven:<group>:<artifact>:<version>` (resolved under `~/.m2` — for
    stock LX this is the exact bytes Chromatik loads) or a repo-relative dir like
    `target/classes`.
  - `target`: symbolic — `chromatik-mcp` → `package/src/main/resources/catalog/` (ships in our
    jar; stock LX only), `repo` → the content repo's own `src/main/resources/catalog/`
    (docs ship inside *its* jar — preferred when you have write access), `overlay` →
    `~/.chromatik-mcp/catalog/` (machine-local; for content you can read but not modify).
- Local checkout paths, resolved **per repo key**, in order, **first match wins**: each
  repo is looked up in each source below in turn, and a source that exists but lacks
  that repo's key falls through to the next source *for that repo only* — a present
  `sources.local.json` does not block fallthrough for repos it doesn't mention, and
  does not stop a legitimate override for repos it does.
  1. `~/.chromatik-mcp/catalog-sources.json` — machine-level, same `{"<repo>":
     "<absolute path>"}` shape as `sources.local.json` below. Lives outside every
     checkout, so it resolves identically no matter which worktree or skill-directory
     name (pre- or post-rename) is running. This is the preferred home — set it up once
     per machine.
  2. `sources.local.json` (this directory, **git-ignored** — copy
     [`sources.local.example.json`](sources.local.example.json)): repo name → local
     checkout path. A per-checkout override for the rare case where a repo's local path
     needs to differ from the machine-level file for this run.
  3. Neither has an entry → clone the repo from `url` into a scratch dir, or skip it.
     This is a **setup defect, not routine behavior**: it means the machine-level file
     is missing an entry for this repo. Record it as `cloned-to-scratch` (or `no-source`
     if skipped) in the run report, and surface it as a **top-line warning**, not buried
     mid-report — name the missing repo key and the file it belongs in
     (`~/.chromatik-mcp/catalog-sources.json`), so the reader has the exact one-line fix.

  Extending coverage to a new repo = one entry in `sources.json`, plus one entry in
  `~/.chromatik-mcp/catalog-sources.json` (once, machine-wide). If a checkout
  intentionally diverges from the machine-level path for a given run, add a matching
  entry to `sources.local.json` too — otherwise this checkout falls through to a clone
  or skip.

## Pipeline

1. **Class list.** Prefer *live*: read the port from `~/.chromatik-mcp/status.json` and call
   `list_available_patterns` / `_effects` / `_modulators` — exact truth for the user's
   install. Offline fallback: parse the `DEFAULT_PATTERNS` / `DEFAULT_EFFECTS` /
   `DEFAULT_MODULATORS` arrays in `LXRegistry.java` (LX repo), plus a source scan of each
   content repo for concrete classes extending `LXPattern`/`LXEffect`/`LXModulator` not
   annotated `@LXComponent.Hidden`.
2. **Locate source.** FQCN → `<root>/<srcDir>/<fqcn path>.java` across configured repos.
   Inner classes map to their outer file. No source found → record `no-source` in the run
   report and write **no entry** (undocumented is the honest state — never fabricate).
3. **Incremental gate.** Compute `sourceSha256` (`shasum -a 256`). If an entry exists
   with a matching hash, **skip** (count as fresh). `--force` regenerates everything.
4. **Hash class bytes.** From the configured `classBytes`: `unzip -p <jar> <fqcn/path>.class | shasum -a 256`
   or hash the file under `target/classes`. Missing artifact → omit `classBytesSha256`
   (runtime will report `stale: "unknown"`).
5. **Summarize — Sonnet subagents** (per the subagent model policy: exploration and
   writing on Sonnet). Batch ~8 classes per agent. Each agent reads the class source
   (plus its direct base class when the subclass is thin) and writes the entry per the
   format doc. Front-load the format doc's full content-rule list into the agent prompt;
   the load-bearing ones: topic sentence + atomic claim bullets (no paragraph
   narration), the claim filter (every bullet aids selection or prevents misuse),
   length budget (Summary ≤ ~100 words, body ≤ ~250), live-vs-latched stated per
   behavior-shaping control, no parameter names/ranges, behavior-vocabulary tags.
   Apply the triage rule before batching: classes whose live parameter descriptions
   are the whole story get skipped (count as `triaged-out` in the run report) or get
   a minimal entry. If the existing entry declares `curated:` sections, carry those
   sections through verbatim and regenerate only the rest (count as `curated-preserved`).
6. **Validate.** `cd package && mvn package` — `CatalogFormatTest` walks every entry
   (frontmatter keys, FQCN=filename, hash shapes, section headings). Fix mechanically
   before review. (Until PR-7b lands the test, validate with a shell pass over the
   required keys.)
7. **Review.** Standard dev-loop review-agent pass over the diff — doc *quality* is
   judged there, not by the format test.
8. **Report.** PR body carries counts: generated / skipped-fresh / triaged-out /
   no-source / hash-missing / cloned-to-scratch. Also record, per repo, which source
   tier resolved it (machine-level file / local override / cloned to scratch) — put any
   `cloned-to-scratch` repos in a top-line warning, not buried in the counts.

## Never

- Hand-edit an *undeclared* catalog entry to "fix" it — fix the generation (or the source)
  and re-run; such edits are silently clobbered by the next run and have no hash integrity.
  The one exception is a **curated section**: an insight generation cannot reach because it
  came from driving the pattern live, not from reading its source. Such an entry declares
  `curated: parameterInteractions` and/or `usageTips`, plus `curatedAt`, in its frontmatter.
  **Step 5 must then read the existing entry and carry those sections through verbatim** —
  this is the only thing standing between curated prose and deletion, since nothing in the
  code enforces it. Never curate the Summary; it must stay derivable from source. See
  `docs/catalog-format.md` for what the marker does and does not guarantee.
- Record parameter names, ranges, defaults, or option lists — the live tools own
  structure; restating it here reintroduces the drift this design exists to kill.
- Write an entry for a class whose source you did not read.
