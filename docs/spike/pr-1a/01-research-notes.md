# PR-1a — research notes (verified facts)

Raw facts gathered for the SDK-feasibility spike. Every claim here was verified against
the actual SDK source (cloned) or Maven Central metadata — not model memory. An earlier
exploratory pass produced plausible-but-wrong guesses (e.g. `io.modelcontextprotocol:sdk`,
`HttpServerTransport`); those are corrected below.

## Sources

- Java MCP SDK repo, cloned at HEAD (`2.0.0-SNAPSHOT`, last commit 2026-06-04): <https://github.com/modelcontextprotocol/java-sdk>
- Maven Central metadata: `https://repo1.maven.org/maven2/io/modelcontextprotocol/sdk/mcp/maven-metadata.xml`
- Canonical embedded-servlet example in the repo: `conformance-tests/server-servlet/src/main/java/io/modelcontextprotocol/conformance/server/ConformanceServlet.java`
- LX source: `/Users/danoved/Source/LX/src/main/java/heronarts/lx/LX.java` (`VERSION = "1.2.1"`, `getProject()`, `LXClassLoader`)

## Coordinates and versions (Maven Central)

- Group ID: **`io.modelcontextprotocol.sdk`**
- Convenience bundle artifact: **`mcp`** (core + Jackson 3 binding; provides *both* server and client APIs)
- Other modules: `mcp-core`, `mcp-json-jackson2`, `mcp-json-jackson3`, `mcp-test`, `mcp-bom`
- Published versions: stable line up to **`1.1.3`**; latest published overall is **`2.0.0-RC1`** (`<latest>`/`<release>` in metadata). Milestones `2.0.0-M1..M3` precede it.
- Chose **`2.0.0-RC1`** — it matches the 2.0 API in the source we read and resolves from Maven Central. SDK 2.0 targets Java 17+; our build is Java 21 (compatible).

## Transport classes (in `mcp-core`, bundled by `mcp`)

Server, package `io.modelcontextprotocol.server.transport`:
- `HttpServletStreamableServerTransportProvider` — streamable-HTTP, **extends `jakarta.servlet.HttpServlet`**, has a `builder()` (`mcpEndpoint`, `keepAliveInterval`, `securityValidator`, `disallowDelete`, `contextExtractor`, `jsonMapper`).
- `HttpServletSseServerTransportProvider` — older HTTP+SSE transport.
- `StdioServerTransportProvider` — stdio.

Client, package `io.modelcontextprotocol.client.transport`:
- `HttpClientStreamableHttpTransport` — JDK-`HttpClient`-based; `builder(String baseUri).endpoint("/mcp").build()`.

Because the transport is a Jakarta servlet, it needs a servlet container. The repo's own
example hosts it on **embedded Tomcat** (`org.apache.tomcat.embed:tomcat-embed-core`,
v`11.0.2`, Jakarta Servlet API `6.1.0`). `Tomcat.start()` returns immediately (the listener
runs on Tomcat's own threads); only `getServer().await()` blocks — which we skip.

## Server / client builder API (verified signatures)

- Server: `McpServer.sync(transportProvider).serverInfo(String name, String version).build()` → `McpSyncServer`. `serverInfo` and `capabilities` are the only commonly-set fields; both have defaults. `McpSyncServer` has `getServerInfo()`, `closeGracefully()`, `close()`.
- Client: `McpClient.sync(McpClientTransport).build()` → `McpSyncClient`; `client.initialize()` → `McpSchema.InitializeResult`.
- Records: `InitializeResult` exposes `serverInfo()` → `McpSchema.Implementation`, which exposes `name()` / `version()`.

## Embedding pattern (from ConformanceServlet, condensed)

```java
var transport = HttpServletStreamableServerTransportProvider.builder()
    .mcpEndpoint("/mcp").build();                 // a jakarta HttpServlet
var server = McpServer.sync(transport)
    .serverInfo("LX-MCP", "0.0.1").build();
Tomcat tomcat = new Tomcat();
tomcat.setPort(0);                                // 0 = ephemeral
tomcat.setBaseDir(System.getProperty("java.io.tmpdir"));
Context ctx = tomcat.addContext("", baseDir);
Wrapper w = ctx.createWrapper();
w.setName("mcp"); w.setServlet(transport); w.setAsyncSupported(true);
ctx.addChild(w); ctx.addServletMappingDecoded("/*", "mcp");
tomcat.getConnector();                            // realize connector before start
tomcat.start();                                   // returns immediately
int port = tomcat.getConnector().getLocalPort();  // actual bound port
```

## In-process testability

Yes. A JUnit test starts the server on port 0, builds `HttpClientStreamableHttpTransport`
against `http://127.0.0.1:<port>`, and calls `client.initialize()`. No external process,
no network fixture. (`mcp-test` exists for richer harnesses but is not needed for the
initialize round-trip.)

## LX packaging constraint (discovered)

LX loads a package as a **single jar** via `LXClassLoader` (`loadJarFile` eagerly defines
every class entry). LX provides none of the MCP/Tomcat deps, so they must be **bundled**
(fat jar via `maven-shade-plugin`). Observed wrinkle: LX's eager class-defining logs a
non-fatal `ClassNotFoundException: jakarta.mail.Authenticator` (an optional transitive
reference inside Tomcat) — the plugin still initializes and the server still binds. Flag
for PR-2: slim the shaded jar (exclude unused Tomcat submodules / optional deps).
