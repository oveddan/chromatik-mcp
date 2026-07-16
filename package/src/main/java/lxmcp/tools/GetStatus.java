package lxmcp.tools;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import heronarts.lx.LX;

import lxmcp.ConnectionSnapshot;
import lxmcp.ServerStatus;

/**
 * Reports the embedded MCP server's own bind address, uptime, and live connection state
 * (open streams, last activity). A successful call is itself evidence the engine loop is
 * draining tasks — the handler runs on the engine thread like every other tool, so a
 * hung engine would time this call out rather than answer it.
 */
public final class GetStatus implements LxTool {

  private final ServerStatus status;
  private final Supplier<ConnectionSnapshot> snapshot;

  public GetStatus(ServerStatus status, Supplier<ConnectionSnapshot> snapshot) {
    this.status = status;
    this.snapshot = snapshot;
  }

  @Override
  public String name() {
    return "get_status";
  }

  @Override
  public String description() {
    return "The embedded MCP server's own state: bind host/port/url, when it started, "
        + "uptime, and live connection info (whether a client is currently connected, open "
        + "SSE stream count, last activity time). A successful call proves "
        + "the LX engine loop is draining tasks, since this handler runs on the engine "
        + "thread like every other tool.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Schemas.noArgs();
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    long now = System.currentTimeMillis();
    ConnectionSnapshot snap = this.snapshot.get();

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("host", this.status.host());
    payload.put("port", this.status.port());
    payload.put("url", this.status.url());
    payload.put("startedAt", Instant.ofEpochMilli(this.status.startedAtMs()).toString());
    payload.put("uptimeSeconds", (now - this.status.startedAtMs()) / 1000);

    Map<String, Object> connection = new LinkedHashMap<>();
    connection.put("connected", snap.connected());
    connection.put("activeStreams", snap.activeStreams());
    if (snap.lastActivityMs() != 0) {
      connection.put("lastActivityAt", Instant.ofEpochMilli(snap.lastActivityMs()).toString());
    }
    payload.put("connection", connection);

    return Result.ok(payload);
  }
}
