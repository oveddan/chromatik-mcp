package chromatikmcp.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * The PR-1a go/no-go gate: start the embedded MCP server in-process on an ephemeral
 * port, connect a real MCP client over streamable-HTTP, and complete the {@code
 * initialize} handshake. Passing proves the official Java MCP SDK embeds inside a
 * long-running JVM and serves MCP over HTTP — the core feasibility question.
 */
class EmbeddedMcpServerTest {

  @Test
  void initializeHandshakeReturnsServerInfo() {
    EmbeddedMcpServer server = EmbeddedMcpServer.start("Chromatik-MCP", "0.0.1-test", 0);
    assertTrue(server.port() > 0, "server should bind an ephemeral port");
    try {
      HttpClientStreamableHttpTransport transport =
          HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + server.port())
              .endpoint(EmbeddedMcpServer.ENDPOINT)
              .build();
      McpSyncClient client = McpClient.sync(transport).build();
      try {
        McpSchema.InitializeResult result = client.initialize();
        assertNotNull(result, "initialize() should return a result");
        assertNotNull(result.serverInfo(), "initialize result should carry server info");
        assertEquals("Chromatik-MCP", result.serverInfo().name());
      } finally {
        client.closeGracefully();
      }
    } finally {
      server.stop();
    }
  }

  /** A trivial servlet standing in for a real extra mount (e.g. {@code OscParamsServlet}). */
  private static final class EchoServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
      response.setStatus(HttpServletResponse.SC_OK);
      response.setContentType("text/plain");
      response.getWriter().write("echo");
    }
  }

  /**
   * PR-A deliverable: an extra servlet mounted alongside the MCP endpoint answers on its
   * own exact path, and {@code /mcp} keeps working (the exact-path mapping doesn't shadow
   * the {@code /*} wildcard mapping).
   */
  @Test
  void extraServletIsMountedAlongsideMcpEndpoint() throws IOException, InterruptedException {
    EmbeddedMcpServer server = EmbeddedMcpServer.start(
        "Chromatik-MCP", "0.0.1-test", 0, "127.0.0.1", List.of(), null, new ConnectionTracker(),
        Map.of("/osc-params", new EchoServlet()));
    try {
      HttpClient http = HttpClient.newHttpClient();
      HttpResponse<String> response = http.send(
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/osc-params"))
              .GET()
              .build(),
          HttpResponse.BodyHandlers.ofString());
      assertEquals(200, response.statusCode());
      assertEquals("echo", response.body());

      HttpClientStreamableHttpTransport transport =
          HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + server.port())
              .endpoint(EmbeddedMcpServer.ENDPOINT)
              .build();
      McpSyncClient client = McpClient.sync(transport).build();
      try {
        assertNotNull(client.initialize(), "/mcp must still answer alongside the extra mount");
      } finally {
        client.closeGracefully();
      }
    } finally {
      server.stop();
    }
  }

  /**
   * The {@code ConnectionTracker} filter is scoped to {@code /mcp} (see {@code
   * EmbeddedMcpServer.startWithContextClassLoader}), so plain REST polling of an extra
   * mount must not flip the MCP "connected" state — otherwise a Bitwig-side param picker
   * polling {@code /osc-params} every couple seconds would make Chromatik's status.json
   * report a live MCP client that was never there.
   */
  @Test
  void pollingAnExtraServletDoesNotMarkConnectionTrackerConnected()
      throws IOException, InterruptedException {
    ConnectionTracker connectionTracker = new ConnectionTracker();
    EmbeddedMcpServer server = EmbeddedMcpServer.start(
        "Chromatik-MCP", "0.0.1-test", 0, "127.0.0.1", List.of(), null, connectionTracker,
        Map.of("/osc-params", new EchoServlet()));
    try {
      HttpClient http = HttpClient.newHttpClient();
      HttpResponse<String> response = http.send(
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/osc-params"))
              .GET()
              .build(),
          HttpResponse.BodyHandlers.ofString());
      assertEquals(200, response.statusCode());

      assertFalse(connectionTracker.snapshot(System.currentTimeMillis()).connected(),
          "REST polling of a non-MCP mount must not flip the MCP connected state");
    } finally {
      server.stop();
    }
  }

  @Test
  void initializeHandshakeMarksConnectionTrackerConnected() {
    EmbeddedMcpServer server = EmbeddedMcpServer.start("Chromatik-MCP", "0.0.1-test", 0);
    try {
      HttpClientStreamableHttpTransport transport =
          HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + server.port())
              .endpoint(EmbeddedMcpServer.ENDPOINT)
              .build();
      McpSyncClient client = McpClient.sync(transport).build();
      try {
        client.initialize();
        assertTrue(server.connectionTracker().snapshot(System.currentTimeMillis()).connected(),
            "a real client's initialize POST counts as activity");
      } finally {
        client.closeGracefully();
      }
    } finally {
      server.stop();
    }
  }

  @Test
  void explicitHostOverloadBindsLoopback() {
    EmbeddedMcpServer server = EmbeddedMcpServer.start(
        "Chromatik-MCP", "0.0.1-test", 0, "127.0.0.1", List.of(), null);
    try {
      HttpClientStreamableHttpTransport transport =
          HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + server.port())
              .endpoint(EmbeddedMcpServer.ENDPOINT)
              .build();
      McpSyncClient client = McpClient.sync(transport).build();
      try {
        McpSchema.InitializeResult result = client.initialize();
        assertNotNull(result);
      } finally {
        client.closeGracefully();
      }
    } finally {
      server.stop();
    }
  }

  @Test
  void startOnAnAlreadyBoundFixedPortThrows() {
    EmbeddedMcpServer first = EmbeddedMcpServer.start("Chromatik-MCP", "0.0.1-test", 0);
    try {
      int takenPort = first.port();
      IllegalStateException thrown = assertThrows(IllegalStateException.class,
          () -> EmbeddedMcpServer.start(
              "Chromatik-MCP", "0.0.1-test", takenPort, "127.0.0.1", List.of(), null),
          "Tomcat swallows the connector bind failure internally (logs, returns normally "
              + "from start()) rather than throwing, so getLocalPort() == -1 is the only "
              + "signal — this must be surfaced, not reported as a healthy listener.");
      assertTrue(thrown.getMessage().contains(String.valueOf(takenPort)));
    } finally {
      first.stop();
    }
  }

  /**
   * Quit-hang regression guard: LX's package hot-reload can orphan a live plugin (and
   * its Tomcat) without disposing it — see the comment in {@code EmbeddedMcpServer.start}
   * above the {@code setUtilityThreadsAsDaemon(true)} call. If any thread this server
   * owns is non-daemon, an orphaned instance pins the JVM and Chromatik never exits.
   * Tomcat's HTTP exec/poller/acceptor threads are already daemon by default; this
   * guards the "Catalina-utility" scheduled-executor threads specifically.
   */
  @Test
  void utilityThreadsAreDaemon() {
    EmbeddedMcpServer server = EmbeddedMcpServer.start("Chromatik-MCP", "0.0.1-test", 0);
    try {
      for (Thread thread : Thread.getAllStackTraces().keySet()) {
        if (thread.getName().startsWith("Catalina-utility")) {
          assertTrue(thread.isDaemon(),
              "Catalina-utility thread must be daemon so an orphaned server can't pin "
                  + "the JVM at quit: " + thread.getName());
        }
      }
    } finally {
      server.stop();
    }
  }

  /**
   * PR-2 deliverable: the server advertises the tools capability, so a connected client's
   * {@code tools/list} succeeds. With no tools registered it returns an empty list (the
   * first real tool lands in PR-3). This is the over-the-wire proof of the capability.
   */
  @Test
  void listToolsReturnsEmptyOverHttp() {
    EmbeddedMcpServer server = EmbeddedMcpServer.start("Chromatik-MCP", "0.0.1-test", 0, List.of());
    try {
      HttpClientStreamableHttpTransport transport =
          HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + server.port())
              .endpoint(EmbeddedMcpServer.ENDPOINT)
              .build();
      McpSyncClient client = McpClient.sync(transport).build();
      try {
        client.initialize();
        McpSchema.ListToolsResult tools = client.listTools();
        assertNotNull(tools, "listTools() should return a result");
        assertTrue(tools.tools().isEmpty(), "no tools are registered in PR-2");
      } finally {
        client.closeGracefully();
      }
    } finally {
      server.stop();
    }
  }
}
