# Installing chromatik-mcp

chromatik-mcp is a drop-in [Chromatik](https://chromatik.co/) (LX Studio) package: one jar that
embeds an MCP server inside the LX runtime. Install the jar, enable the plugin, point
your MCP client at the port it publishes. No separate server process.

## Requirements

- Chromatik with LX **1.2.1** (the pinned framework version in `lx.package`)
- To build from source: Java **21** and Maven

## 1. Install the jar

**Download** (no build tools needed) — the rolling `latest` release republishes on
every main-branch push that passes the full test suite; versioned releases are on the
[releases page](https://github.com/oveddan/chromatik-mcp/releases):

```sh
curl -L --create-dirs -o ~/Chromatik/Packages/chromatik-mcp.jar \
  https://github.com/oveddan/chromatik-mcp/releases/download/latest/chromatik-mcp.jar
```

**Or build from source:**

```sh
git clone git@github.com:oveddan/chromatik-mcp.git
cd chromatik-mcp/package
mvn install -Pinstall
```

**Use one install method, not both.** The download is named `chromatik-mcp.jar`; the Maven
install produces `chromatik-mcp-<version>.jar`. If both sit in `~/Chromatik/Packages/`,
Chromatik loads the plugin twice (and one copy is stale — the classic "I installed
the new jar but nothing changed" trap; `get_status`'s `buildTime` exposes it).
Remove the other jar when switching methods.

### Upgrading from lx-mcp (the pre-rename name)

This project was renamed from **lx-mcp**. If you installed it under that name:

1. **Remove the old jar(s)** — delete any `lx-mcp*.jar` from `~/Chromatik/Packages/`.
   Otherwise Chromatik loads both plugins: two MCP servers, and the old one keeps
   winning `~/.lx-mcp/status.json`, so clients silently keep driving the old code.
2. **Move your config** — the config/status dir moved from `~/.lx-mcp/` to
   `~/.chromatik-mcp/`. A pinned port or custom host does not carry over by itself:
   `mv ~/.lx-mcp/config.json ~/.chromatik-mcp/config.json` (create the dir if needed),
   then delete the leftover `~/.lx-mcp/` so stale `status.json` files can't mislead
   scripts. Any user catalog overlays move the same way.
3. **Update your MCP client** — re-add the server reading the port from the *new*
   status file (the examples below use the `chromatik` alias).

The `install` profile copies the shaded jar into `~/Chromatik/Packages/`, where Chromatik
discovers packages. (Without the profile, `mvn package` just builds it under `target/`.)
The `install` profile also skips tests — they're the developer/PR gate, not part of the
consumer install flow. Run `mvn package` for the full suite, or force tests during install
with `mvn install -Pinstall -DskipTests=false`.

To sanity-check the jar loads inside real LX from a deployment-faithful classpath before
touching Chromatik:

```sh
./scripts/verify-load.sh
```

## 2. Enable the plugin in Chromatik

Start (or restart) Chromatik, open **Preferences → Plugins**, and enable **Chromatik-MCP**.
Restart once more if Chromatik asks. On startup the plugin:

- starts the MCP server on an ephemeral port, bound to **127.0.0.1 only** — MCP clients
  must run on the same machine; there is no authentication layer
- writes the discovery file `~/.chromatik-mcp/status.json`

To also see a live status indicator in the Chromatik UI, enable **Chromatik-MCP UI** as well
(see [step 5](#5-optional-enable-the-chromatik-ui-status-section)).

### Configuring the port and host

By default the server binds an **ephemeral port** on **127.0.0.1** (loopback-only, safe,
zero-config). To pin a fixed port or change the bind host, create
`~/.chromatik-mcp/config.json`:

```json
{
  "port": 7000,
  "host": "127.0.0.1"
}
```

- `port` — `0` (the default) picks an ephemeral port each startup; any other value pins
  a fixed port. **If that port is already in use, the plugin fails at startup** (LX marks
  it errored and surfaces the failure via its error dialog) rather than silently falling
  back to another port.
- `host` — defaults to `127.0.0.1`. A malformed or missing `config.json` silently falls
  back to the defaults; a config that fails to parse never takes the server down.

**Recommended if you connect from Claude Code via the checked-in `.mcp.json`**: pin the
port to `3232` (chosen to echo Chromatik's default OSC ports 3030/4040) so the repo's
`.mcp.json` finds the server without any per-machine editing:

```json
{ "port": 3232 }
```

Restart Chromatik after saving this file — a config change only takes effect on the next
plugin startup. Otherwise, leave `config.json` absent and stay on the ephemeral+loopback
defaults described above.

**SECURITY WARNING**: setting `host` to anything other than `localhost`, `::1`, or a
`127.0.0.0/8` address binds the MCP server to a network-reachable interface. This server
is **unauthenticated** — anyone who can reach that address has full control of a live
show (arbitrary parameter mutation, project load/save, etc.). Only do this on a trusted
network, and only if you understand the exposure. The plugin logs a loud warning on
startup whenever a non-loopback host is configured.

## 3. Discover the port

`~/.chromatik-mcp/status.json` is the discovery handshake:

```json
{
  "pid": 12345,
  "port": 51234,
  "host": "127.0.0.1",
  "url": "http://127.0.0.1:51234/mcp",
  "projectPath": "/Users/you/Chromatik/Projects/MyShow.lxp",
  "lxVersion": "1.2.1",
  "connected": false,
  "lastActivityAt": null
}
```

- The MCP endpoint is `url` (equivalently `http://<host>:<port>/mcp`, streamable HTTP).
- **Check `pid` liveness before trusting the file** — a clean exit (quitting Chromatik,
  disabling the plugin) rewrites the file with `connected: false` first, but a crashed or
  force-killed session leaves whatever was last written, which can point at a dead port.
  If two Chromatik instances run at once, the last one wins the file.
- `projectPath` is the project open at startup (`null` if none).
- `connected` and `lastActivityAt` track live client activity and are rewritten whenever
  the connection state flips (an MCP client connects or its stream/window of recent
  activity expires); `lastActivityAt` is an ISO-8601 timestamp, or `null` if no client
  has ever been active. The `get_status` tool reports the same state live, without
  re-reading the file.

## 4. Connect an MCP client

Any MCP client that speaks streamable HTTP works. The port is ephemeral, so read it from
status.json (or re-read it after restarting Chromatik and update your client config).

**Claude Code** — the repo ships a project-scope [`.mcp.json`](../.mcp.json) that expects
the fixed port `3232`:

1. Pin the port — see [Configuring the port and host](#configuring-the-port-and-host)
   above — and restart Chromatik.
2. Open Claude Code in this repo (or any directory containing a copy of `.mcp.json`) and
   the `chromatik` server is available automatically.

For other projects, or if you'd rather not pin the port, add the server manually with the
port read live from `status.json` (uses `jq`):

```sh
claude mcp add --transport http chromatik "http://127.0.0.1:$(jq -r .port ~/.chromatik-mcp/status.json)/mcp"
```

**Claude Desktop / other clients** — point the client at
`http://127.0.0.1:<port>/mcp` (`3232` if pinned). In Claude Desktop, remote/HTTP MCP
servers are added as a custom connector (Settings → Connectors → Add custom connector)
with that URL — `claude_desktop_config.json` only accepts stdio (`command`/`args`)
servers, so an HTTP entry pasted there is silently ignored. Clients that only support
stdio config can bridge with [`mcp-remote`](https://www.npmjs.com/package/mcp-remote):

```json
{
  "mcpServers": {
    "chromatik": {
      "command": "npx",
      "args": ["mcp-remote", "http://127.0.0.1:3232/mcp"]
    }
  }
}
```

**Curl-level fallback / smoke test** — [`scripts/mcp-client.sh`](../scripts/mcp-client.sh)
is a dependency-light (`curl` + `python3`) client for exercising the endpoint without a
full MCP client, and doubles as a quick way to confirm the server is reachable:

```sh
scripts/mcp-client.sh init                          # handshake, prints server instructions
scripts/mcp-client.sh tools                          # list available tool names
scripts/mcp-client.sh call get_status '{}'           # call a tool, print structuredContent
```

It resolves the port from `$CHROMATIK_MCP_PORT`, then `~/.chromatik-mcp/status.json`, then
the legacy `~/.lx-mcp/status.json`, and transparently re-initializes its session if
Chromatik has restarted since the last call.

Verify the connection by calling `get_project_info` — it should report the LX version,
channel count, and OSC ports of the running instance.

## 5. (Optional) Enable the Chromatik UI status section

The jar also bundles a second, Chromatik-only plugin, **Chromatik-MCP UI**, that adds a small
"Chromatik-MCP" section to the left pane's **Global** tab: a connected/disconnected indicator, the
server URL, and time since the last client activity.

Enable it the same way as the core plugin — **Preferences → Plugins → Chromatik-MCP UI** —
alongside (not instead of) **Chromatik-MCP**. The UI plugin reads state from the core plugin and
does nothing on its own; if the core plugin isn't enabled, Chromatik-MCP UI logs a message and
skips adding the section rather than erroring.

This split exists because the UI plugin depends on Chromatik's studio/UI classes, which
aren't present in a pure-core headless LX run (e.g. `scripts/verify-load.sh`, CI). Only
the core **Chromatik-MCP** plugin is required to run the MCP server headlessly; **Chromatik-MCP UI** is
a visual convenience for interactive Chromatik sessions.

## Reinstalling while Chromatik is running

Overwriting the jar (e.g. `mvn install -Pinstall`) while Chromatik is running triggers
LX's package hot-reload watcher, which orphans the live MCP server instead of restarting
it — the **old** server keeps answering on the same port, still running the code from
before the reinstall. Compare `get_status`'s `buildTime` against the freshly built jar's
timestamp to spot this; a mismatch means you're talking to the orphan. **Restart
Chromatik after every reinstall** to actually pick up the new jar.

## Troubleshooting

- **No status.json** — the plugin isn't enabled or Chromatik hasn't restarted since
  install. Check Preferences → Plugins for Chromatik-MCP.
- **Connection refused on the recorded port** — stale status.json (check the `pid`);
  restart Chromatik and re-read the file.
- **Tools error with `internal` unexpectedly** — check Chromatik's log; tool failures
  that LX swallows are logged there with the `[Chromatik-MCP]` prefix.
- **A non-fatal `ClassNotFoundException: jakarta.mail.Authenticator` at load** is a
  known cosmetic artifact of the shaded jar (slimming is a tracked follow-up).
