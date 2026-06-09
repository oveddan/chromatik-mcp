# LX/Chromatik coding guidelines

Idioms distilled from Mark Slee's (`@mcslee`) code reviews on the
[Apotheneum](https://github.com/Apotheneum/Apotheneum) repo. These are the
recurring notes on AI-written LX code — capture them here so we get the
feedback once, not on every PR.

The meta-point, in his words:

> Simultaneously cool that it can whip this all out, but so many issues in the
> implementation … allocating multiple new `ArrayLists` on every render loop,
> some `synchronized` calls that don't need to be in there. Multiple versions of
> algorithm implementations in static classes, no `interface` to relate them.
> Magic integer constants for the algorithm types.

The goal is to keep the slop contained: working *and* idiomatic, not just
working.

## 1. Don't allocate in the render loop

`run()` / `render()` is called every frame. Allocating there (new `ArrayList`,
new generator, new buffer) churns the GC for no reason.

- Pre-allocate buffers and stateful objects in the constructor or in
  `onActive()`; reuse them each frame.
- If a per-frame value is derived, mutate an existing field rather than
  returning a fresh object.

> Looks like this is allocating a new generator on every frame? — on a `render`
> that called `renderBolt` and built a new generator each call.

## 2. Model behavior with `enum`, not maps / magic constants / parallel classes

When you have a fixed set of variants (algorithms, modes, shapes), use an
`enum` that carries its behavior — not a `HashMap` keyed by strings, not magic
`int` constants, not several unrelated static classes.

Attach the implementation to the enum constant so call sites are just
`algorithm.generator`:

```java
public enum Algorithm {
  MIDPOINT("Midpoint", new MidpointDisplacementAlgorithm()),
  LSYSTEM("L-System", new LSystemAlgorithm());

  private final String displayName;
  private final LightningGenerator generator;

  Algorithm(String displayName, LightningGenerator generator) {
    this.displayName = displayName;
    this.generator = generator;
  }
}
```

If the variants are stateless, make each a singleton instance per constant (as
above) rather than allocating per use.

> an `enum` would really do the trick vs. a `HashMap` with data/state stored in
> Strings.

## 3. Relate implementations with an interface

If you have multiple algorithm/strategy implementations, give them a shared
`interface`. Don't ship "multiple versions of algorithm implementations in
static classes, no `interface` to relate them."

## 4. Don't add thread synchronization

The render pipeline is single-threaded. `synchronized` blocks/methods in pattern
or component code are almost always wrong and just add contention.

> Oof, thread synchronization?! Knock that out of there if you get a moment.

## 5. Use the framework's helpers — don't reinvent them

LX already provides the common operations. Reach for them before writing your
own loop.

- `setColors(color)` fills the whole buffer in one call — don't loop over pixels
  to do it.
- `EnumParameter` derives its UI labels from the enum's `toString()`. Don't
  write manual display-name plumbing.
- Subclass existing components instead of duplicating them. `ImagePattern.Image`
  already handles GIFs — extend it rather than writing a parallel
  `GifFrameExtractor`.

> FYI future reference, `setColors(backgroundColor);` will do that in one line
> for you.

## 6. Check whether it belongs in core LX first

Some functionality is better contributed upstream than maintained as a local
pattern. The "colorize with threshold" PR was closed because the feature went
into core `Colorize` instead. Before building a general-purpose primitive,
check if it already exists — or should exist — in LX itself.

## 7. Remove cruft; respect model bounds

- Delete dead/unneeded code before requesting review (e.g. `CustomByteEncoder`
  classes that "def don't need to be in there").
- Stay within the device geometry — don't draw off the bounds of the model.

## 8. Keep diffs minimal and history clean

- Configure your editor's auto-formatter so it does **not** reformat lines you
  didn't touch. Spurious reformatting noise makes review harder.

  > always a bit annoying when auto-formatters on diff settings. Any chance you
  > can get yours not to format lines you don't touch?

- Squash-and-merge to keep `main` history clean.

---

### How this maps to `lx-mcp`

This repo is an in-process MCP server, not a pattern, so the render-loop rule
(§1) and the single-threaded assumption (§4) apply differently: MCP handlers run
on the HTTP server's thread, not the engine thread, so thread-safety of
mutations is a real concern here rather than something to strip out — the right
way to apply a mutation off the engine thread is an open design question for the
domain primitives (§ "Composability" in CLAUDE.md), not settled. The rest carry
over directly: model variants with enums (§2), share interfaces (§3), use LX's
helpers instead of reinventing (§5), prefer upstream when it fits (§6), and keep
diffs and history clean (§7, §8). See [CLAUDE.md](../CLAUDE.md) for the
composability rules specific to this project.
