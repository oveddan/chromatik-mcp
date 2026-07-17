package chromatikmcp;

import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.MutableParameter;

/**
 * Live server-lifecycle state shared by the plugin, the {@code get_status} tool, and a
 * future UI. {@link #connected} and {@link #lastActivityMs} are updated by the plugin's
 * engine loop task from {@link ConnectionSnapshot} reads; host/port/startedAt are set
 * once, right after the embedded server binds.
 *
 * <p>Lives in the root package (not {@code chromatikmcp.mcp}) per CLAUDE.md's layering: the
 * plugin passes in the endpoint path string at {@link #initialize} time rather than this
 * class reaching into {@code chromatikmcp.mcp.EmbeddedMcpServer} for it.
 */
public final class ServerStatus {

  public final BooleanParameter connected = new BooleanParameter("Connected", false)
      .setDescription("Whether an MCP client has been active within the last 60s or holds an open stream");

  public final MutableParameter lastActivityMs = new MutableParameter("LastActivity", 0);

  private volatile String host;
  private volatile int port;
  private volatile long startedAtMs;
  private volatile String endpoint;

  /** Set once by the plugin after the embedded server has bound its port. */
  public void initialize(String host, int port, long startedAtMs, String endpoint) {
    this.host = host;
    this.port = port;
    this.startedAtMs = startedAtMs;
    this.endpoint = endpoint;
  }

  public String host() {
    return this.host;
  }

  public int port() {
    return this.port;
  }

  public long startedAtMs() {
    return this.startedAtMs;
  }

  /** {@code http://host:port/mcp} — the address MCP clients connect to. */
  public String url() {
    return "http://" + this.host + ":" + this.port + this.endpoint;
  }
}
