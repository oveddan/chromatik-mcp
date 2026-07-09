# Installing lx-mcp

lx-mcp is a drop-in [Chromatik](https://chromatik.co/) (LX Studio) package: one jar that
embeds an MCP server inside the LX runtime. Install the jar, enable the plugin, point
your MCP client at the port it publishes. No separate server process.

## Requirements

- Chromatik with LX **1.2.1** (the pinned framework version in `lx.package`)
- To build from source: Java **21** and Maven

## 1. Build and install the jar

```sh
git clone git@github.com:oveddan/lx-mcp.git
cd lx-mcp/package
mvn install -Pinstall
```

The `install` profile copies the shaded jar into `~/Chromatik/Packages/`, where Chromatik
discovers packages. (Without the profile, `mvn package` just builds it under `target/`.)

To sanity-check the jar loads inside real LX from a deployment-faithful classpath before
touching Chromatik:

```sh
./scripts/verify-load.sh
```

## 2. Enable the plugin in Chromatik

Start (or restart) Chromatik, open **Preferences → Plugins**, and enable **LX-MCP**.
Restart once more if Chromatik asks. On startup the plugin:

- starts the MCP server on an ephemeral port, bound to **127.0.0.1 only** — MCP clients
  must run on the same machine; there is no authentication layer
- writes the discovery file `~/.lx-mcp/status.json`

## 3. Discover the port

`~/.lx-mcp/status.json` is the discovery handshake:

```json
{
  "pid": 12345,
  "port": 51234,
  "projectPath": "/Users/you/Chromatik/Projects/MyShow.lxp",
  "lxVersion": "1.2.1"
}
```

- The MCP endpoint is `http://127.0.0.1:<port>/mcp` (streamable HTTP).
- **Check `pid` liveness before trusting the file** — it is written once at plugin
  startup and not yet cleaned up on exit, so a stale file from a previous run can point
  at a dead port. If two Chromatik instances run at once, the last one wins the file.
- `projectPath` is the project open at startup (`null` if none).

## 4. Connect an MCP client

Any MCP client that speaks streamable HTTP works. The port is ephemeral, so read it from
status.json (or re-read it after restarting Chromatik and update your client config).

**Claude Code** — from your working directory (uses `jq` to read the port):

```sh
claude mcp add --transport http lx "http://127.0.0.1:$(jq -r .port ~/.lx-mcp/status.json)/mcp"
```

**Claude Desktop / other clients** — add a streamable-HTTP server with URL
`http://127.0.0.1:<port>/mcp` in the client's MCP settings.

Verify the connection by calling `get_project_info` — it should report the LX version,
channel count, and OSC ports of the running instance.

## Troubleshooting

- **No status.json** — the plugin isn't enabled or Chromatik hasn't restarted since
  install. Check Preferences → Plugins for LX-MCP.
- **Connection refused on the recorded port** — stale status.json (check the `pid`);
  restart Chromatik and re-read the file.
- **Tools error with `internal` unexpectedly** — check Chromatik's log; tool failures
  that LX swallows are logged there with the `[LX-MCP]` prefix.
- **A non-fatal `ClassNotFoundException: jakarta.mail.Authenticator` at load** is a
  known cosmetic artifact of the shaded jar (slimming is a tracked follow-up).
