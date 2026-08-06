package chromatikmcp;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.http.HttpServlet;

import heronarts.lx.LX;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import chromatikmcp.mcp.ConnectionTracker;
import chromatikmcp.mcp.EmbeddedMcpServer;

/**
 * Shared lifecycle for integration tests that run the MCP server against a headless LX.
 * The drainer substitutes for LX's engine thread and is always stopped and joined before
 * JUnit disposes the test's LX instance.
 */
public final class StreamableHttpTestHarness implements AutoCloseable {

  private static final long DRAINER_JOIN_MILLIS = 2_000;

  private final AtomicBoolean draining = new AtomicBoolean(true);
  private final Thread drainer;
  private final EmbeddedMcpServer server;
  private final McpSyncClient client;

  private StreamableHttpTestHarness(
      LX lx,
      List<McpServerFeatures.SyncToolSpecification> tools,
      String instructions,
      ConnectionTracker connectionTracker,
      Map<String, HttpServlet> extraServlets,
      boolean initializeClient) {
    this.drainer = new Thread(() -> {
      while (this.draining.get()) {
        lx.engine.run();
        try {
          Thread.sleep(2);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }, "test-engine-drainer");
    this.drainer.start();

    EmbeddedMcpServer startedServer = null;
    McpSyncClient startedClient = null;
    try {
      startedServer = EmbeddedMcpServer.start(
          "Chromatik-MCP", "0.0.1-test", 0, "127.0.0.1", tools, instructions,
          connectionTracker, extraServlets);
      if (initializeClient) {
        startedClient = createClient(startedServer.port());
        startedClient.initialize();
      }
    } catch (RuntimeException | Error e) {
      if (startedClient != null) {
        startedClient.closeGracefully();
      }
      if (startedServer != null) {
        startedServer.stop();
      }
      stopDrainer();
      throw e;
    }
    this.server = startedServer;
    this.client = startedClient;
  }

  public static StreamableHttpTestHarness startMcp(
      LX lx,
      List<McpServerFeatures.SyncToolSpecification> tools,
      String instructions,
      ConnectionTracker connectionTracker) {
    return new StreamableHttpTestHarness(
        lx, tools, instructions, connectionTracker, Map.of(), true);
  }

  public static StreamableHttpTestHarness startHttp(
      LX lx,
      Map<String, HttpServlet> extraServlets) {
    return new StreamableHttpTestHarness(
        lx, List.of(), null, new ConnectionTracker(), extraServlets, false);
  }

  public int port() {
    return this.server.port();
  }

  public McpSyncClient client() {
    if (this.client == null) {
      throw new IllegalStateException("Harness was started without an MCP client");
    }
    return this.client;
  }

  public McpSchema.CallToolResult call(String tool, Map<String, Object> args) {
    // Use one transport per call so no mutating request can land on a stale pooled
    // keep-alive connection and tempt an ambiguous retry.
    McpSyncClient callClient = createClient(this.server.port());
    try {
      callClient.initialize();
      return callClient.callTool(new McpSchema.CallToolRequest(tool, args));
    } finally {
      callClient.closeGracefully();
    }
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> structured(McpSchema.CallToolResult result) {
    assertNotEquals(Boolean.TRUE, result.isError(), "expected a success result");
    assertInstanceOf(Map.class, result.structuredContent(), "success carries structuredContent");
    return (Map<String, Object>) result.structuredContent();
  }

  @Override
  public void close() {
    try {
      if (this.client != null) {
        this.client.closeGracefully();
      }
      this.server.stop();
    } finally {
      stopDrainer();
    }
  }

  private void stopDrainer() {
    this.draining.set(false);
    this.drainer.interrupt();
    try {
      this.drainer.join(DRAINER_JOIN_MILLIS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while stopping test engine drainer", e);
    }
    if (this.drainer.isAlive()) {
      throw new IllegalStateException("Test engine drainer did not stop within 2 seconds");
    }
  }

  private static McpSyncClient createClient(int port) {
    HttpClientStreamableHttpTransport transport =
        HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
            .endpoint(EmbeddedMcpServer.ENDPOINT)
            .build();
    return McpClient.sync(transport).build();
  }

}
