package lxmcp.mcp;

import java.util.List;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

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
  private final ConnectionTracker connectionTracker;

  private EmbeddedMcpServer(
      Tomcat tomcat, McpSyncServer server, int port, ConnectionTracker connectionTracker) {
    this.tomcat = tomcat;
    this.server = server;
    this.port = port;
    this.connectionTracker = connectionTracker;
  }

  /** Start with no tools registered — {@code tools/list} works and returns an empty list. */
  public static EmbeddedMcpServer start(String serverName, String version, int requestedPort) {
    return start(serverName, version, requestedPort, "127.0.0.1", List.of(), null);
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
    return start(serverName, version, requestedPort, "127.0.0.1", tools, null);
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
    return start(serverName, version, requestedPort, "127.0.0.1", tools, instructions);
  }

  /**
   * Start the server, binding {@code host} instead of the loopback-only default. Callers
   * outside tests should think hard before passing anything but a loopback address — see
   * the security comment at the connector below. Constructs its own {@link
   * ConnectionTracker}; use the overload taking one explicitly to observe it before
   * {@code start()} returns.
   */
  public static EmbeddedMcpServer start(
      String serverName,
      String version,
      int requestedPort,
      String host,
      List<McpServerFeatures.SyncToolSpecification> tools,
      String instructions) {
    return start(serverName, version, requestedPort, host, tools, instructions, new ConnectionTracker());
  }

  /**
   * Same as the five-arg {@code host} overload, but takes the {@link ConnectionTracker}
   * to register as the request filter instead of constructing one internally. Lets a
   * caller (the plugin) hold the tracker before {@code start()} returns, so a {@code
   * get_status} supplier can close over the tracker directly rather than over this
   * server's own field — see the "supplier NPE window" note in {@code LxMcpPlugin}.
   */
  public static EmbeddedMcpServer start(
      String serverName,
      String version,
      int requestedPort,
      String host,
      List<McpServerFeatures.SyncToolSpecification> tools,
      String instructions,
      ConnectionTracker connectionTracker) {
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
      return startWithContextClassLoader(
          serverName, version, requestedPort, host, tools, instructions, connectionTracker);
    } finally {
      thread.setContextClassLoader(prior);
    }
  }

  private static EmbeddedMcpServer startWithContextClassLoader(
      String serverName,
      String version,
      int requestedPort,
      String host,
      List<McpServerFeatures.SyncToolSpecification> tools,
      String instructions,
      ConnectionTracker connectionTracker) {
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

    FilterDef filterDef = new FilterDef();
    filterDef.setFilterName("connection-tracker");
    filterDef.setFilter(connectionTracker);
    filterDef.setAsyncSupported("true");
    context.addFilterDef(filterDef);
    FilterMap filterMap = new FilterMap();
    filterMap.setFilterName("connection-tracker");
    filterMap.addURLPattern("/*");
    context.addFilterMap(filterMap);

    // Loopback by default: status.json discovery is inherently local, and the tool
    // surface mutates a live show — an unauthenticated listener should never reach the
    // LAN without an explicit, logged opt-in (see ConfigFile / LxMcpPlugin). Touching
    // the connector before start also makes setPort(0) yield an ephemeral bind. No
    // async timeout is set: the SDK servlet disables it via setTimeout(0), so the
    // EngineExecutor call timeout is the only bound on a blocked tool call.
    tomcat.getConnector().setProperty("address", host);

    try {
      tomcat.start();
    } catch (LifecycleException e) {
      throw new IllegalStateException("Failed to start embedded MCP server", e);
    }

    // Tomcat's StandardService swallows connector bind failures (logs and continues;
    // start() returns normally), so a fixed port already in use, or a host address this
    // machine can't bind, silently yields an unbound connector rather than an exception.
    // getLocalPort() == -1 is that failure's only signal — surface it as a real error
    // rather than reporting "listening on port -1" as healthy.
    int boundPort = tomcat.getConnector().getLocalPort();
    if (boundPort == -1) {
      // Tomcat is still "started" from its own point of view even though the connector
      // never bound — leaving it running here would leak it (a caller that retries
      // start() on a different port stacks up orphaned Tomcat instances). Best-effort
      // cleanup; a failure here must not mask the original bind failure being reported.
      try {
        server.closeGracefully();
      } catch (RuntimeException ignored) {
        // best-effort
      }
      try {
        tomcat.stop();
        tomcat.destroy();
      } catch (LifecycleException | RuntimeException ignored) {
        // best-effort
      }
      throw new IllegalStateException(
          "Embedded MCP server failed to bind host=" + host + " port=" + requestedPort
              + " — the port is likely already in use, or the host address is unassignable "
              + "on this machine.");
    }
    return new EmbeddedMcpServer(tomcat, server, boundPort, connectionTracker);
  }

  /** The actual TCP port the server bound to. */
  public int port() {
    return this.port;
  }

  /** Observed client-activity state (open streams, last activity). */
  public ConnectionTracker connectionTracker() {
    return this.connectionTracker;
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
