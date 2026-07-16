# lx-mcp — Java package

The drop-in LX/Chromatik jar.

See [../docs/build-plan.md](../docs/build-plan.md) for the roadmap and [../CLAUDE.md](../CLAUDE.md) for contributor conventions.

**Status**: PR-1a (SDK feasibility) — **GO**. `LxMcpPlugin` starts an embedded streamable-HTTP
MCP server (official Java MCP SDK on embedded Tomcat) from `initialize()` and writes
`~/.lx-mcp/status.json` for client discovery. No domain tools yet. See
[../docs/sdk-feasibility.md](../docs/sdk-feasibility.md).

## Build & verify

```sh
# Compile gate: build the jar, confirm lx.package was token-filtered.
scripts/verify-build.sh

# Compile + headless load gate: also boot LX with no UI and confirm the
# plugin is discovered and its initialize() runs.
scripts/verify-build.sh --load
```

The load gate (`scripts/verify-load.sh`) runs LX headless against an isolated
`user.home`, drops the built jar into a throwaway `Packages/` dir, force-enables
the plugin, and greps the log for discovery (`Package:LX-MCP`) and init
(`[LX-MCP] plugin loaded`). It never touches your real `~/Chromatik` or
`~/LXStudio`. The harness lives in `src/test/java/lxmcp/HeadlessLoadCheck.java`
and is not included in the shipping jar.

Requires `mvn`, a JDK 21+, and `com.heronarts:lx:1.2.1` resolvable from your
local Maven repo.

## Install into Chromatik

```sh
mvn -Pinstall install   # copies the jar to ~/Chromatik/Packages/
```

Restart Chromatik; "LX-MCP" appears in the installed-packages list.
