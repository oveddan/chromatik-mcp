# agent-plugin

This directory is the agent-facing packaging of chromatik-mcp: a Claude Code / Codex
plugin that bundles the MCP server connection and the house rules for driving a live
Chromatik well. It is **not** user-facing install documentation — that lives on the docs
site (`site/src/content/docs/`), added in a later PR. This README is for people (and
agents) working on the plugin itself.

## What's here

```
.claude-plugin/plugin.json   Claude Code plugin manifest
.codex-plugin/plugin.json    Codex plugin manifest (points "skills" at ./skills/)
.mcp.json                    Bundled MCP server connection (chromatik, over HTTP)
LICENSE                      Copy of the repo LICENSE — the tarball ships standalone
skills/driving-chromatik/    The one skill this plugin currently carries
```

Both ecosystem manifests live at the plugin root, one directory apart
(`.claude-plugin/` and `.codex-plugin/`) — Claude Code ignores manifest fields it doesn't
recognize, so a single `agent-plugin/` tree serves both toolchains without duplication.
Component directories (`skills/`, and future `agents/`, `commands/`) live at the plugin
root, never inside either dotted manifest directory.

## Dogfooding locally

Claude Code auto-loads any `~/.claude/skills/<name>/` directory containing a
`.claude-plugin/plugin.json` as `<name>@skills-dir` on the next session — no marketplace,
no install command. To try this plugin without installing from a registry:

```sh
ln -s "$(pwd)/agent-plugin" ~/.claude/skills/chromatik
# or, if you'd rather not symlink:
cp -R agent-plugin ~/.claude/skills/chromatik
```

Restart Claude Code and the `driving-chromatik` skill should be available.

## `SKILL.md` is the source; `driving.md` is generated

`skills/driving-chromatik/SKILL.md` is hand-written and is the single source of truth for
the driving house rules. `site/src/content/docs/driving.md` (the docs-site page of the
same name) is **generated** from it — `site/scripts/generate-driving-page.mjs` reads
`SKILL.md`, strips its frontmatter, and writes the body between
`<!-- generated:start:driving -->` / `<!-- generated:end -->` markers in `driving.md`.
Run `npm run driving-ref` (from `site/`) after editing `SKILL.md`; `npm run driving-ref --
--check` (or however your shell passes flags through) fails loudly if the page has
drifted. Edit `SKILL.md`, never the generated block in `driving.md` directly — anything
outside the markers (the page's Starlight frontmatter and page-only framing prose) is
untouched by the generator and is fine to hand-edit.

## Unverified items

- **`skills/driving-chromatik/agents/openai.yaml`'s location is unverified against Codex
  documentation.** It may belong at the plugin root instead of nested under the skill —
  this placement is a best-effort guess pending confirmation from Codex's own plugin
  docs.
- The `${CHROMATIK_MCP_PORT:-3232}` shell-style default-value expansion in this
  directory's `.mcp.json` is a Claude Code feature; Codex's own MCP dependency
  declaration (`skills/driving-chromatik/agents/openai.yaml`) uses a literal port instead
  for that reason.
