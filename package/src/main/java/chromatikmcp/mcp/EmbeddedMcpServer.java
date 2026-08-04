package chromatikmcp.mcp;

import java.util.List;
import java.util.Map;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.filters.SetCharacterEncodingFilter;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

import jakarta.servlet.http.HttpServlet;

import io.modelcontextprotocol.json.McpJsonDefaults;
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
   * server's own field — see the "supplier NPE window" note in {@code ChromatikMcpPlugin}.
   */
  public static EmbeddedMcpServer start(
      String serverName,
      String version,
      int requestedPort,
      String host,
      List<McpServerFeatures.SyncToolSpecification> tools,
      String instructions,
      ConnectionTracker connectionTracker) {
    return start(
        serverName, version, requestedPort, host, tools, instructions, connectionTracker,
        Map.of());
  }

  /**
   * Same as the seven-arg {@code connectionTracker} overload, but additionally mounts
   * {@code extraServlets} (path → servlet, e.g. {@code "/osc-params"}) alongside the MCP
   * endpoint on the same Tomcat listener and port. Each extra servlet is registered with
   * an exact-path mapping, which Tomcat's servlet-mapping precedence rules resolve ahead
   * of the MCP wrapper's {@code /*} wildcard mapping — so {@code /mcp} traffic is
   * unaffected by anything mounted here.
   */
  public static EmbeddedMcpServer start(
      String serverName,
      String version,
      int requestedPort,
      String host,
      List<McpServerFeatures.SyncToolSpecification> tools,
      String instructions,
      ConnectionTracker connectionTracker,
      Map<String, HttpServlet> extraServlets) {
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
          serverName, version, requestedPort, host, tools, instructions, connectionTracker,
          extraServlets);
    } finally {
      thread.setContextClassLoader(prior);
    }
  }

  /**
   * {@link RewordingJsonSchemaValidator} rewrites every "structuredContent does not match
   * tool outputSchema" message to input-schema wording, on the assumption that no tool
   * here declares an {@code outputSchema} — so every failure the shared validator instance
   * can produce is actually an input-argument failure. If a tool ever declares one, that
   * assumption breaks silently: a genuine output-serialization bug would be rewritten into
   * "arguments do not match the tool's input schema," blaming the client's call for a
   * server-side defect. Fail fast at startup instead.
   *
   * <p>This guard inspects only tools registered at {@code start(...)};
   * dynamically-added tools via {@code McpServer.addTool(...)} would bypass this check.
   */
  private static void requireNoOutputSchemas(List<McpServerFeatures.SyncToolSpecification> tools) {
    for (McpServerFeatures.SyncToolSpecification spec : tools) {
      if (spec.tool().outputSchema() != null) {
        throw new IllegalStateException(
            "Tool '" + spec.tool().name() + "' declares an outputSchema, but "
                + RewordingJsonSchemaValidator.class.getSimpleName()
                + " rewrites all validator failures — including genuine output-schema "
                + "failures — into input-argument wording. Revisit that wrapper (see its "
                + "class doc) before any tool declares an outputSchema.");
      }
    }
  }

  private static EmbeddedMcpServer startWithContextClassLoader(
      String serverName,
      String version,
      int requestedPort,
      String host,
      List<McpServerFeatures.SyncToolSpecification> tools,
      String instructions,
      ConnectionTracker connectionTracker,
      Map<String, HttpServlet> extraServlets) {
    requireNoOutputSchemas(tools);

    HttpServletStreamableServerTransportProvider transport =
        HttpServletStreamableServerTransportProvider.builder()
            .mcpEndpoint(ENDPOINT)
            .build();

    McpServer.SyncSpecification<?> serverSpec = McpServer.sync(transport)
        .serverInfo(serverName, version)
        .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
        .tools(tools)
        // Upstream RC-only wording bug (issue #115): DefaultJsonSchemaValidator.validate()
        // (mcp-json-jackson3 2.0.0-RC1) hardcodes an "outputSchema"/"structuredContent"
        // message for every validation failure, including ToolInputValidator's checks of a
        // tool call's arguments — misleading, since no tool here declares an outputSchema
        // (docs/tool-conventions.md), so every such failure is actually an input-argument
        // failure. RewordingJsonSchemaValidator wraps the SDK's own default (resolved here,
        // inside the TCCL swap above, same as the SDK's own lazy resolution would use) and
        // only rewords that hardcoded phrase. Delete this wrapper at the SDK GA bump
        // (docs/build-plan.md follow-up, targeted for PR-6).
        .jsonSchemaValidator(new RewordingJsonSchemaValidator(McpJsonDefaults.getSchemaValidator()));
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

    // The SDK reads JSON through request.getReader(). Servlet containers otherwise
    // default a request without an explicit charset to ISO-8859-1, even though JSON is
    // UTF-8 on the wire. Force the MCP endpoint to UTF-8 before the SDK obtains its
    // reader; this covers clients that send the common bare application/json content
    // type and prevents non-ASCII tool arguments from becoming mojibake.
    SetCharacterEncodingFilter encodingFilter = new SetCharacterEncodingFilter();
    encodingFilter.setEncoding("UTF-8");
    encodingFilter.setIgnore(true);
    FilterDef encodingFilterDef = new FilterDef();
    encodingFilterDef.setFilterName("mcp-utf8");
    encodingFilterDef.setFilter(encodingFilter);
    encodingFilterDef.setAsyncSupported("true");
    context.addFilterDef(encodingFilterDef);
    FilterMap encodingFilterMap = new FilterMap();
    encodingFilterMap.setFilterName("mcp-utf8");
    encodingFilterMap.addURLPattern(ENDPOINT);
    encodingFilterMap.addURLPattern(ENDPOINT + "/*");
    context.addFilterMap(encodingFilterMap);

    // Exact-path mappings (e.g. "/osc-params") outrank the "/*" wildcard mapping above
    // per the servlet spec's mapping-precedence rules, so these never shadow /mcp.
    int extraServletIndex = 0;
    for (Map.Entry<String, HttpServlet> entry : extraServlets.entrySet()) {
      String path = entry.getKey();
      String name = "extra-" + (extraServletIndex++);
      Wrapper extraWrapper = context.createWrapper();
      extraWrapper.setName(name);
      extraWrapper.setServlet(entry.getValue());
      extraWrapper.setLoadOnStartup(1);
      context.addChild(extraWrapper);
      context.addServletMappingDecoded(path, name);
    }

    FilterDef filterDef = new FilterDef();
    filterDef.setFilterName("connection-tracker");
    filterDef.setFilter(connectionTracker);
    filterDef.setAsyncSupported("true");
    context.addFilterDef(filterDef);
    FilterMap filterMap = new FilterMap();
    filterMap.setFilterName("connection-tracker");
    // Scoped to the MCP endpoint only — plain REST polling of extra servlets (e.g.
    // /osc-params) must not flip the MCP "connected" state in status.json. Both patterns
    // cover the exact path and any sub-path a future MCP transport variant might use.
    filterMap.addURLPattern(ENDPOINT);
    filterMap.addURLPattern(ENDPOINT + "/*");
    context.addFilterMap(filterMap);

    // Loopback by default: status.json discovery is inherently local, and the tool
    // surface mutates a live show — an unauthenticated listener should never reach the
    // LAN without an explicit, logged opt-in (see ConfigFile / ChromatikMcpPlugin). Touching
    // the connector before start also makes setPort(0) yield an ephemeral bind. No
    // async timeout is set: the SDK servlet disables it via setTimeout(0), so the
    // EngineExecutor call timeout is the only bound on a blocked tool call.
    tomcat.getConnector().setProperty("address", host);

    // LX's package watch-service hot-reloads this jar if it's overwritten while
    // Chromatik is running: it clears the plugin registry without disposing the live
    // plugin instance, orphaning this Tomcat (LXRegistry.reloadContent(), LX source
    // LXRegistry.java:723-731). disposePlugins() at quit only sees the new,
    // never-initialized entries, so no thread this server owns may be non-daemon —
    // an orphaned non-daemon thread pins the JVM and Chromatik hangs on quit forever.
    // Tomcat's HTTP exec/poller/acceptor threads are already daemon by default; only
    // the "Catalina-utility" scheduled-executor threads are not, unless told so here.
    if (tomcat.getServer() instanceof StandardServer standardServer) {
      standardServer.setUtilityThreadsAsDaemon(true);
    }

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
