# lx-mcp

A drop-in LX/Chromatik package for AI-driven show composition over MCP.

**Status**: planning + scaffolding. See [docs/build-plan.md](docs/build-plan.md) for the active roadmap.

## Architecture

The jar embeds an HTTP MCP server (official Java MCP SDK, streamable-HTTP on embedded Tomcat) inside the LX runtime as an `LXPlugin`. Any MCP-speaking client — Claude Code, Claude Desktop, Cursor, Codex, custom orchestrators — connects to it directly and calls tools that mutate LX state in-process. No separate Node server, no `.lxp` file editing, no file watcher. Mutations route through `LXCommand`, so every change gets undo for free, and are serialized onto the LX engine thread via `lx.engine.addTask(...)`. The only filesystem touchpoint is `~/.lx-mcp/status.json`, written on startup so clients can discover the HTTP port.

```
tool handler  ──> domain primitive  ──> LXCommand.perform(...)   (mutation with undo)
(MCP-shaped)     (intent, narrow)   ──> direct lx.engine.* edit  (mutation without undo)
                                    ──> read lx.engine.*         (read-only)
```

## License

TBD.
