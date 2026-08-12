---
title: Install the agent plugin
description: Install the chromatik agent plugin for Claude Code or Codex — it bundles the MCP connection, the driving skill, the reviewer agent, and the /chromatik-learn and /chromatik-review commands.
---

[Connect your AI client](../connect/) gets you the raw MCP connection: the tools work,
and that's enough. The **agent plugin** adds everything around them — the house rules for
driving a live show, the project-profile workflow, a code reviewer, and slash commands —
so you don't have to paste context in yourself.

## What's in it

| component | what it does |
|---|---|
| `driving-chromatik` skill | House rules for driving a live Chromatik: connecting and recovering from restarts, canonical-path addressing, timeout and error semantics, consulting component docs before reasoning about a pattern, and the mutate → look → adjust verification loop. Published here as [Driving Chromatik well](../driving/) |
| `project-profile` skill | The project-profile document format and the rules for deriving one from a live show |
| `project-surveyor` agent | Surveys a running project and writes a profile |
| `chromatik-reviewer` agent | Reviews chromatik-mcp changes against the project's conventions |
| `/chromatik-learn` | Builds a project profile from a live instance |
| `/chromatik-review` | Runs the reviewer over a branch |
| bundled MCP server | The `chromatik` HTTP connection, so you don't run `claude mcp add` separately |

The skill also carries three reference files it loads on demand rather than up front —
`addressing.md`, `error-codes.md`, and `composition.md` (the arrange timeline). Keeping
them out of the always-loaded skill body is deliberate: an agent that never touches a clip
shouldn't pay for the timeline rules on every session.

## Claude Code

The plugin isn't published to a marketplace yet, so install it from a clone. Claude Code
auto-loads any `~/.claude/skills/<name>/` directory that contains a
`.claude-plugin/plugin.json`, as `<name>@skills-dir` — no install command needed:

```sh
git clone https://github.com/oveddan/chromatik-mcp.git
ln -s "$(pwd)/chromatik-mcp/agent-plugin" ~/.claude/skills/chromatik
```

Or copy it, if you'd rather not have a symlink into a clone you might move:

```sh
cp -R chromatik-mcp/agent-plugin ~/.claude/skills/chromatik
```

**Restart Claude Code.** A running session won't pick it up. Then check that
`driving-chromatik` appears in your available skills, and that `/chromatik-learn` and
`/chromatik-review` are offered.

:::note[Pin the port]
The bundled MCP connection uses `http://127.0.0.1:${CHROMATIK_MCP_PORT:-3232}/mcp` — it
defaults to port **3232** rather than reading `status.json`, because a plugin manifest
can't run `jq`. Pin the port to match by putting `{"port": 3232}` in
`~/.chromatik-mcp/config.json` and restarting Chromatik, or set `CHROMATIK_MCP_PORT` in
your environment. Otherwise the connection lands on the wrong ephemeral port. See
[Getting started](../getting-started/#configuring-the-port-and-host).
:::

## Codex

The same directory carries a Codex manifest at `.codex-plugin/plugin.json`, which
declares `"skills": "./skills/"` — both skills come across. Install it the way your Codex
version expects to pick up a local plugin directory, pointing at the same
`agent-plugin/` tree.

:::caution[Partly unverified]
The Codex side is best-effort and not fully confirmed against Codex's own plugin docs:

- `skills/driving-chromatik/agents/openai.yaml` may belong at the plugin root rather than
  nested under the skill.
- The Codex manifest declares only `skills`. Whether Codex has an equivalent mechanism for
  the `agents/` and `commands/` directories is unverified — assume for now that you get
  the skills but not the reviewer agent or the slash commands.
- Codex's MCP dependency declaration uses a **literal** port instead of the
  `${CHROMATIK_MCP_PORT:-3232}` default-value expansion, which is a Claude Code feature.

If you've confirmed any of these either way, please
[open an issue](https://github.com/oveddan/chromatik-mcp/issues).
:::

## Any other MCP client

Clients without a plugin system still get the tools — connect the server directly per
[Connect your AI client](../connect/), and point the agent at
[Driving Chromatik well](../driving/) (or at
[`llms-full.txt`](https://oveddan.github.io/chromatik-mcp/llms-full.txt), which carries
the whole site as plain markdown) so it has the house rules in context.

## Working on the plugin itself

`agent-plugin/skills/driving-chromatik/SKILL.md` is the source of truth for the driving
rules; the [Driving Chromatik well](../driving/) page is generated from it and gated
against drift in CI. See [development.md](https://github.com/oveddan/chromatik-mcp/blob/main/docs/development.md)
for the generators and the gates.
