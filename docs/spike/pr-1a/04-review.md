# PR-1a — review

**Verdict: PASS (= GO).**

## Open questions — each has a defended, verified answer

| Question | Answer | Evidence |
| --- | --- | --- |
| Streamable-HTTP at needed maturity + version? | Yes; `mcp:2.0.0-RC1` ships `HttpServletStreamableServerTransportProvider` + `HttpClientStreamableHttpTransport` | Read from cloned SDK source; round-trip test green |
| Embeddable without owning main thread? | Yes; Tomcat starts on its own threads, `initialize()` returns | Live headless load gate: server bound while LX continued boot |
| Embedding shape? | `EmbeddedMcpServer.start(name, version, port)` primitive | `package/src/main/java/lxmcp/mcp/EmbeddedMcpServer.java` |
| In-process testable? | Yes | `EmbeddedMcpServerTest` passes under `mvn package` |
| Port-discovery handshake? | `~/.lx-mcp/status.json {pid,port,projectPath,lxVersion}` | `StatusFile` + wired in `LxMcpPlugin.initialize` |

## Claims spot-checked against research notes

- Coordinates `io.modelcontextprotocol.sdk:mcp` and version set (`1.1.3` stable, `2.0.0-RC1`
  latest) confirmed against Maven Central metadata — corrected the earlier exploratory
  guess (`io.modelcontextprotocol:sdk` / `HttpServerTransport`), which was wrong.
- Embedding pattern matches the SDK's own `conformance-tests/server-servlet` example.
- No unsourced assertions remain in `sdk-feasibility.md`; every capability claim maps to a
  green test or a cited source.

## Hidden assumptions surfaced (none blocking)

- **Fat-jar packaging.** LX provides none of the deps, so the jar is shaded (~9 MB). A
  non-fatal `ClassNotFoundException: jakarta.mail.Authenticator` is logged during LX's
  eager class-scan (optional Tomcat transitive). Documented as a PR-2 slimming item; does
  not affect server startup. **Surfaced, not worked around silently.**
- **RC vs stable.** Chose `2.0.0-RC1` to match the source we read. If 2.0 stabilizes with
  API drift, the single `mcp.version` property is the only change point.

## Docs-sync audit

- `docs/build-plan.md` — PR-1a tracker line updated to `[~]` GO with findings. ✔
- `package/README.md` — stale "contributes nothing yet" status corrected. ✔
- `CLAUDE.md` — re-read; its `lxmcp/mcp/*` layering description already matches what landed
  (`EmbeddedMcpServer`, `StatusFile`); QA-strategy reference still points at PR-1c. No
  change needed. ✔
