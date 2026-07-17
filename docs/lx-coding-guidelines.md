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

## 9. Plugin lifecycle: let `initialize()` throw, free in `dispose()`

(Verified against LX source rather than a review note — `LXRegistry.Plugin`
calls `initialize()`/`dispose()` and the `Apotheneum` launcher plugin is the
reference implementation.)

LX already wraps `LXPlugin.initialize(lx)` and `dispose()`:

```java
// LXRegistry.Plugin
try {
  this.instance.initialize(lx);
} catch (Throwable x) {
  LX.error(x, "Unhandled error in plugin initialize: " + clazz.getName());
  lx.pushError(x, "Error on initialization of plugin " + ...); // user-facing dialog
  setException(x);                                             // marks plugin hasError
}
```

So:

- **Don't swallow exceptions in `initialize()`.** Let them propagate — LX logs
  them, surfaces a user-facing error via `pushError`, and flags the plugin as
  errored in the UI. A local `try/catch` that only calls `LX.error` *downgrades*
  the failure: the plugin still shows as healthy while it's actually broken. If a
  checked exception is in the way (the interface declares no `throws`), wrap it
  unchecked (`UncheckedIOException`, `IllegalStateException`) so it still
  propagates — don't catch-and-log it away.
- **Implement `dispose()` symmetrically.** Whatever `initialize()` acquires —
  listeners, threads, servers, sockets — release it in `dispose()`. Apotheneum's
  launcher does `addListener` in `initialize()` and `removeListener` in
  `dispose()`. A plugin that starts a server and never stops it leaks the bound
  port and its threads when the plugin is disabled.
- **Catch only where LX won't.** Async callbacks, background threads, and OSC
  handlers run outside LX's wrapper, so *those* should `try/catch` and report via
  `LX.error` / `pushError` (again, see the Apotheneum launcher). The rule is:
  propagate where the framework already catches; catch where it doesn't.
- **House style:** a `PREFIX` constant plus `log()`/`error()` helpers wrapping
  `LX.log`/`LX.error`, not inline `"[Name] "` literals at every call site.

---

## Conventions from the LX source

The idioms above come from review feedback. The conventions below come from
reading the framework itself (`/Users/danoved/Source/LX/`) — they're how LX
handles errors, threading, and lifecycle internally, so plugin code should
match them. File references are to the LX source tree.

### 9. Logging: use `LX.log` / `LX.warning` / `LX.error`

Don't use `System.out` / `printStackTrace` or a third-party logger. LX has a
static logging API that prefixes every line with `[LX yyyy/MM/dd HH:mm:ss]` and
can be redirected to a log file (`LX.java:1544-1583`):

```java
LX.log(String message)                  // stdout
LX.warning(String message)              // stdout, guarded by LX.LOG_WARNINGS
LX.debug(String message)                // stdout, guarded by LX.LOG_DEBUG
LX.error(String message)                // stderr
LX.error(Throwable x)                   // stderr + stack trace
LX.error(Throwable x, String message)   // stderr + context + stack trace
```

Use `LX.error(throwable, context)` — pass the throwable so the stack trace is
preserved; don't flatten it to a string.

### 10. Catch-and-log at boundaries; don't let one failure kill the engine

LX's house style is to catch at the seam of an operation, log it for
developers, surface a user-facing message, and keep running rather than
propagate. `LXCommandEngine.perform` is the canonical example
(`command/LXCommandEngine.java:58-89`):

```java
public LXCommandEngine perform(LXCommand command) {
  try {
    command.perform(this.lx);
    // ... push onto undo stack
  } catch (InvalidCommandException icx) {
    this.lx.pushError(icx, "Unexpected error performing action " + command.getName() + ...);
    LX.error(icx, "Unexpected error performing action " + command + " - bad internal state?");
    clear();
  } catch (Exception x) {
    this.lx.pushError(x, "...");
    LX.error(x, "...");
    clear();
  }
  return this;
}
```

Two distinct channels: `LX.error(...)` for developer diagnostics,
`lx.pushError(throwable, message)` for the user-facing surface
(`LX.java:547-559`). This maps cleanly onto our `Result<T>` boundary: catch at
the tool/primitive seam, `LX.error(...)` for the log, and return
`Result.error(...)` to the MCP client instead of `pushError` (no UI to push
to). Never throw across the MCP handler boundary.

### 11. Mutate engine state on the engine thread via `engine.addTask`

The engine runs on its own thread. Code on any other thread must not mutate LX
state directly — instead enqueue a `Runnable` that LX drains at the top of the
next engine loop (`LXEngine.java:846`, processed at `:1087-1097`):

```java
lx.engine.addTask(() -> {
  // runs on the engine thread; safe to mutate LX state here
});
```

`addTask` is the thread-safe enqueue (it appends to a synchronized queue and
sets an `AtomicBoolean`); the engine swaps the queue under lock and runs the
tasks itself. **This is the answer to the threading question for `chromatik-mcp`**: MCP
handlers run on the HTTP server's thread, so every domain primitive that mutates
LX state must marshal its work onto the engine thread with `engine.addTask`,
then hand the result back to the handler thread (e.g. via a `CompletableFuture`
completed inside the task). Don't add `synchronized` to the primitives (§4) and
don't touch `lx.engine.*` directly from a handler thread.

### 12. Parameters: `public final` fields, register in the constructor

Declare parameters as `public final` fields with fluent configuration, and
register them via `addParameter(path, parameter)` in the constructor
(`LXComponent.java:1212-1237`). Registration auto-wires listeners and OSC; you
don't manage that plumbing yourself:

```java
public final BoundedParameter speed =
  new BoundedParameter("Speed", 1, 0, 10)
    .setDescription("Playback speed");
// in constructor:
addParameter("speed", this.speed);
```

Bounded parameters **clamp** out-of-range values silently rather than throwing;
they throw `IllegalArgumentException` only for construction-time config errors
(e.g. inverted bounds) — `parameter/BoundedParameter.java:241-256`.

### 13. Serialization: `LXSerializable`, `KEY_*` constants, `Utils` helpers

Persisted state implements `LXSerializable` (`save(LX, JsonObject)` /
`load(LX, JsonObject)`), names every JSON key with a `KEY_*` constant, uses the
`LXSerializable.Utils` helpers for parameter (de)serialization, and **guards
every read with `obj.has(key)`** (`LXComponent.java:1466-1510`,
`LXSerializable.java`):

```java
public final static String KEY_PARAMETERS = "parameters";
// save:
obj.add(KEY_PARAMETERS, LXSerializable.Utils.saveParameters(this.parameters));
// load:
if (obj.has(KEY_PARAMETERS)) { LXSerializable.Utils.loadParameters(lx, obj, this.parameters); }
```

### 14. Lifecycle: symmetric register/unregister, `dispose()` calls `super`

Register listeners and resources in the constructor / `onActive()`; tear them
down in the matching `onInactive()` / `dispose()`. `dispose()` unregisters
everything it added and **must call `super.dispose()`** — LX asserts this with
`LXComponent.assertDisposed` (`LXComponent.java:1082-1132`, `LX.java:728-731`).
Disposing twice throws. This is the framework-level statement of the CLAUDE.md
rule "register/unregister listeners symmetrically."

### 15. Validate at boundaries with `Objects.requireNonNull`

Public entry points null-check their arguments with a message rather than
letting a later NPE surface far from the cause (`LX.java:691`); network/OSC
handlers bounds-check indices and log via `LXOscEngine.error(...)` rather than
throwing (`LXComponent.java:732-770`). Do the same at the MCP boundary: validate
args up front and return `Result.error(...)` with a clear message.

### Custom exception types

When LX does throw, it uses small typed exceptions, not bare `RuntimeException`:
`LX.InstantiationException` (with a `Type` enum: `EXCEPTION` / `LICENSE` /
`PLUGIN`, `LX.java:75-97`) and `LXCommand.InvalidCommandException`
(`command/LXCommand.java:99-111`). Follow suit if we need a checked failure
mode that callers should distinguish.

---

### How this maps to `chromatik-mcp`

This repo is an in-process MCP server, not a pattern, so the render-loop rule
(§1) and the single-threaded assumption (§4) apply differently: MCP handlers run
on the HTTP server's thread, not the engine thread, so thread-safety of
mutations is a real concern here rather than something to strip out. The
framework already dictates the answer (§11): marshal every state mutation onto
the engine thread with `lx.engine.addTask(...)` from inside the domain
primitives — don't add `synchronized` (§4) and don't touch `lx.engine.*` from a
handler thread. Errors at the MCP seam follow §10/§15: catch, `LX.error(...)`,
and return `Result.error(...)` instead of throwing. The rest carry over
directly: model variants with enums (§2), share interfaces (§3), use LX's
helpers instead of reinventing (§5), prefer upstream when it fits (§6), and keep
diffs and history clean (§7, §8). The plugin lifecycle rule (§9) applies most
directly of all — `ChromatikMcpPlugin` *is* an `LXPlugin`, so its `initialize()` must
propagate and its `dispose()` must stop the embedded server. See
[CLAUDE.md](../CLAUDE.md) for the composability rules specific to this project.
