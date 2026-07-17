package chromatikmcp;

/**
 * Immutable point-in-time read of {@link chromatikmcp.mcp.ConnectionTracker} state. Lives in the
 * root package (not {@code chromatikmcp.mcp}) so tool handlers can depend on it without reaching
 * into MCP transport plumbing (CLAUDE.md layering) — {@code mcp} classes may depend on
 * root types, never the reverse.
 */
public record ConnectionSnapshot(int activeStreams, long lastActivityMs, boolean connected) {}
