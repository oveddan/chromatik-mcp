package lxmcp.mcp;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;

/**
 * Embedded streamable-HTTP MCP server, hosted on an embedded Tomcat listener.
 *
 * <p>The MCP SDK's streamable-HTTP transport is a Jakarta {@code HttpServlet}, so it
 * needs a servlet container. Embedded Tomcat runs the listener on its own threads —
 * {@link Tomcat#start()} returns immediately, so this can be started from
 * {@code LXPlugin.initialize(lx)} without blocking LX's main thread.
 *
 * <p>This primitive is intentionally LX-agnostic (it takes a name/version/port, not an
 * {@code LX}) so it can be unit-tested in-process. The plugin layer wires in the
 * LX-specific concerns (status file, project path).
 */
public final class EmbeddedMcpServer {

  /** Path the MCP endpoint is mounted at; clients connect to {@code http://host:port/mcp}. */
  public static final String ENDPOINT = "/mcp";

  private final Tomcat tomcat;
  private final McpSyncServer server;
  private final int port;

  private EmbeddedMcpServer(Tomcat tomcat, McpSyncServer server, int port) {
    this.tomcat = tomcat;
    this.server = server;
    this.port = port;
  }

  /**
   * Start the server on {@code requestedPort} (use {@code 0} for an ephemeral port).
   *
   * @return a handle exposing the actual bound {@link #port()} and {@link #stop()}.
   */
  public static EmbeddedMcpServer start(String serverName, String version, int requestedPort) {
    HttpServletStreamableServerTransportProvider transport =
        HttpServletStreamableServerTransportProvider.builder()
            .mcpEndpoint(ENDPOINT)
            .build();

    McpSyncServer server = McpServer.sync(transport)
        .serverInfo(serverName, version)
        .build();

    Tomcat tomcat = new Tomcat();
    tomcat.setPort(requestedPort);
    String baseDir = System.getProperty("java.io.tmpdir");
    tomcat.setBaseDir(baseDir);

    Context context = tomcat.addContext("", baseDir);
    Wrapper wrapper = context.createWrapper();
    wrapper.setName("mcp");
    wrapper.setServlet(transport);
    wrapper.setLoadOnStartup(1);
    wrapper.setAsyncSupported(true);
    context.addChild(wrapper);
    context.addServletMappingDecoded("/*", "mcp");

    // Touch the connector before start so setPort(0) yields an ephemeral bind.
    tomcat.getConnector().setAsyncTimeout(30_000);

    try {
      tomcat.start();
    } catch (LifecycleException e) {
      throw new IllegalStateException("Failed to start embedded MCP server", e);
    }

    int boundPort = tomcat.getConnector().getLocalPort();
    return new EmbeddedMcpServer(tomcat, server, boundPort);
  }

  /** The actual TCP port the server bound to. */
  public int port() {
    return this.port;
  }

  /**
   * Stop the MCP server and tear down the Tomcat listener.
   *
   * <p>Shutdown failures propagate (wrapped unchecked) so the plugin's {@code dispose()}
   * — itself wrapped by LX's error handling — reports them rather than hiding them.
   */
  public void stop() {
    this.server.closeGracefully();
    try {
      this.tomcat.stop();
      this.tomcat.destroy();
    } catch (LifecycleException e) {
      throw new IllegalStateException("Failed to stop embedded MCP server", e);
    }
  }
}
