---
title: Architecture
description: An MCP server embedded in the LX runtime — in-process mutation with undo, one jar, two filesystem touchpoints.
---

The jar embeds an HTTP MCP server (official Java MCP SDK, streamable-HTTP on embedded
Tomcat) inside the LX runtime as an `LXPlugin`. Any MCP-speaking client — Claude Code,
Claude Desktop, Cursor, Codex, custom orchestrators — connects to it directly and
calls tools that mutate LX state **in-process**. No separate Node server, no `.lxp`
file editing, no file watcher.

## Why in-process matters

Editing project files means the agent works on a dead snapshot: it can't see the live
engine, its changes need a reload, and it races the human at the console. Embedding
the server in the runtime means every tool call reads or mutates the same live object
graph the UI renders — and the human and the agent share one undo stack.

## Mutation through LXCommand

Mutations route through LX's own `LXCommand` system, so every change gets undo for
free — each agent edit is one Cmd-Z step at the console. Exceptions (swatch recall,
trigger fires, snapshot recall — an LX quirk where the undo entry captures post-recall
values) are called out in their tool descriptions. All calls are serialized onto the
LX engine thread, which makes concurrent agent sessions safe: calls interleave
atomically.

```
tool handler  ──> domain primitive  ──> LXCommand.perform(...)   (mutation with undo)
(MCP-shaped)     (intent, narrow)   ──> direct lx.engine.* edit  (mutation without undo)
                                    ──> read lx.engine.*         (read-only)
```

Tool handlers parse arguments and shape results; **domain primitives** are the only
layer that knows how a mutation is actually applied. If the implementation strategy
for an operation changes (a new `LXCommand` lands upstream, say), exactly one function
changes.

## The semantic catalog

`get_component_doc` serves generated behavior docs for stock LX patterns, effects, and
modulators — what a component renders, how its parameters interact. Entries are keyed
to **bytecode hashes**, so the tool can honestly flag `stale: true` when the code has
changed since the doc was written, and `list_available_*` responses carry `documented`
flags. The catalog exists because parameter trees tell an agent what knobs exist, not
what the algorithm does with them.

## Filesystem touchpoints

Exactly two, both under `~/.lx-mcp/`:

- **`status.json`** — written on startup for endpoint discovery: `{pid, port, host,
  url, projectPath, lxVersion, serverVersion, buildTime, connected, lastActivityAt}`.
- **`config.json`** (optional) — pin a fixed port or change the bind host.

Default bind is `127.0.0.1` only. There is no authentication layer, so non-loopback
binds are at your own risk — anyone who can reach the address has full control of the
show (a startup warning says as much).
