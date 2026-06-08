# lx-mcp — Java package

The drop-in LX/Chromatik package. Ships as a single jar; LX's class loader picks it up from `~/Chromatik/Packages/`.

What it contributes:
- An `LXPlugin` that, on initialization, starts an HTTP MCP server inside the LX runtime.
- A set of MCP tools that mutate LX state via `LXCommand` (channels, patterns, modulators, modulation routing, MIDI mappings, parameter sets). Undo for free where `LXCommand` covers the operation.
- A status file at `~/.lx-mcp/status.json` (`{pid, port, projectPath, lxVersion}`) so AI clients can discover the HTTP port.

No `.lxp` file editing, no file watcher, no Node bridge — AI edits happen in-process and the Chromatik UI updates live.

Build:

```
mvn package              # produces target/lx-mcp-*.jar
mvn -Pinstall install    # also copies the jar into ~/Chromatik/Packages/
```

Scaffolding mirrors `/Users/danoved/Source/Apotheneum/` (Java 21, `com.heronarts:lx:1.2.1` as `provided`, `lx.package` JSON descriptor in `src/main/resources/` with Maven token filtering).

See [../docs/build-plan.md](../docs/build-plan.md) for the PR roadmap and [../CLAUDE.md](../CLAUDE.md) for the composability rules (`tool handler → domain primitive → LXCommand.perform(...)`).

Status: PR-0 not yet implemented.
