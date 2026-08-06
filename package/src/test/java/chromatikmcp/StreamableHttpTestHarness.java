package chromatikmcp;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

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
  private final Set<String> readOnlyTools;

  private StreamableHttpTestHarness(
      LX lx,
      List<McpServerFeatures.SyncToolSpecification> tools,
      String instructions,
      ConnectionTracker connectionTracker,
      Map<String, HttpServlet> extraServlets,
      boolean initializeClient) {
    this.readOnlyTools = tools.stream()
        .filter(spec -> spec.tool().annotations() != null
            && Boolean.TRUE.equals(spec.tool().annotations().readOnlyHint()))
        .map(spec -> spec.tool().name())
        .collect(Collectors.toUnmodifiableSet());
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
      try {
        if (startedClient != null) {
          startedClient.closeGracefully();
        }
      } finally {
        try {
          if (startedServer != null) {
            startedServer.stop();
          }
        } finally {
          stopDrainer();
        }
      }
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
    if (this.readOnlyTools.contains(tool)) {
      try {
        return client().callTool(new McpSchema.CallToolRequest(tool, args));
      } catch (RuntimeException e) {
        // Retrying is safe only when the server advertises the operation as read-only.
        if (isHeaderParserNoBytes(e)) {
          return callFresh(tool, args);
        }
        throw e;
      }
    }

    // Mutations use a new transport and are issued exactly once. A missing response
    // cannot prove that a mutating request did not already reach the server.
    return callFresh(tool, args);
  }

  private McpSchema.CallToolResult callFresh(String tool, Map<String, Object> args) {
    McpSyncClient callClient = createClient(this.server.port());
    Throwable failure = null;
    try {
      callClient.initialize();
      return callClient.callTool(new McpSchema.CallToolRequest(tool, args));
    } catch (RuntimeException | Error e) {
      failure = e;
      throw e;
    } finally {
      try {
        callClient.closeGracefully();
      } catch (RuntimeException | Error closeFailure) {
        if (failure != null) {
          failure.addSuppressed(closeFailure);
        } else {
          throw closeFailure;
        }
      }
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
    } finally {
      try {
        this.server.stop();
      } finally {
        stopDrainer();
      }
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

  private static boolean isHeaderParserNoBytes(Throwable throwable) {
    for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
      if (cause instanceof java.io.IOException
          && cause.getMessage() != null
          && cause.getMessage().contains("header parser received no bytes")) {
        return true;
      }
    }
    return false;
  }

  private static McpSyncClient createClient(int port) {
    HttpClientStreamableHttpTransport transport =
        HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
            .endpoint(EmbeddedMcpServer.ENDPOINT)
            .build();
    return McpClient.sync(transport).build();
  }

}
