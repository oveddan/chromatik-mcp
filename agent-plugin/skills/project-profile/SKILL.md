---
name: project-profile
description: The format for a project profile — a derived record of how a specific live Chromatik project actually uses patterns, effects, modulation, color, and external control — and the rules for deriving one from a survey pass. Use when writing or reading a profile produced by /chromatik-learn, when deciding whether a survey finding is durable structure or momentary live state, or when deciding whether an observation in one is strong enough to promote into a shipped catalog entry.
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

**Durable structure only.** A convention is something this project's author decided and
built: it would still be true after the show is torn down, reopened tomorrow, and left
untouched. Where the show happened to be parked when the survey ran is not a convention,
however cleanly it reads — that belongs in `## Momentary state` below, and the two must
not share this section.

Features this project follows, each with its evidence count inline — `Linked color mode
(17 of 18 patterns)`, not just "uses Linked color mode." A one-off is not a convention;
report it with its count of 1 and let the reader judge, or move it to "Open questions" if
it's too thin to call a pattern at all.

No observation is ever dropped for being weak — but "not dropped" means *routed*, not
*written up here*. Every candidate a surveyor reports lands somewhere: here if it's
durable, in `## Momentary state` if it's a live reading, in `## Open questions` if it's
too thin or too contradictory to call either way. Silence — an observation that appears in
no section at all — is the one thing the profile must not produce. That is a rule against
discarding evidence, not a quota this section has to fill: a Conventions section that is
short, or empty, because nothing durable cleared the bar is a correct outcome, so long as
what was seen is visible elsewhere. Strength of belief belongs in Confidence, not in
whether the line appears.

**Never promote a remembered value read off hardware that is not connected.** An
unplugged MIDI surface still reports its last-known control values, and they count
perfectly — eight muted channels out of eight, a clean 8/8. They are evidence of nothing
about this show. Report the device, its connected state, and the fact that its control
values are stale, in `## Open questions`; do not attach a convention count to them here.

Each surveyor is the one that already computed instance counts for its own concern (a
color pass already knows Linked mode is 17-of-18; a modulation pass already knows the
tempo-synced fraction), so each surveyor reports its own candidate conventions with their
counts as part of its concern report. The synthesis step collects them, routes each one to
this section or to `## Momentary state` by the durability test above, and states it — it
does not recompute counts from raw tables.

### 3. `## Momentary state`

Required, not optional. It is allowed to be short, but "nothing volatile was recorded" has
to be a stated finding rather than an absent section.

Where the show was parked when the survey ran. Everything here is a correctly-read value
and a fact about one moment, not about the project. State the reading and its path, and
say what it would take for it to mean something durable.

The rule: **a parameter a performer touches during a set is not evidence of a convention,
however cleanly it reads.** Confidence and durability are orthogonal axes. "6 of the 8
channel faders are at 0" is an exact count off a single `list_channels` payload — maximally
confident by everything `## Confidence` can express, and still nothing but where the show
happened to be parked when the survey ran. Only this section can say so.

Treat these fields as volatile by default, wherever they surface:

| Field | Why it's volatile |
|---|---|
| `fader` | performance level; ridden constantly, and parked anywhere between sets |
| `enabled`, on a channel or an effect | toggled live to bring an element in or out |
| `running` | a modulator can be stopped mid-session and left stopped |
| `compositeLevel` | per-pattern blend amount — a live control, not a composition choice |
| active pattern | changes every few minutes in playlist mode |
| `cue` / `aux` | monitoring state, not composition |
| MIDI template control values | knob and fader positions on a surface |

The table names **controls, not field names.** Apply the test to the thing the reading
describes, never to the string it is keyed by — several of these names recur on components
nobody touches mid-set, and matching on the name alone routes durable configuration out of
Conventions. `list_midi_devices` returns `channelEnabled`, `controlEnabled` and
`syncEnabled` per port: those are persistent routing decisions about how a device is wired
into the engine, and they belong in `## External control` as structure. A view's `enabled`
from `get_views` is likewise part of how the project is built. Neither becomes momentary by
sharing a word with a channel's live toggle.

That list is a floor, not a ceiling — but extend it by the same test, never by how fast a
field can be edited. A control belongs here when someone reaches for it *while the show is
running*, to shape a moment: a level, a live toggle, a monitoring state, a knob on a
surface. Blend mode, crossfade assignment and view assignment are each one gesture away
too, and all three are durable structure, because nobody rides them mid-set.

The converse also matters — don't route a setting here just because it *can* change.
`clockSource` is the case to reason from: nobody rides it during a set, it is a
configuration choice made deliberately between sessions, and its current value is worth
recording plainly in `## External control`. What it does not license is the inference. A
project usually clocked by tap tempo or DAW MIDI clock reads as `OSC` the moment someone
switches it once while checking whether an instrument is alive, so record the reading, not
a claim about how the show is normally driven; if the profile wants to assert the habit,
that needs the author, and until then it belongs in `## Open questions`.

A volatile field can still support a durable claim, but only structurally, and the profile
has to say which reading it is making. "Channel 8's fader is at 0" is momentary. "Channel 8
exists, carries the dedicated Color role, and sits on the Multiply blend" is structural and
survives the fader moving. Split the observation; don't launder one into the other.

The `Base` column of the per-instance tables in `## Practice` is a momentary reading by
this same standard — a base value is whatever the parameter was last left at. What is
durable in those tables is the wiring: which parameters are modulated, by which class, at
what depth. Read them that way, and never restate a base value as a convention.

### 4. `## Practice`

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

**Budget and degradation.** A real project carries on the order of a hundred pattern and
effect instances, and the full table for every one of them will not always fit in the
surveyor's context. When it doesn't, degrade in this order and no other:

1. **Every instance with at least one modulated parameter keeps its full per-parameter
   table**, unmodulated rows included. These are what the format exists for; they are the
   last thing cut.
2. **Instances with no modulated parameter collapse to one roster line each** — path,
   FQCN, parameter count: `/lx/mixer/channel/3/pattern/2 — ...PlanesPattern — 14
   parameters, none modulated`. The animated-versus-static ratio survives this; the
   per-parameter rows don't.
3. **If even the roster won't fit, stop emitting and name what's left in `## Not
   surveyed`** — the count, plus every remaining instance by path and FQCN. A path list is
   cheap; losing track of which instances exist is not.

Never sample. "A representative subset of the `PlanesPattern` instances" is not a finding
this format can aggregate, and a reader cannot tell it apart from a complete census. Never
truncate a table mid-instance either — drop the whole instance to the next rung and record
it there.

Whichever rung the survey reached, say so in `## Not surveyed`, with the counts at each:
how many instances got full tables, how many collapsed to roster lines, how many were named
only. A reader must always be able to tell a project that has little modulation from a
survey that ran out of room — those two produce nearly identical documents otherwise, and
that ambiguity is precisely what `## Not surveyed` exists to prevent.

Class-level rollups in this section state which rung they were computed over. A
tempo-synced fraction derived from tier-1 instances alone is a fraction of *modulated*
instances, not of all instances, and has to say so.

### 5. `## External control`

MIDI mappings (`list_midi_mappings`), MIDI devices/surfaces
(`list_midi_devices`/`list_midi_surfaces`), and instantiated MIDI templates
(`list_midi_templates`). For each template, use `list_parameters` on its returned path to
record the named hardware controls it exposes. Also report OSC state and clock source
(`get_tempo`, `get_project_info`) — record the clock source as the setting it is, without
inferring from one reading how the show is normally clocked (see `## Momentary state`).
The *control values* on a MIDI template are a different matter: those are knob positions,
and they belong in `## Momentary state`, not here. Always include a subsection noting that
DAW-side mapping (e.g. what a Bitwig/Ableton project sends) is not parsed by this pipeline
— that's a known, permanent gap for this skill, not a bug to route around.

### 6. `## Confidence`

Required, not optional. Per claim or claim group, say how strongly the evidence supports
it, and say why: an instance count, a fraction, or a named limitation (e.g. "device-local
modulation engines were only sampled on channels 1-4"). Three instances and eighteen
instances are not the same claim strength even when they point the same direction; this
section is where that difference has to live, because the Conventions/Practice sections
report counts, not confidence.

This section measures *how much evidence* backs a claim, and nothing else. It cannot say a
claim will stop being true the next time someone touches a fader — that is durability, a
separate axis, and it lives in `## Momentary state`. A maximally confident claim can be
entirely momentary. Don't hedge a volatile reading down to "medium confidence" here as a
substitute for routing it correctly: that misreports the evidence *and* leaves the reading
sitting in Conventions.

### 7. `## Open questions`

Required, not optional. Weak signal stays visible here rather than being dropped. A
one-off convention candidate, a contradiction between two tools' payloads, a modulator
whose target couldn't be resolved — all of it goes here rather than in the trash. This
section is genuinely allowed to be short (or, on a very well-covered survey, empty) but
it must be considered, not skipped as boilerplate.

### 8. `## Not surveyed`

What was skipped and why: concerns that didn't run, how deep pattern-rack/effect-chain
recursion actually got before stopping, which degradation rung `## Practice` reached and
the instance counts at each, and any question no tool could answer — name the tool that
would need to change. This section is the honest edge of the survey; a reader should be
able to tell exactly where the profile's authority ends.

Two gaps are permanent, and belong here in every profile whether or not a surveyor thought
to raise them. State both by construction rather than leaving each reader to notice an
absence and each surveyor to rediscover it:

- **The physical layer.** None of the six concerns call `describe_model`, `list_fixtures`,
  or `get_output_map`. A profile records how a project is composed and driven, not what it
  is wired to — fixture inventory, model geometry, and output/packet mapping are outside it
  by design, not by oversight. Which pixels a channel actually lights cannot be answered
  from this document.
- **DAW-side mapping**, as `## External control` also records: what a Bitwig or Ableton
  project intends to send is not parsed by this pipeline. The profile can report what the
  engine receives, never what a DAW means by it.

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
