# chromatik-mcp — Java package

The drop-in LX/Chromatik jar.

See [../docs/build-plan.md](../docs/build-plan.md) for the roadmap and [../CLAUDE.md](../CLAUDE.md) for contributor conventions.

**Status**: `ChromatikMcpPlugin` starts an embedded streamable-HTTP MCP server (official
Java MCP SDK on embedded Tomcat) from `initialize()` and exposes the generated domain-tool
surface spanning project/model reads and saves, channels, patterns/effects, parameters,
modulators, MIDI mapping and templates, snapshots, views/fixtures, render previews,
arrange-timeline authoring, batched operations, and a component-doc catalog. The authoritative
current inventory is the generated [tool reference](../docs/tools.md). It
writes `~/.chromatik-mcp/status.json` for client discovery on startup, rewrites connection
state while running, and records a final disconnected state on clean shutdown. It reads the
optional `~/.chromatik-mcp/config.json` for a fixed port / bind host. See
[../docs/build-plan.md](../docs/build-plan.md) for the PR history and
[../docs/sdk-feasibility.md](../docs/sdk-feasibility.md) for the original SDK spike.

## Build & verify

```sh
# Everyday build gate: `mvn package`, with the full log kept on disk and only a
# one-line pass/fail summary (or extracted failing tests/errors) printed.
scripts/build-gate.sh

# Compile gate: build the jar, confirm lx.package was token-filtered.
scripts/verify-build.sh

# Compile + headless load gate: also boot LX with no UI and confirm the
# plugin is discovered and its initialize() runs.
scripts/verify-build.sh --load
```

Use `build-gate.sh` for routine iteration; use `verify-build.sh --load` when you need to
confirm the plugin actually loads under headless LX (e.g. after touching `lx.package` or
plugin registration).

The load gate (`scripts/verify-load.sh`) runs LX headless against an isolated
`user.home`, drops the built jar into a throwaway `Packages/` dir, force-enables
the plugin, and greps the log for discovery (`Package:Chromatik-MCP`) and init
(`[Chromatik-MCP] plugin loaded`). It never touches your real `~/Chromatik` or
`~/LXStudio`. The harness lives in `src/test/java/chromatikmcp/HeadlessLoadCheck.java`
and is not included in the shipping jar.

Requires `mvn` and a JDK 25+. The pinned `com.heronarts:{lx,glx,glxstudio}:1.2.2`
artifacts resolve from Maven Central; GLX and GLXStudio 1.2.2 are published as Java 25
class files even though the core LX jar remains Java 21 bytecode.

For a future LX release, run `scripts/bump-lx-version.sh --dry-run <version>` from the
repository root, then run it without `--dry-run`. Use `--check <version>` for a
non-mutating drift check. The helper does not change JDK/workflow pins or derived catalog
hashes; refresh those separately when the release requires it.

## Install into Chromatik

```sh
mvn -Pinstall install   # copies the jar to ~/Chromatik/Packages/
```

Restart Chromatik; "Chromatik-MCP" appears in the installed-packages list.
