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
- `sources.local.json` (this directory, **git-ignored** — copy
  [`sources.local.example.json`](sources.local.example.json)): repo name → local
  checkout path on this machine. If a repo has no local mapping, clone it from `url`
  into a scratch dir or skip it with a note in the run report.

  Extending coverage to a new repo = one entry in each file.

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
   a minimal entry.
6. **Validate.** `cd package && mvn package` — `CatalogFormatTest` walks every entry
   (frontmatter keys, FQCN=filename, hash shapes, section headings). Fix mechanically
   before review. (Until PR-7b lands the test, validate with a shell pass over the
   required keys.)
7. **Review.** Standard dev-loop review-agent pass over the diff — doc *quality* is
   judged there, not by the format test.
8. **Report.** PR body carries counts: generated / skipped-fresh / triaged-out /
   no-source / hash-missing.

## Never

- Hand-edit a catalog entry to "fix" it — fix the generation (or the source) and re-run;
  hand edits are silently clobbered by the next run and have no hash integrity.
- Record parameter names, ranges, defaults, or option lists — the live tools own
  structure; restating it here reintroduces the drift this design exists to kill.
- Write an entry for a class whose source you did not read.
