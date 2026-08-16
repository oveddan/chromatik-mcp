# Developing chromatik-mcp from source

How to build, test, run, and change the plugin. If you only want to *install* it, see
the [README](../README.md) — this page is for contributors.

## Requirements

- **Java 25** and **Maven**. Java 25 is not optional: the published
  `com.heronarts:{lx,glx,glxstudio}:1.2.2` jars this project compiles against are built
  for it, and `<maven.compiler.release>` is pinned to 25 in `package/pom.xml`.
- **Node 20** — only if you touch the generated docs artifacts or the landing page
  (`agent-plugin/`), whose drift gates are Node scripts.
- **Chromatik** with LX 1.2.2, for live testing against a real show. Not needed to build
  or to run the test suite — everything is headless.

```sh
git clone https://github.com/oveddan/chromatik-mcp.git
cd chromatik-mcp
java -version   # expect 25
```

If your default JDK isn't 25, point `JAVA_HOME` at one; `package/scripts/resolve-java.sh`
is what the gate scripts use to find a concrete JDK (the macOS `/usr/bin/java` stub breaks
under the isolated-`user.home` boot that `verify-load.sh` performs).

## Repository layout

| path | what's in it |
|---|---|
| `package/` | the Maven project — the entire shipped jar. `mvn` commands run against `package/pom.xml` |
| `package/src/main/java/chromatikmcp/tools/` | MCP tool handlers: parse args, call a domain primitive, format the result |
| `package/src/main/java/chromatikmcp/domain/` | domain primitives — the only place that knows how a mutation is actually applied |
| `package/src/main/java/chromatikmcp/mcp/` | server lifecycle, HTTP transport, status-file writing |
| `package/src/main/java/chromatikmcp/engine/` | engine-thread marshalling (`EngineExecutor`) |
| `package/src/main/resources/catalog/` | the generated semantic component catalog served by `get_component_doc` |
| `package/src/test/java/` | 65 test classes — domain unit tests + tool-handler integration tests, all headless |
| `package/scripts/` | build/verify gates (see below) |
| `landing/` | the single-page site published to GitHub Pages (setup section generated from the README) |
| `scripts/` | repo-level helpers: doc generators, drift gates, the LX version bump |
| `agent-plugin/` | the Claude Code plugin (driving skill, reviewer agent, project surveyor) |
| `docs/` | this directory — contributor and reference docs |

## Build and install

```sh
package/scripts/build-gate.sh
```

**Use `build-gate.sh`, not raw `mvn package`.** It runs the same build but keeps the full
log on disk and prints a one-line pass/fail summary — the raw log (compiler + the full
surefire suite + shade) is thousands of lines, which floods an agent's context. It also
runs offline first (a warm build is ~10s; without `-o` Maven does network round-trips that
can add minutes), retries once online if an artifact isn't cached yet, and carries a
watchdog for the known macOS CoreMIDI class-lock deadlock that can wedge a test JVM at 0%
CPU forever. Concurrent runs (parallel agent worktrees) serialize on a lock.

To build and drop the jar into Chromatik's package directory:

```sh
cd package && mvn install -Pinstall
```

The `install` profile copies the shaded jar to `~/Chromatik/Packages/` and **skips tests**
— they're the developer gate, not part of an install. Force them with
`-DskipTests=false`. Without the profile, `mvn package` just builds under `target/`.

Two install hazards, both of which have cost real debugging time:

- **Never keep both jars.** The release download is `chromatik-mcp.jar`; the Maven install
  produces `chromatik-mcp-<version>.jar`. Both in `~/Chromatik/Packages/` means Chromatik
  loads the plugin twice, one of them stale. `get_status`'s `buildTime` exposes it.
- **Never reinstall the jar while Chromatik is running.** Quit Chromatik, install, relaunch.

## Test and verify

```sh
package/scripts/build-gate.sh      # compile + full JUnit suite (the main gate)
package/scripts/verify-load.sh     # headless plugin-load gate
```

`verify-load.sh` boots real LX with no UI under an isolated `user.home`, and confirms it
discovers the shaded jar from a `Packages/` dir and runs `ChromatikMcpPlugin.initialize()`.
It exists because the test suite runs with the MCP SDK on the system classpath, which
hides a real deployment failure: the SDK resolves its JSON mapper through the
thread-context classloader, which Chromatik's child `LXClassLoader` is never set as, so a
load test built from the project's own dependency tree passes while the shipped jar
fails. `verify-load.sh` deliberately constructs an LX-only parent classpath so the gate is
deployment-faithful. Run it before claiming a change loads.

Testing conventions — the template and the do→undo→assert pattern every mutation test
follows — are in [qa-strategy.md](qa-strategy.md). Every domain primitive gets a unit test
against a headless `LX`; every tool handler gets an integration test.

One LX-specific trap worth knowing before you write a test: **repeated `new LX()` in one
JVM deadlocks** on the JDK-global javax.sound/CoreMIDI lock. Construct LX once per test
class. If a build hangs at 0% CPU, that's what happened — `build-gate.sh` kills and
retries it rather than hanging forever.

## Drift gates

Several artifacts are generated and will fail CI if regenerated output differs from what's
committed. Run the generator and commit the result as part of your change.

| when you change | run | gated by |
|---|---|---|
| a tool's name, description, schema, or `readOnly` | `package/scripts/dump-tool-catalog.sh` → then `node scripts/generate-tool-reference.mjs` | `ToolCatalogDriftTest` (in `build-gate.sh`) and the `tools.md matches tools.json` CI job |
| any tool name referenced in `agent-plugin/` | `node scripts/check-plugin-tool-names.mjs` | the `agent-plugin tool names match tools.json` CI job |

Only the first has a check inside `build-gate.sh`; the rest are CI-only (the `docs-checks`
job), so run them locally before pushing docs or plugin changes. Both scripts take
`--check` to verify without writing, and neither needs `npm install` — they use only node
builtins.

CI (`.github/workflows/build.yml`) additionally runs
`scripts/verify-heronarts-bytecode.sh` (published Heron Arts bytecode baseline) and
`scripts/test-bump-lx-version.sh` (the LX version-bump helper's own test; `build-gate.sh`
runs this one too).

The `build` job flakes occasionally — re-run it before investigating a failure you can't
reproduce locally.

## The landing page

`landing/` is a single HTML page published to GitHub Pages. The setup instructions on it
are **not** authored there — `scripts/build-landing.mjs` renders them from the section of
the repo README between the `landing:start` / `landing:end` markers, so the page and the
README cannot drift.

```sh
node scripts/build-landing.mjs          # writes landing/dist/ (gitignored)
open landing/dist/index.html            # local preview
```

Edit setup instructions in `README.md`; edit the hero, the links-out section, and the
styling in `landing/template.html` and `landing/public/style.css`. `deploy-docs.yml`
rebuilds and publishes on every push to main that touches the README, `landing/`, or the
builder. `landing/public/og.png` is the committed social-share card.

## Regenerating the component catalog

`get_component_doc` serves entries under `package/src/main/resources/catalog/`, keyed by
source hash. Use the `chromatik-mcp-catalog` skill to add coverage or refresh entries after
LX or content-repo code changes; it skips entries whose source hash is unchanged and
records bytecode hashes so the runtime can report `stale: true` honestly. Format contract:
[catalog-format.md](catalog-format.md).

## Making a change

Read [CLAUDE.md](../CLAUDE.md) first — it carries the rules that aren't obvious from the
code. The load-bearing ones:

- **Work in a git worktree**, never the primary checkout. Multiple sessions run against
  this repo concurrently, so the root checkout is shared state. `git fetch origin main`
  immediately before creating the worktree and branch from `origin/main` — merges land via
  the GitHub API, so a local `origin/main` is routinely stale.
- **Composability is the prime directive.** A tool handler never calls
  `lx.command.perform(...)` or mutates `lx.engine.*` directly. Extract a named domain
  primitive for the intent and call that. The layering:

  ```
  tool handler  ──> domain primitive  ──> LXCommand.perform(...)   (mutation with undo)
  (MCP-shaped)     (intent, narrow)   ──> direct lx.engine.* edit  (mutation without undo)
                                      ──> read lx.engine.*         (read-only)
  ```

- **`lx.command.perform()` swallows failures** — it pushes a UI error, wipes undo/redo, and
  returns normally. Mutation primitives must verify by state-read and throw if the command
  didn't apply.
- **Tool descriptions are part of the product.** They're the only documentation the agent
  driving the server ever reads. Review the prose as hard as the Java.
- **Keep PRs small** — one independently demoable slice, per [build-plan.md](build-plan.md).

Conventions that were decided once and shouldn't be re-litigated per PR:
[tool-conventions.md](tool-conventions.md) (tool surface, canonical-path addressing,
`Result` wire shape, engine-thread rule, pagination) and
[lx-coding-guidelines.md](lx-coding-guidelines.md) (LX idioms distilled from upstream
review). Review criteria: [review-criteria.md](review-criteria.md).

## Driving a live instance while developing

Install the jar, launch Chromatik, enable the plugin, and connect a client — the port is in
`~/.chromatik-mcp/status.json`. `scripts/mcp-client.sh` is a minimal shell client for
poking at the endpoint without an agent in the loop. Full connection setup per client:
[README](../README.md#5-connect-your-ai-client).

Gaps found while live-driving belong in [live-findings.md](live-findings.md), not in a
session transcript.

## Releasing

A release is cut by merging a PR that bumps `<version>` in `package/pom.xml`; CI tags the
commit and publishes the jar. Never push a `v*` tag by hand and never upload a jar
manually. Procedure: [releasing.md](releasing.md).
