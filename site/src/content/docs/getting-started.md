---
title: Getting started
description: Install the chromatik-mcp jar into Chromatik and start the embedded MCP server.
---

chromatik-mcp is a drop-in [Chromatik](https://chromatik.co/) (LX Studio) package: one jar
that embeds an MCP server inside the LX runtime. Install the jar, enable the plugin,
point your MCP client at the port it publishes. No separate server process.

## Requirements

- [Chromatik](https://chromatik.co/download/) with LX **1.2.2** (the pinned framework version in `lx.package`)
- To build from source (optional): Java **25** and Maven (required by the published GLX/GLXStudio 1.2.2 jars)

## 1. Install the jar

### Option A — download (no build tools needed)

Download the latest release into Chromatik's packages directory:

```sh
curl -L --create-dirs -o ~/Chromatik/Packages/chromatik-mcp.jar \
  https://github.com/oveddan/chromatik-mcp/releases/latest/download/chromatik-mcp.jar
```

That URL always resolves to the newest tagged release; older ones are on the
[releases page](https://github.com/oveddan/chromatik-mcp/releases). To track main
instead — rebuilt on every push that touches the plugin code and passes the full test
suite — swap the URL for
`https://github.com/oveddan/chromatik-mcp/releases/download/latest/chromatik-mcp.jar`.

### Option B — build from source

```sh
git clone https://github.com/oveddan/chromatik-mcp.git
cd chromatik-mcp/package
mvn install -Pinstall
```

The `install` profile copies the shaded jar into `~/Chromatik/Packages/`, where
Chromatik discovers packages. (Without the profile, `mvn package` just builds it under
`target/`.) The `install` profile skips tests — they're the developer gate, not part of
the consumer install flow.

:::caution[Use one install method, not both]
The download is named `chromatik-mcp.jar`; the Maven install produces
`chromatik-mcp-<version>.jar`. If both sit in `~/Chromatik/Packages/`, Chromatik loads the
plugin twice — and one copy is stale. Remove the other jar when switching methods —
including any `lx-mcp*.jar` left over from before this project was renamed.
:::

:::caution[Never reinstall while Chromatik is running]
Overwriting the jar with Chromatik open triggers LX's package hot-reload watcher, which
orphans the live MCP server instead of restarting it — it keeps answering with stale
code (compare `get_status`'s `buildTime`). On versions before v0.0.1's fix, it also made
Chromatik hang on quit. **Quit Chromatik → install → relaunch.**
:::

## 2. Enable the plugin in Chromatik

Start (or restart) Chromatik, open **Preferences → Plugins**, and enable **Chromatik-MCP**.
Restart once more if Chromatik asks. On startup the plugin:

- starts the MCP server on an ephemeral port, bound to **127.0.0.1 only** — MCP clients
  must run on the same machine; there is no authentication layer
- writes the discovery file `~/.chromatik-mcp/status.json`

Enabling **Chromatik-MCP** is the only checkbox — the bundled **Chromatik-MCP UI**
plugin enables itself alongside it and adds a live status section (connected state,
server URL, time since last activity) to the left pane's **Global** tab.

### Configuring the port and host

By default the server binds an **ephemeral port** on **127.0.0.1** (loopback-only,
safe, zero-config). To pin a fixed port or change the bind host, create
`~/.chromatik-mcp/config.json`:

```json
{
  "port": 7000,
  "host": "127.0.0.1"
}
```

- `port` — `0` (the default) picks an ephemeral port each startup; any other value pins
  a fixed port. If that port is already in use, the plugin fails at startup (surfaced
  via LX's error dialog) rather than silently falling back.
- `host` — defaults to `127.0.0.1`. A malformed or missing `config.json` silently falls
  back to the defaults.

:::danger[Security]
Setting `host` to anything other than a loopback address binds the MCP server to a
network-reachable interface. This server is **unauthenticated** — anyone who can reach
that address has full control of a live show. Only do this on a trusted network. The
plugin logs a loud warning on startup whenever a non-loopback host is configured.
:::

## 3. Discover the port

`~/.chromatik-mcp/status.json` is the discovery handshake:

```json
{
  "pid": 12345,
  "port": 51234,
  "host": "127.0.0.1",
  "url": "http://127.0.0.1:51234/mcp",
  "projectPath": "/Users/you/Chromatik/Projects/MyShow.lxp",
  "lxVersion": "1.2.2",
  "serverVersion": "0.1.0",
  "buildTime": "2026-07-20T18:04:33Z",
  "connected": false,
  "lastActivityAt": null
}
```

- The MCP endpoint is `url` (streamable HTTP).
- Check `pid` liveness before trusting the file — a crashed session can leave a stale
  file pointing at a dead port. If two Chromatik instances run at once, the last one
  wins the file.
- `connected` / `lastActivityAt` track live client activity; the `get_status` tool
  reports the same state without re-reading the file.

## 4. Connect your AI client

See [Connect your AI client](../connect/) for step-by-step setup in Claude Code,
Claude Desktop, Cursor, VS Code, Codex, and any other streamable-HTTP MCP client.

Verify the connection by asking your agent to call `get_project_info` — it should
report the LX version, channel count, and OSC ports of the running instance.

## Troubleshooting

- **No status.json** — the plugin isn't enabled or Chromatik hasn't restarted since
  install. Check Preferences → Plugins for Chromatik-MCP.
- **Connection refused on the recorded port** — stale status.json (check the `pid`);
  restart Chromatik and re-read the file.
- **Tools error with `internal` unexpectedly** — check Chromatik's log; tool failures
  that LX swallows are logged there with the `[Chromatik-MCP]` prefix.
- **A non-fatal `ClassNotFoundException: jakarta.mail.Authenticator` at load** is a
  known cosmetic artifact of the shaded jar.
