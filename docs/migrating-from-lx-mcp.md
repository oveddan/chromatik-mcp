# Migrating from lx-mcp

This project was renamed from **lx-mcp** to **chromatik-mcp**. If you installed it under
the old name, three things need cleaning up — the old jar, the old config directory, and
your MCP client config.

## 1. Remove the old jar(s)

Delete any `lx-mcp*.jar` from `~/Chromatik/Packages/`. Otherwise Chromatik loads both
plugins: two MCP servers run, and the old one keeps winning `~/.lx-mcp/status.json`, so
clients silently keep driving the old code.

## 2. Move your config

The config and status directory moved from `~/.lx-mcp/` to `~/.chromatik-mcp/`. A pinned
port or custom host does not carry over by itself:

```sh
mkdir -p ~/.chromatik-mcp
mv ~/.lx-mcp/config.json ~/.chromatik-mcp/config.json
rm -rf ~/.lx-mcp
```

Removing the leftover directory matters — a stale `status.json` there can mislead
scripts that still fall back to the legacy path. Any user catalog overlays move the same
way.

## 3. Update your MCP client

Re-add the server, reading the port from the *new* status file. See
[the README](../README.md#5-connect-your-ai-client) for per-client instructions.
