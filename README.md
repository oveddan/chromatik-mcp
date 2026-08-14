# chromatik-mcp

A drop-in LX/Chromatik package that lets an agent read, explain, compose into, and debug a running Chromatik show over MCP.

The jar embeds an MCP server inside the LX runtime, so any MCP-speaking client — Claude Code, Claude Desktop, Cursor, Codex, your own orchestrator — connects straight into the running engine. Every call reads or mutates the same live object graph your console renders, and the human and the agent share one undo stack. No separate server process, no `.lxp` file editing, no reload cycle.

<!-- landing:start -->

## Requirements

- [Chromatik](https://chromatik.co/download/) with LX **1.2.2** (the pinned framework version in `lx.package`)
- To build from source (optional): Java **25** and Maven — required by the published GLX/GLXStudio jars

## 1. Install the jar

**Download** — no build tools needed. This URL always resolves to the newest tagged release; older ones are on the [releases page](https://github.com/oveddan/chromatik-mcp/releases):

```sh
curl -L --create-dirs -o ~/Chromatik/Packages/chromatik-mcp.jar \
  https://github.com/oveddan/chromatik-mcp/releases/latest/download/chromatik-mcp.jar
```

To track main instead, use the rolling `latest` prerelease — republished on every main-branch push that touches `package/` and passes the full test suite: `https://github.com/oveddan/chromatik-mcp/releases/download/latest/chromatik-mcp.jar`

**Or build from source:**

```sh
git clone https://github.com/oveddan/chromatik-mcp.git
cd chromatik-mcp/package
mvn install -Pinstall
```

The `install` profile copies the shaded jar into `~/Chromatik/Packages/`, where Chromatik discovers packages. (Without the profile, `mvn package` just builds it under `target/`.) It also skips tests — those are the developer gate, not part of the consumer install flow.

> [!WARNING]
> **Use one install method, not both.** The download is named `chromatik-mcp.jar`; the Maven install produces `chromatik-mcp-<version>.jar`. If both sit in `~/Chromatik/Packages/`, Chromatik loads the plugin twice — and one copy is stale. That's the classic "I installed the new jar but nothing changed" trap; `get_status`'s `buildTime` exposes it. Remove the other jar when switching methods.

> [!CAUTION]
> **Never reinstall while Chromatik is running.** Overwriting the jar triggers LX's package hot-reload watcher, which orphans the live MCP server instead of restarting it — the **old** server keeps answering on the same port, still running pre-reinstall code. Compare `get_status`'s `buildTime` against the freshly built jar to spot it. **Quit Chromatik → install → relaunch.**

Upgrading from **lx-mcp**, the pre-rename name? Old jars and the old `~/.lx-mcp/` config directory both need cleaning up, or two plugins load at once and clients silently drive the old one: [docs/migrating-from-lx-mcp.md](docs/migrating-from-lx-mcp.md).

## 2. Enable the plugin in Chromatik

Start (or restart) Chromatik, open **Preferences → Plugins**, and enable **Chromatik-MCP**. Restart once more if Chromatik asks. On startup the plugin:

- starts the MCP server on an ephemeral port, bound to **127.0.0.1 only** — MCP clients must run on the same machine; there is no authentication layer
- writes the discovery file `~/.chromatik-mcp/status.json`

**Chromatik-MCP** is the only checkbox you need. It automatically enables its bundled **Chromatik-MCP UI** companion, which adds a live status section — connected state, server URL, time since last activity — to the left pane's **Global** tab. The companion stays a separate internal plugin because its Chromatik UI dependencies are unavailable in headless LX; the core MCP server still loads in CI and other UI-free runtimes.

## 3. Configure the port and host (optional)

By default the server binds an **ephemeral port** on **127.0.0.1** — loopback-only, safe, zero-config. To pin a fixed port or change the bind host, create `~/.chromatik-mcp/config.json`:

```json
{
  "port": 7000,
  "host": "127.0.0.1"
}
```

- `port` — `0` (the default) picks an ephemeral port each startup; any other value pins a fixed port. **If that port is already in use the plugin fails at startup** (LX marks it errored and surfaces the failure in its error dialog) rather than silently falling back to another port.
- `host` — defaults to `127.0.0.1`. A malformed or missing `config.json` silently falls back to the defaults; a config that fails to parse never takes the server down.

Restart Chromatik after saving — a config change only takes effect on the next plugin startup.

Pinning a port is worth it if your client's config is static JSON (Cursor, VS Code, Codex) or if you use the agent plugin, which defaults to **3232** — chosen to echo Chromatik's default OSC ports 3030/4040:

```json
{ "port": 3232 }
```

> [!CAUTION]
> **Setting `host` to anything other than `localhost`, `::1`, or a `127.0.0.0/8` address binds the MCP server to a network-reachable interface.** This server is **unauthenticated** — anyone who can reach that address has full control of a live show: arbitrary parameter mutation, project load/save, everything. Only do this on a trusted network, and only if you understand the exposure. The plugin logs a loud warning on startup whenever a non-loopback host is configured.

## 4. Discover the port

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

Read it any time with:

```sh
jq -r .port ~/.chromatik-mcp/status.json     # port only
jq -r .url  ~/.chromatik-mcp/status.json     # full endpoint URL
```

- The MCP endpoint is `url`, equivalently `http://<host>:<port>/mcp`, streamable HTTP.
- **Check `pid` liveness before trusting the file.** A clean exit — quitting Chromatik, disabling the plugin — rewrites it with `connected: false` first, but a crashed or force-killed session leaves whatever was last written, which can point at a dead port. If two Chromatik instances run at once, the last one wins the file.
- `projectPath` is the project seen at the most recent status-file rewrite (`null` if none). Project changes don't trigger a rewrite by themselves, so query `get_project_info` when the live project path matters.
- `connected` and `lastActivityAt` track live client activity, rewritten whenever the connection state flips. `lastActivityAt` is ISO-8601, or `null` if no client has ever been active. The `get_status` tool reports the same state live, without re-reading the file.

## 5. Connect your AI client

Any MCP client that speaks streamable HTTP works. The endpoint is `http://127.0.0.1:<port>/mcp` and nothing else — no authentication, no SSE-only legacy endpoint.

**Claude Code** — one command, reading the live port with `jq`:

```sh
claude mcp add --transport http chromatik "http://127.0.0.1:$(jq -r .port ~/.chromatik-mcp/status.json)/mcp"
```

Add `--scope user` to make it available in every project. Verify with `/mcp` — the `chromatik` server should list its tools.

**Claude Desktop** — **Settings → Connectors → Add custom connector**, name it `chromatik`, paste the endpoint URL from `status.json`. Note that `claude_desktop_config.json` only accepts stdio (`command`/`args`) servers, so an HTTP entry pasted there is silently ignored.

<details>
<summary><b>Cursor, VS Code, Codex, and stdio-only clients</b></summary>

These all take static JSON or TOML, so [pin a port](#3-configure-the-port-and-host-optional) first.

**Cursor** — `~/.cursor/mcp.json` (global) or `.cursor/mcp.json` (project), then enable it under **Settings → MCP**:

```json
{
  "mcpServers": {
    "chromatik": { "url": "http://127.0.0.1:3232/mcp" }
  }
}
```

**VS Code (GitHub Copilot)** — `.vscode/mcp.json` in your workspace. VS Code prompts to start the connection; tools appear in Copilot Chat's agent-mode tool picker:

```json
{
  "servers": {
    "chromatik": { "type": "http", "url": "http://127.0.0.1:3232/mcp" }
  }
}
```

**Codex** — `~/.codex/config.toml`:

```toml
[mcp_servers.chromatik]
url = "http://127.0.0.1:3232/mcp"
```

**Clients that only support stdio** can bridge with [`mcp-remote`](https://www.npmjs.com/package/mcp-remote):

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

</details>

Verify the connection by asking your agent to call `get_project_info` — it should report the LX version, channel count, and OSC ports of the running instance. Then try:

- *"List the channels and describe the current show structure."*
- *"Add a channel with a gradient pattern and make it slowly breathe using an LFO on its fader."*
- *"Grab a frame render and describe what the output looks like right now."*

## 6. Install the agent plugin (recommended)

The steps above get you the raw tools, which is enough. The [agent plugin](agent-plugin/) adds everything around them — the house rules for driving a live show, a project-surveyor agent, a code reviewer, and the `/chromatik-learn` and `/chromatik-review` commands — so you don't have to paste context in yourself. For Claude Code it's one symlink:

```sh
git clone https://github.com/oveddan/chromatik-mcp.git
ln -s "$(pwd)/chromatik-mcp/agent-plugin" ~/.claude/skills/chromatik
```

Restart Claude Code, and pin the port to `3232` so the bundled connection lands correctly. Full instructions, including Codex: [agent-plugin/README.md](agent-plugin/README.md).

## Troubleshooting

- **No `status.json`** — the plugin isn't enabled, or Chromatik hasn't restarted since install. Check Preferences → Plugins for Chromatik-MCP.
- **Connection refused on the recorded port** — stale `status.json` (check the `pid`); restart Chromatik and re-read the file.
- **Worked yesterday, refused today** — Chromatik restarted and the ephemeral port moved. Re-read `status.json`, or pin a port.
- **Tools listed but calls fail**, or a tool errors with `internal` unexpectedly — check Chromatik's log for `[Chromatik-MCP]` entries; failures LX swallows internally are logged there.
- **A non-fatal `ClassNotFoundException: jakarta.mail.Authenticator` at load** is a known cosmetic artifact of the shaded jar (slimming is a tracked follow-up).

[`scripts/mcp-client.sh`](scripts/mcp-client.sh) is a dependency-light (`curl` + `python3`) client for exercising the endpoint without a full MCP client — the quickest way to confirm the server is reachable:

```sh
scripts/mcp-client.sh init                    # handshake, prints server instructions
scripts/mcp-client.sh tools                   # list available tool names
scripts/mcp-client.sh call get_status '{}'    # call a tool, print structuredContent
```

It resolves the port from `$CHROMATIK_MCP_PORT`, then `~/.chromatik-mcp/status.json`, and transparently re-initializes its session if Chromatik has restarted since the last call.

<!-- landing:end -->

## What it can do

The tool surface works end-to-end: discovery, parameters, tempo, modulation wiring, channels/groups/patterns/effect chains, mixer performance controls (crossfader, cue/aux), MIDI mapping and templates, palette read-write, snapshots, model views, fixtures and output wiring, render previews, OSC addressing, project/model save, arrange-timeline composition authoring, and a generated semantic catalog of what each component does.

Everything is addressed by canonical LX path (e.g. `/lx/mixer/channel/1/fader`) as returned by the discovery tools, and mutations are undoable in Chromatik with Cmd-Z unless a tool says otherwise.

**[docs/tools.md](docs/tools.md)** is the generated inventory of all tools, regenerated from the running server — the authoritative list of what exists today.

## Documentation

Everything lives as markdown in this repo, readable by humans and agents alike.

| | |
|---|---|
| [docs/tools.md](docs/tools.md) | generated inventory of every tool |
| [docs/architecture.md](docs/architecture.md) | the contract an integrator builds against: wire shape, addressing, threading, undo, state lifecycle |
| [agent-plugin/](agent-plugin/) | the Claude Code / Codex plugin — driving rules, recipes, agents, commands |
| [agent-plugin/skills/driving-chromatik/SKILL.md](agent-plugin/skills/driving-chromatik/SKILL.md) | house rules for driving a live show well — **point your agent here** |
| [.../references/recipes.md](agent-plugin/skills/driving-chromatik/references/recipes.md) | task recipes: survey a project, build structure, chain effects, map macros, author the timeline |
| [docs/development.md](docs/development.md) | build from source, test, drift gates, repo layout, conventions |
| [docs/tool-conventions.md](docs/tool-conventions.md) | tool naming, canonical paths, wire shapes, threading, mutation contracts |
| [docs/osc-addressing.md](docs/osc-addressing.md) | canonical paths vs OSC addresses, and the label-based modulator exception |
| [docs/](docs/) | everything else — catalog format, build plan, QA strategy, releasing |

## Architecture

The jar embeds an HTTP MCP server (official Java MCP SDK, streamable-HTTP on embedded Tomcat) inside the LX runtime as an `LXPlugin`. Mutations route through `LXCommand`, so every change gets undo for free, and are serialized onto the LX engine thread. The filesystem touchpoints are `~/.chromatik-mcp/status.json` (written on startup for endpoint discovery) and the optional `~/.chromatik-mcp/config.json`.

```
tool handler  ──> domain primitive  ──> LXCommand.perform(...)   (mutation with undo)
(MCP-shaped)     (intent, narrow)   ──> direct lx.engine.* edit  (mutation without undo)
                                    ──> read lx.engine.*         (read-only)
```

Full contract — connection lifecycle, `Result` wire shape, addressing rules, threading guarantees, and the undo exceptions: **[docs/architecture.md](docs/architecture.md)**.

## Develop from source

Java 25 and Maven (the published LX jars require 25); Node 20 only if you touch the generated docs artifacts or the landing page.

```sh
git clone https://github.com/oveddan/chromatik-mcp.git
cd chromatik-mcp
package/scripts/build-gate.sh     # compile + the full headless test suite
cd package && mvn install -Pinstall   # drop the jar into ~/Chromatik/Packages/
```

Use `build-gate.sh` rather than raw `mvn package` — it keeps the full log on disk and prints a one-line summary, and carries a watchdog for a known macOS CoreMIDI deadlock. `package/scripts/verify-load.sh` is the headless plugin-load gate; several docs artifacts are generated and gated against drift. Full guide — repo layout, testing conventions, drift gates, catalog regeneration, and the conventions a change has to hold to: **[docs/development.md](docs/development.md)**.

## License

[MIT](LICENSE). Note the LX framework this plugin targets is separately licensed (free for non-commercial use — see [lx.studio/license](https://lx.studio/license)); this license covers only the chromatik-mcp code.
