# chromatik-mcp — Docs

- Installing and connecting a client lives in the [README](../README.md); [migrating-from-lx-mcp.md](migrating-from-lx-mcp.md) covers upgrades from the pre-rename name.
- [architecture.md](architecture.md) — the contract an integrator builds against: connection, wire shape, addressing, threading, undo, state lifecycle.
- [development.md](development.md) — develop from source: build, test, the drift gates, repo layout, and the conventions a change has to hold to.
- Task recipes (discovery, structure, modulation, snapshots/views, arrange-timeline authoring) live with the driving skill: [agent-plugin/skills/driving-chromatik/references/recipes.md](../agent-plugin/skills/driving-chromatik/references/recipes.md).
- [tool-conventions.md](tool-conventions.md) — tool naming, canonical paths, wire shapes, threading, pagination, and mutation contracts.
- [catalog-format.md](catalog-format.md) — semantic component-documentation format and runtime lookup behavior.
- [osc-addressing.md](osc-addressing.md) — canonical paths versus OSC addresses and the label-based modulator exception.
- [lx-coding-guidelines.md](lx-coding-guidelines.md), [review-criteria.md](review-criteria.md) — implementation and review conventions.
- [live-findings.md](live-findings.md) — verified live gaps, queued follow-ups, and merged findings history.
- [build-plan.md](build-plan.md) — shipped milestones, current follow-ups, and historical PR plan.
- [loop-engineering.md](loop-engineering.md) — the repository's objective-gated agent development loop.
- [demo-script.md](demo-script.md) — live-demo beat sheet and dry-run checklist.
- [releasing.md](releasing.md) — how a release is cut (bump the pom version, merge; CI tags and publishes).
- [sdk-feasibility.md](sdk-feasibility.md), [lxcommand-mapping.md](lxcommand-mapping.md), [qa-strategy.md](qa-strategy.md) — spike-phase deliverables (PR-1a SDK feasibility, PR-1b LXCommand mapping, PR-1c QA strategy).
- Per-client connection setup (Claude Code, Claude Desktop, Cursor, VS Code, Codex, generic HTTP) lives on the docs site: [Connect your AI client](../site/src/content/docs/connect.mdx) (published at https://oveddan.github.io/chromatik-mcp/connect/). This supersedes the `install/` snippets dir planned in PR-6.
