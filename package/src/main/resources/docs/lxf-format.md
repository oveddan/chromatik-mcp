# The `.lxf` fixture file format

A `.lxf` file is a JSON document that describes one LX fixture: its **geometry**
(the points that make up the fixture, and their real-world position), its
**hierarchy** (a fixture is often assembled from smaller fixtures — nested
`components`), and its **wiring** (which network `outputs` carry pixel data to
which physical controller). LX parses it with `heronarts.lx.structure.JsonFixture`.

Fixture files live under the project's `Fixtures/` media folder
(`LX.Media.FIXTURES`, typically `~/Chromatik/Fixtures/`). A fixture's `type`
string (used both as the top-level file name and as a `components[].type`
reference to another file — see below) is its path *relative to that folder,
with the `.lxf` extension stripped and `/` as the path separator* — e.g. type
`"heart/R1"` resolves to `Fixtures/heart/R1.lxf`.

The parser is plain Gson (`new Gson().fromJson(...)`). Line (`//`) and block
(`/* ... */`) comments are permitted — Apotheneum's `.lxf` files open with a
`/* ... */` license header and parse cleanly — but trailing commas are NOT;
a trailing comma throws a parse error.

## Top-level keys

| Key | Purpose |
| --- | --- |
| `label` | Display name for the fixture. |
| `tag` / `tags` | One tag, or an array of tags (see [Tags](#tags)). `modelKey` / `modelKeys` are deprecated synonyms, still parsed with a warning. |
| `parameters` | Named, typed variables this fixture (and its children) can reference as `$name` (see [Parameters and expressions](#parameters-and-expressions)). |
| `components` | Array of child fixtures — geometry primitives, native Java classes, or references to other `.lxf` files (see [Component types](#component-types)). |
| `output` / `outputs` | A single output object, or an array of them (see [Outputs and segments](#outputs-and-segments)). |
| `meta` | Arbitrary string metadata (`$expr`-expanded), stored on the fixture but not interpreted by LX itself. |
| `transforms` | An ordered array of transform objects (`rotateX/Y/Z`, `scaleX/Y/Z`, translation) applied to the fixture's geometry — these move real points, not just the preview (see [Component types](#component-types)). |
| `ui` / `mesh` / `meshes` | UI-only visualization hints (mesh rendering) — not needed to get a fixture pixel-addressable, so not detailed further here. |

Legacy top-level arrays `points`, `strips`, `arcs`, `children` are still parsed
but emit a deprecation warning — prefer `components` with an explicit `type`
per entry. `points`/`strips`/`arcs` items are each implicitly typed as their
section name; `children` items are not — each still needs its own explicit
`type` key, exactly like `components[]`.

## Component types

Every entry in `components[]` is a JSON object with a `type` key that selects
one of the forms below, plus shared keys every component accepts:

- **Geometry / placement**: `x`, `y`, `z`, `yaw`, `pitch`, `roll`, `scale`,
  `pointSize`. (`rotateX`/`Y`/`Z` and `scaleX`/`Y`/`Z` are NOT read here —
  see the `transforms` array below. `direction`/`normal`/`end` are specific
  to `"strip"`/`"arc"` components — see those sections.)
- **Common**: `id` (lets `outputs[].segments[].componentId` address it),
  `instances` (repeat this component `N` times — see
  [`$instance`/`$instances`](#instance-and-instances-and-instances-expansion)),
  `brightness` (0-1), `enabled` (a boolean `$expr`; `false` skips the
  component entirely).
- **`transforms`**: an array of transform objects applied in order, each a
  translation (`x`/`y`/`z`), a single rotation (`yaw`/`pitch`/`roll` or
  `rotateX`/`rotateY`/`rotateZ` — only one rotation key per entry), or a
  scale (`scale`, or `scaleX`/`scaleY`/`scaleZ`, or a `scale` object with
  `x`/`y`/`z`). This is the only place `rotateX`/`rotateY`/`rotateZ` and
  `scaleX`/`scaleY`/`scaleZ` are read — a bare `"rotateX": 45` on the
  component itself is ignored; use `"transforms": [{"rotateX": 45}]`.

### `"point"`

A single point. No extra keys beyond the shared ones above.

### `"points"`

An explicit list of points via `coords`, an array of `{"x":.., "y":.., "z":..}`
objects. This form is uncommon in practice (seen mostly in retired fixtures) —
prefer `"strip"` for a regular row of points.

### `"strip"`

A straight line of points: `numPoints`, `spacing` (distance between points),
plus the shared `direction`/`end` geometry keys.

### `"arc"`

Points along a circular arc: `numPoints`, `radius`, `degrees`, `mode`
(`"origin"` or `"center"` — origin-of-rotation), `normal`, `direction`.

### `"class"`

Instantiates a native Java fixture class: `class` (fully-qualified class name)
plus a `parameters` object of constructor-style values for that class (not the
same `parameters` block as the top-level declaration — this one supplies
values *to* a native class's own parameters).

### Any other string — a file reference

If `type` isn't one of the five keywords above, it's treated as the `type` of
another `.lxf` file: LX resolves it first relative to the referencing file's
own folder, then as an absolute path under `Fixtures/`. This is how composite
installations are assembled from smaller, reusable fixture files — see the
[flat name-reference example](#example-2-flat-name-reference-composite) below.

## Parameters and expressions

The top-level `parameters` object declares named variables:

```json
"parameters": {
  "nodeSpacing": { "type": "float", "min": 1, "default": 9.375,
                   "label": "Spacing", "description": "Spacing of nodes" }
}
```

`type` is one of `string`, `int`, `float`, `boolean` (a `string` parameter with
an `options` array becomes a select). `label`, `description`, `min`, `max`,
`options` are all optional beyond `type`/`default`. Parameter names must match
`^[a-zA-Z0-9]+$` — no underscores, hyphens, or other punctuation; a name like
`node_spacing` is invalid and silently dropped with a warning.

Any string-valued field elsewhere in the file (geometry, output `host`,
`enabled`, `meta`, tags, or a child component's own parameter overrides) may
reference a declared parameter as `$name` or `${name}`, and combine references
with arithmetic/boolean operators evaluated by `heronarts.lx.structure.Expression`
(`+ - * / % ^`, comparisons, `&&`/`&`, `||`/`|`, `!`, parentheses, ternary
`?:`):

```json
"x": "$instance * $nodeSpacing",
"enabled": "$cub01On & !$cub01Flip"
```

### `$instance`/`$instances` and `instances`-expansion

A component with `"instances": N` is expanded into `N` separate components,
each with the implicit variables `$instance` (0-based index, `0..N-1`) and
`$instances` (the declared `N`) available for its own expressions — this is
how the strip in [Example 1](#example-1-parametric-strip-leaf) fans out into
50 identical strips at increasing `x` offsets. `$instance`/`$instances` are
reserved names — declaring a top-level parameter with either name is
rejected.

## Outputs and segments

`output` (single) or `outputs` (array) each describe one network destination.
Common keys: `enabled` (boolean `$expr`, defaults on), `fps` (rate limit),
`protocol`, `transport` (OPC only: UDP or TCP), `host` (`$expr`-capable),
`port`, `priority` (sACN only, 0-200), `sequenceEnabled`, `byteOrder`,
`kinetVersion` (KiNET only: `PORTOUT` or `DMXOUT`).

Per-protocol addressing key cheat-sheet (universe-like key, then
channel/offset-like key):

| `protocol` | universe-like key | channel/offset-like key |
| --- | --- | --- |
| `artnet` / `artdmx` | `universe` | `channel` |
| `artsync` | — | — |
| `sacn` / `e131` | `universe` | `channel` |
| `ddp` | — | `dataOffset` |
| `opc` | `channel` | `offset` |
| `kinet` | `kinetPort` | `channel` |

`artsync` sends an Art-Net sync datagram rather than pixel data, so it needs
neither key.

If an output has no `segments`, its own top-level `start`/`num`/`stride`/etc.
keys describe a single implicit segment. If it has `segments`, an array of
segment objects each select and map a slice of the fixture's points onto that
output: `start`, `num`, `stride`, `outputStride`, `repeat`, `duplicate`,
`padPre`, `padPost`, `reverse` (serpentine direction per segment), `byteOrder`,
`headerBytes`, `footerBytes`, and `componentIndex`/`componentId` (target a
specific child component of a JsonFixture, by position or by its declared
`id`).

## Tags

Give one tag as a single `tag` string, or multiple tags as an array under
`tags`. Each tag value must itself match `[A-Za-z0-9_.\-/]+` in its entirety —
a `tag` string is validated and stored whole, it is not split on spaces or
commas (e.g. `"tag": "cube face"` fails validation and is dropped, rather
than becoming two tags `cube` and `face`); use `"tags": ["cube", "face"]`
for multiple tags. Anything that doesn't match the regex is dropped with a
warning at load time, not a hard error. Tags drive view
selectors (`get_views`/`add_view` match fixtures by tag) and are how
`list_fixtures`/`get_fixture` group and filter fixtures for a client.

## Worked examples

### Example 1: parametric strip leaf

Trimmed from `Apotheneum-CubeFace.lxf` — one `strip` component repeated 50
times, each instance offset along `x` by its index times a declared spacing
parameter:

```json
{
  "label": "Apotheneum-CubeFace",
  "tag": "cubeFace",
  "parameters": {
    "nodeSpacing": { "type": "float", "min": 1, "default": 9.375 }
  },
  "components": [
    {
      "type": "strip",
      "instances": 50,
      "numPoints": 45,
      "x": "$instance * $nodeSpacing",
      "spacing": "$nodeSpacing"
    }
  ]
}
```

### Example 2: flat name-reference composite

A common pattern for large installations (Robot Heart's `heart.lxf` is built
this way): a top-level file whose `components` are nothing but references to
other named `.lxf` files, each contributing its own geometry and output. A
referenced leaf typically declares its own `kinet` output:

```json
{
  "label": "Heart",
  "components": [
    { "type": "heart/R1" },
    { "type": "heart/R2" },
    { "type": "heart/L1" }
  ]
}
```

where `heart/R1.lxf` might be:

```json
{
  "label": "Heart-R1",
  "components": [
    { "type": "strip", "numPoints": 144, "spacing": 1.0 }
  ],
  "output": { "protocol": "kinet", "kinetPort": 1, "host": "10.0.0.5" }
}
```

### Example 3: `$expr` per-controller outputs with serpentine segments

Trimmed from `Apotheneum.lxf` — the output for one physical Art-Net
controller (`cub01`), enabled by a combination of boolean parameters, with a
serpentine run of ten 45-point segments alternating direction:

```json
"outputs": [
  {
    "enabled": "$cub01On & !$cub01Flip & !$cub01HackExt",
    "protocol": "artnet",
    "host": "$cub01",
    "universe": 0,
    "segments": [
      { "start": 0,  "num": 45 },
      { "start": 45, "num": 45, "reverse": true },
      { "start": 90, "num": 45 }
      // … 7 more segments, alternating "reverse" …
    ]
  }
]
```

The real file repeats this pattern once per physical controller (`cub01` through
`cub20` in Apotheneum), each with its own `enabled` expression and `host`
parameter.

## Using this with chromatik-mcp

`add_fixture` instantiates a fixture by registered `class` (a built-in fixture
type) or by `.lxf` `type` string — `list_available_fixtures` lists both
(`classes` and `jsonTypes`) and reports `fixturesDirectory`, the absolute path
where you write a new `.lxf` file with your own file tools. `index` (insert
position) is supported with `class` only; `type` always appends, so reposition
a newly-added `.lxf`-based fixture afterwards with `move_fixture` if needed.

To instantiate a brand-new fixture *type* from scratch, write its `.lxf` file
into `fixturesDirectory` yourself, then call `list_available_fixtures` (or
`reload_fixtures`) to pick it up before adding it. The supported edit loop for
an *existing* fixture file is:

1. Edit the `.lxf` file on disk (with whatever file-editing tool your client
   has) — add/change `components`, `parameters`, or `outputs`.
2. Call `reload_fixtures`, which re-scans the fixture type list and reloads
   every already-instantiated `JsonFixture` from disk, picking up your edits
   without restarting Chromatik.

Use `get_fixture` and `describe_model` to inspect an existing fixture's live
structure (its resolved points, tags, and parameter values) before or after
an edit — they read the in-memory model that `reload_fixtures` just
refreshed, not the raw JSON.
