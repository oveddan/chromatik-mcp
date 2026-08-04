---
name: project-profile
description: The format for a project profile — a derived record of how a specific live Chromatik project actually uses patterns, effects, modulation, color, and external control — and the rules for deriving one from a survey pass. Use when writing or reading a profile produced by /chromatik-learn, or when deciding whether an observation in one is strong enough to promote into a shipped catalog entry.
---

## What a profile is

A project profile is a derived record of one project's habits: which conventions it
follows, which classes it uses and how it drives their parameters, what runs its clock,
and what nobody could confirm. It is built by surveying a *running* Chromatik instance
over MCP tools — never by reading the `.lxp` file or LX source — and it describes exactly
the project that was open when the survey ran.

A profile is not documentation of LX. It is not a technique catalog entry. It is a
snapshot of one show, with its own names, on one machine.

## Where it lives, and why

`~/.chromatik-mcp/profiles/<slug>.md`, where `<slug>` is the survey's project path's
**basename**, slugified: lowercase, `.lxp` stripped, every run of non-alphanumeric
characters collapsed to a single hyphen, leading/trailing hyphens trimmed. `Treetop
Transmission - Basel25.lxp` becomes `treetop-transmission-basel25.md`. Basename, not the
full path — a full-path slug would embed the user's home directory into the filename of
the file this pipeline calls its privacy mechanism.

That directory sits next to `status.json` and `config.json` — the plugin's existing
machine-local home, outside this repository. Nothing in this repo reads, packages, or
uploads `~/.chromatik-mcp/profiles/` — the catalog overlay is pinned to its own
`catalog/` subdirectory and does not scan the parent. That is a real guarantee about this
codebase, but it is not a guarantee about the user's filesystem: if `$HOME` is itself a
git repository (a common dotfiles setup), a `git add -A` there would stage a profile.
`/chromatik-learn` mitigates this by construction: the first time it creates the
`profiles/` directory, it writes a `.gitignore` containing a single `*` into it, so a
profile is excluded even if a surrounding repo would otherwise pick it up. Nothing in the
profile format itself is redacted to compensate for the remaining risk — see "Names are
allowed" below.

**If a profile already exists at that path, archive it first.** Rename it to
`<slug>-<survey-date>.md`, where `<survey-date>` is the *archived* profile's own recorded
survey date from its `## Project` section (not today's date) — a profile superseded
months after it was written should be filed under when it was surveyed, not when it was
overwritten. If a file already exists at that archive path (e.g. two re-surveys landing
on the same source date), append `-2`, `-3`, etc. rather than clobbering it. Never
overwrite a profile — original or archived — silently; an author may want to diff what
changed between two surveys of the same project.

## Names are allowed

Channel names, view names, swatch labels, device labels — all of it belongs in the
profile. The profile is local and describes whichever project is open; stripping names
would only make it less useful without making it more private. The privacy guarantee is
the file's location, not its content.

The rule that *does* strip things is **promotion** (below) — a separate, later step that
is out of scope for this skill's output but that this skill's data feeds.

## The six concerns

A survey runs one read-only agent per concern, in parallel. These are the only concerns;
don't invent others and don't merge two into one dispatch:

1. **structure** — mixer topology: channels, groups, crossfade assignment, blend vs.
   playlist mode, view assignment per channel and per device, pattern-rack nesting depth.
2. **color** — palette contents and swatch count, per-pattern color mode (Fixed / Linked
   / Palette) for every palette-capable class, how color is scoped across structures.
3. **effects** — full effect census by class and by depth (master / channel / leaf
   pattern), masking approach and how it's achieved. Census only — parameter-level detail
   for effect instances is `pattern-modulation`'s remit, below.
4. **pattern-modulation** — the per-instance modulation table (below) for every pattern
   *and effect* instance, modulated or not. This is the only concern that emits
   per-instance target tables — `global-modulation` does not duplicate them.
5. **global-modulation** — every modulator by class and engine, the wiring graph, what
   drives what, depth distribution, tempo-sync usage. Reports which instances a modulator
   targets by path (for `pattern-modulation` to cross-reference), but does not itself
   produce per-instance tables — that would duplicate concern 4 and risk the two passes
   disagreeing.
6. **external-control** — MIDI devices/surfaces/templates/mappings, OSC receive/transmit
   state, clock source, what actually drives the show live.

## Profile sections, in this order

### 1. `## Project`

Name, project path, LX version, server version, when surveyed (date), and which of the
six concerns actually ran (a partial survey is still worth writing down — see "Not
surveyed").

### 2. `## Conventions`

Features this project follows, each with its evidence count inline — `Linked color mode
(17 of 18 patterns)`, not just "uses Linked color mode." A one-off is not a convention;
report it with its count of 1 and let the reader judge, or move it to "Open questions" if
it's too thin to call a pattern at all. There is no cutoff below which a feature is
omitted — silence is the one thing this section must not produce. Strength of belief
belongs in Confidence, not in whether the line appears.

Each surveyor is the one that already computed instance counts for its own concern (a
color pass already knows Linked mode is 17-of-18; a modulation pass already knows the
tempo-synced fraction), so each surveyor reports its own candidate conventions with their
counts as part of its concern report. The synthesis step collects and states them here —
it does not recompute counts from raw tables.

### 3. `## Practice`

One subsection per fully-qualified class name (never the display name — a profile keys
on the class, and display names are per-project). For each: instance count, which
parameters are actually driven and by which modulator class, the tempo-synced fraction of
its driving modulators (of those with a tempo-sync parameter at all — see `Tempo` below),
and the animated-vs-static fraction of its parameters.

Back this with the per-instance table below; the class-level rollup is a summary of it,
not a replacement for it.

**Per-instance modulation table** — one heading and table per pattern or effect instance:

```
### Nebula — heronarts.lx.pattern.form.PlanesPattern
path: /lx/mixer/channel/3/pattern/2

| Parameter | Base   | Modulated by (class) | Source path | Depth | Polarity | Tempo |
|---|---|---|---|---|---|---|
| yaw   | 180.0  | CycleModulator | .../modulation/modulator/1 | 0.31  | BIPOLAR  | n/a  |
| pitch | 0.0    | —              | —                          | —     | —        | —    |
```

Rules that keep this aggregable, non-negotiable:

- **One row per parameter, including unmodulated ones.** The animated-versus-static
  ratio only exists if the zeros are recorded.
- The heading names the instance's display label *and* its FQCN; the table is keyed by
  the FQCN, not the label.
- `Depth` is the modulation range as reported by `list_modulations` (`detail: full`), not
  a description of it.
- `Tempo` is `synced`, `free`, or `n/a`, read from the driving modulator's own tempo-sync
  parameter — not guessed from the project's clock source. Some modulator classes have no
  tempo-sync parameter at all (they extend `LXModulator` directly, not
  `LXPeriodicModulator`, where `tempoSync` lives — `CycleModulator` is one such class).
  For those, write `n/a`, not `free` — `free` asserts a class *can* run untempo'd and
  simply isn't right now, which is a fabricated observation for a class that has no sync
  parameter to read. The class-level tempo-synced fraction in `## Practice` excludes
  `n/a` rows from its denominator; it is a fraction of syncable instances, not of all
  instances.

### 4. `## External control`

MIDI mappings (`list_midi_mappings`), MIDI devices/surfaces
(`list_midi_devices`/`list_midi_surfaces`), and instantiated MIDI templates
(`list_midi_templates`). For each template, use `list_parameters` on its returned path to
record the named hardware controls it exposes. Also report OSC state and clock source
(`get_tempo`, `get_project_info`). Always include a subsection noting explicitly that
DAW-side mapping (e.g. what a Bitwig/Ableton project sends) is not parsed by this pipeline
— that's a known, permanent gap for this skill, not a bug to route around.

### 5. `## Confidence`

Required, not optional. Per claim or claim group, say how strongly the evidence supports
it, and say why: an instance count, a fraction, or a named limitation (e.g. "device-local
modulation engines were only sampled on channels 1-4"). Three instances and eighteen
instances are not the same claim strength even when they point the same direction; this
section is where that difference has to live, because the Conventions/Practice sections
report counts, not confidence.

### 6. `## Open questions`

Required, not optional. Weak signal stays visible here rather than being dropped. A
one-off convention candidate, a contradiction between two tools' payloads, a modulator
whose target couldn't be resolved — all of it goes here rather than in the trash. This
section is genuinely allowed to be short (or, on a very well-covered survey, empty) but
it must be considered, not skipped as boilerplate.

### 7. `## Not surveyed`

What was skipped and why: concerns that didn't run, how deep pattern-rack/effect-chain
recursion actually got before stopping, and any question no tool could answer — name the
tool that would need to change. This section is the honest edge of the survey; a reader
should be able to tell exactly where the profile's authority ends.

## Promotion — not this skill's output, but its consumer

An observation in a profile may graduate into a shipped catalog entry only if it survives
being restated as a property of the *class*, confirmed against that class's own source —
not against this project's use of it. "This project drives `yaw` with a free-running
`CycleModulator` at depth 0.31" is a profile observation. "`PlanesPattern.yaw` runs
0..360 degrees and reads naturally as a rotation offset" is a catalog-worthy claim only
once confirmed from `PlanesPattern`'s own source, independent of any one project's
habits.

Practically: profile data is a search heuristic for where to look, not a citation. A
profile entry that cannot be restated and independently confirmed this way stays in the
profile, indefinitely. Promotion itself is out of scope for this skill and for
`/chromatik-learn` — this section exists so a later reviewer knows the profile is safe to
mine for candidates without treating anything in it as pre-verified.
