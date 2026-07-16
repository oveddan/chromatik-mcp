# Java MCP SDK feasibility (PR-1a)

## TL;DR — **GO**

The official Java MCP SDK embeds cleanly inside the long-running LX JVM and serves MCP
over streamable-HTTP. This is verified two ways, both green in CI:

1. **In-process embed test** — start the server on an ephemeral port, connect a real MCP
   client, complete the `initialize` handshake, assert the server identifies as `LX-MCP`.
   (`EmbeddedMcpServerTest`, runs under `mvn package`.)
2. **Live load gate** — the shaded package jar, loaded by LX's own `LXClassLoader` in a
   headless boot, starts the server: `[LX-MCP] MCP server listening on port <ephemeral>`.
   (`scripts/verify-build.sh --load`.)

No architectural blocker. The one wrinkle (fat-jar slimming) is a PR-2 polish item, not a
gate failure. Proceed to PR-1b / PR-1c.

## What was decided

| Question | Answer |
| --- | --- |
| SDK coordinates | `io.modelcontextprotocol.sdk:mcp` (bundle: core + Jackson 3, both server & client) |
| Version | **`2.0.0-RC1`** (latest published; matches 2.0 source; needs Java 17+, we run 21) |
| Transport | `HttpServletStreamableServerTransportProvider` (streamable-HTTP) — a Jakarta `HttpServlet` |
| Container | Embedded **Tomcat** (`tomcat-embed-core` 11.0.2) — the SDK's own example pattern |
| Owns main thread? | **No.** `Tomcat.start()` returns immediately; the listener runs on Tomcat threads |
| In-process testable? | **Yes** — client + server in one JVM, ephemeral port, no external process |
| Port discovery | `~/.lx-mcp/status.json` `{pid, port, projectPath, lxVersion}`, written on startup |

## Embedding pattern

The mutation lives in one composable primitive, [`EmbeddedMcpServer`](../package/src/main/java/lxmcp/mcp/EmbeddedMcpServer.java),
kept LX-agnostic so it unit-tests in-process:

```java
EmbeddedMcpServer server = EmbeddedMcpServer.start("LX-MCP", "0.0.1", 0); // 0 = ephemeral
int port = server.port();
// ... server.stop() on teardown
```

Internally: build the streamable-HTTP transport (`.mcpEndpoint("/mcp")`), wrap it in
`McpServer.sync(transport).serverInfo(...).build()`, host the servlet on embedded Tomcat
bound to port 0, and read back `getConnector().getLocalPort()`. The plugin
([`LxMcpPlugin`](../package/src/main/java/lxmcp/LxMcpPlugin.java)) calls this from
`initialize(lx)` and writes the status file — nothing blocks LX startup.

Client side (used by the embed test, and the shape any MCP platform uses):

```java
var transport = HttpClientStreamableHttpTransport
    .builder("http://127.0.0.1:" + port).endpoint("/mcp").build();
McpSyncClient client = McpClient.sync(transport).build();
client.initialize();   // -> InitializeResult; serverInfo().name() == "LX-MCP"
```

## Open questions — resolved

- **Streamable-HTTP at the maturity we need?** Yes. The SDK ships a dedicated
  `HttpServletStreamableServerTransportProvider` (not just the older SSE transport) and a
  matching `HttpClientStreamableHttpTransport` client. Round-trip verified.
- **Embeddable in a long-running JVM without owning main?** Yes — confirmed live inside
  headless LX. Tomcat runs its own threads; `initialize()` returns immediately.
- **Embedding shape?** The `EmbeddedMcpServer.start(...)` primitive above; ~40 lines.
- **Port-discovery handshake?** Confirmed: `~/.lx-mcp/status.json` with
  `{pid, port, projectPath, lxVersion}`, written by [`StatusFile`](../package/src/main/java/lxmcp/mcp/StatusFile.java).

## Packaging note (carry into PR-2, not a blocker)

LX loads a package as a single jar and provides none of these deps, so the build bundles
them with `maven-shade-plugin` (≈9 MB jar). During LX's eager class-scan, an optional
transitive Tomcat reference (`jakarta.mail.Authenticator`) logs a non-fatal
`ClassNotFoundException` — the plugin still initializes and the server still binds. PR-2
should slim the shaded jar (drop unused Tomcat submodules / optional deps) to remove the
noise and shrink the artifact.

## How to reproduce

```sh
cd package
mvn package              # compiles + runs the in-process embed test
scripts/verify-build.sh --load   # boots headless LX, confirms the server starts in-process
```
