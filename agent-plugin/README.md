# The chromatik agent plugin

A Claude Code / Codex plugin that bundles the MCP server connection together with
everything an agent needs to drive a live Chromatik well: the house rules, the
project-profile workflow, a code reviewer, and two slash commands.

The [README](../README.md) gets you the raw MCP connection, and the tools work fine that
way. This adds the context around them, so you don't have to paste it in yourself.

## What's in it

| component | what it does |
|---|---|
| `driving-chromatik` skill | House rules for driving a live Chromatik: connecting and recovering from restarts, canonical-path addressing, timeout and error semantics, consulting component docs before reasoning about a pattern, and the mutate → look → adjust verification loop |
| `project-profile` skill | The project-profile document format and the rules for deriving one from a live show |
| `project-surveyor` agent | Surveys a running project and writes a profile |
| `chromatik-reviewer` agent | Reviews chromatik-mcp changes against the project's conventions |
| `/chromatik-learn` | Builds a project profile from a live instance |
| `/chromatik-review` | Runs the reviewer over a branch |
| bundled MCP connection | The `chromatik` HTTP endpoint declaration, so you don't run `claude mcp add` separately. The server itself lives inside Chromatik — the plugin only points at it |

The driving skill carries four reference files it loads on demand rather than up front —
[`addressing.md`](skills/driving-chromatik/references/addressing.md) (canonical vs OSC
paths), [`error-codes.md`](skills/driving-chromatik/references/error-codes.md) (the full
`Result` wire shape),
[`composition.md`](skills/driving-chromatik/references/composition.md) (the arrange
timeline), and [`recipes.md`](skills/driving-chromatik/references/recipes.md) (worked task
flows). Keeping them out of the always-loaded skill body is deliberate: an agent that
never touches a clip shouldn't pay for the timeline rules on every session.

## Install for Claude Code

Not published to a marketplace yet, so install from a clone. Claude Code auto-loads any
`~/.claude/skills/<name>/` directory containing a `.claude-plugin/plugin.json` as
`<name>@skills-dir` — no install command needed:

```sh
git clone https://github.com/oveddan/chromatik-mcp.git
ln -s "$(pwd)/chromatik-mcp/agent-plugin" ~/.claude/skills/chromatik
```

Or copy it, if you'd rather not have a symlink into a clone you might move:

```sh
cp -R chromatik-mcp/agent-plugin ~/.claude/skills/chromatik
```

**Restart Claude Code** — a running session won't pick it up. Then check that
`driving-chromatik` appears in your available skills and that `/chromatik-learn` and
`/chromatik-review` are offered.

> [!IMPORTANT]
> **Pin the port to 3232.** The bundled connection uses
> `http://127.0.0.1:${CHROMATIK_MCP_PORT:-3232}/mcp` — it defaults to 3232 rather than
> reading `status.json`, because a plugin manifest can't run `jq`. Put `{"port": 3232}` in
> `~/.chromatik-mcp/config.json` and restart Chromatik, or set `CHROMATIK_MCP_PORT` in
> your environment. Otherwise the connection lands on the wrong ephemeral port. See
> [Configure the port and host](../README.md#3-configure-the-port-and-host-optional).

## Install for Codex

The same directory carries a Codex manifest at `.codex-plugin/plugin.json` declaring
`"skills": "./skills/"` — both skills come across. Install it the way your Codex version
expects to pick up a local plugin directory, pointing at the same `agent-plugin/` tree.

> [!WARNING]
> **The Codex side is best-effort and not fully confirmed** against Codex's own plugin
> docs:
>
> - `skills/driving-chromatik/agents/openai.yaml` may belong at the plugin root rather
>   than nested under the skill.
> - The manifest declares only `skills`. Whether Codex has an equivalent mechanism for
>   `agents/` and `commands/` is unverified — assume for now that you get the skills but
>   not the reviewer agent or the slash commands.
> - Codex's MCP dependency declaration uses a **literal** port instead of the
>   `${CHROMATIK_MCP_PORT:-3232}` default-value expansion, which is a Claude Code feature.
>   Pin the port in `~/.chromatik-mcp/config.json` rather than relying on the environment
>   variable.
>
> If you've confirmed any of these either way, please
> [open an issue](https://github.com/oveddan/chromatik-mcp/issues).

## Any other MCP client

Clients without a plugin system still get the tools — connect the server directly per the
[README](../README.md#5-connect-your-ai-client), then point your agent at
[`skills/driving-chromatik/SKILL.md`](skills/driving-chromatik/SKILL.md) so it has the
house rules in context.

## Layout

```
.claude-plugin/plugin.json   Claude Code plugin manifest
.codex-plugin/plugin.json    Codex plugin manifest (points "skills" at ./skills/)
.mcp.json                    Bundled MCP server connection (chromatik, over HTTP)
LICENSE                      Copy of the repo LICENSE — the tarball ships standalone
agents/                      chromatik-reviewer, project-surveyor
commands/                    /chromatik-review, /chromatik-learn
skills/driving-chromatik/    House rules for driving a live Chromatik
skills/project-profile/      The project-profile document format and how to derive one
```

Both ecosystem manifests live at the plugin root, one directory apart (`.claude-plugin/`
and `.codex-plugin/`) — Claude Code ignores manifest fields it doesn't recognize, so a
single `agent-plugin/` tree serves both toolchains without duplication. Component
directories (`skills/`, `agents/`, `commands/`) live at the plugin root, never inside
either dotted manifest directory.

## Working on the plugin

`skills/driving-chromatik/SKILL.md` is hand-written and is the single source of truth for
the driving house rules — there is no generated copy of it anywhere, so edit it directly.

Every tool-shaped name referenced anywhere under `agent-plugin/` is checked against the
live tool catalog in CI, so a tool rename can't silently orphan a reference in the skill.
See [docs/development.md](../docs/development.md) for that gate and the rest of the
build.
