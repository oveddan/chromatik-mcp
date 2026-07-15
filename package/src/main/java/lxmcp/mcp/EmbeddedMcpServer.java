package lxmcp.mcp;

import java.util.List;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

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

  /** Start with no tools registered — {@code tools/list} works and returns an empty list. */
  public static EmbeddedMcpServer start(String serverName, String version, int requestedPort) {
    return start(serverName, version, requestedPort, List.of(), null);
  }

  /**
   * Start the server on {@code requestedPort} (use {@code 0} for an ephemeral port),
   * registering {@code tools}.
   *
   * <p>The tools capability is advertised explicitly (even for an empty list) so a
   * connected client's {@code tools/list} succeeds rather than failing as an unsupported
   * method. {@code listChanged} is {@code false}: the tool set is fixed at startup.
   *
   * @return a handle exposing the actual bound {@link #port()} and {@link #stop()}.
   */
  public static EmbeddedMcpServer start(
      String serverName,
      String version,
      int requestedPort,
      List<McpServerFeatures.SyncToolSpecification> tools) {
    return start(serverName, version, requestedPort, tools, null);
  }

  /**
   * Start the server, additionally setting the MCP {@code instructions} string returned in
   * the initialize result. {@code instructions} may be {@code null} to omit it. Plain
   * {@code String} — this class stays LX-agnostic, so callers own the content.
   */
  public static EmbeddedMcpServer start(
      String serverName,
      String version,
      int requestedPort,
      List<McpServerFeatures.SyncToolSpecification> tools,
      String instructions) {
    // The SDK resolves its JSON mapper + schema validator via ServiceLoader on the
    // thread-context classloader, eagerly at builder time. Inside Chromatik this jar
    // lives in a child classloader (LXClassLoader) that is never the TCCL, so without
    // this swap initialize() dies with ServiceConfigurationError — while every test
    // passes, because tests have the SDK on the system classpath. The SDK memoizes the
    // resolved instances, so the swap is only needed for the duration of startup.
    Thread thread = Thread.currentThread();
    ClassLoader prior = thread.getContextClassLoader();
    thread.setContextClassLoader(EmbeddedMcpServer.class.getClassLoader());
    try {
      return startWithContextClassLoader(serverName, version, requestedPort, tools, instructions);
    } finally {
      thread.setContextClassLoader(prior);
    }
  }

  private static EmbeddedMcpServer startWithContextClassLoader(
      String serverName,
      String version,
      int requestedPort,
      List<McpServerFeatures.SyncToolSpecification> tools,
      String instructions) {
    HttpServletStreamableServerTransportProvider transport =
        HttpServletStreamableServerTransportProvider.builder()
            .mcpEndpoint(ENDPOINT)
            .build();

    McpServer.SyncSpecification<?> serverSpec = McpServer.sync(transport)
        .serverInfo(serverName, version)
        .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
        .tools(tools);
    if (instructions != null) {
      serverSpec = serverSpec.instructions(instructions);
    }
    McpSyncServer server = serverSpec.build();

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

    // Loopback only: status.json discovery is inherently local, and the tool surface
    // mutates a live show — never an unauthenticated listener on the LAN. (Touching
    // the connector before start also makes setPort(0) yield an ephemeral bind. No
    // async timeout is set: the SDK servlet disables it via setTimeout(0), so the
    // EngineExecutor call timeout is the only bound on a blocked tool call.)
    tomcat.getConnector().setProperty("address", "127.0.0.1");

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
