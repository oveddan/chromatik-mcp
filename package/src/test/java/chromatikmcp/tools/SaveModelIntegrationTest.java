package chromatikmcp.tools;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import heronarts.lx.LX;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

import chromatikmcp.ServerStatus;
import chromatikmcp.engine.EngineExecutor;
import chromatikmcp.mcp.ConnectionTracker;
import chromatikmcp.mcp.EmbeddedMcpServer;

/**
 * The {@code save_model} happy path over real streamable-HTTP: {@link ToolsIntegrationTest}'s
 * shared {@code lx} is permanently in static-model mode (see its class javadoc), so it can
 * never link a real {@code .lxm} file — the "link actually moved and is reported" behavior
 * {@code save_model} exists for is otherwise unproven end-to-end. This class constructs its
 * own dynamic-structure {@code LX} (mirroring {@code ProjectsTest.newLxWithMediaPath}) in a
 * fresh JVM fork ({@code reuseForks=false}, package/pom.xml), so it does not collide with
 * {@code ToolsIntegrationTest}'s single-construction CoreMIDI constraint — that constraint is
 * intra-class only.
 */
@Timeout(60)
class SaveModelIntegrationTest {

  @TempDir
  private static java.nio.file.Path mediaPath;

  @AutoClose("dispose")
  private static LX lx;
  private static EmbeddedMcpServer server;
  private static McpSyncClient client;
  private static final AtomicBoolean draining = new AtomicBoolean(true);
  private static Thread drainer;

  @BeforeAll
  static void setUp() {
    LX.Flags flags = new LX.Flags();
    flags.mediaPath = mediaPath.toString();
    lx = new LX(flags);

    drainer = new Thread(() -> {
      while (draining.get()) {
        lx.engine.run();
        try {
          Thread.sleep(2);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }, "save-model-test-engine-drainer");
    drainer.start();

    ServerStatus status = new ServerStatus();
    ConnectionTracker connectionTracker = new ConnectionTracker();
    GetStatus getStatus = new GetStatus(
        status, () -> connectionTracker.snapshot(System.currentTimeMillis()));
    server = EmbeddedMcpServer.start("Chromatik-MCP", "0.0.1-test", 0, "127.0.0.1",
        Tools.specifications(lx, new EngineExecutor(lx), getStatus), Tools.INSTRUCTIONS,
        connectionTracker);
    status.initialize("127.0.0.1", server.port(), System.currentTimeMillis(), EmbeddedMcpServer.ENDPOINT);
    HttpClientStreamableHttpTransport transport =
        HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + server.port())
            .endpoint(EmbeddedMcpServer.ENDPOINT)
            .build();
    client = McpClient.sync(transport).build();
    client.initialize();
  }

  @AfterAll
  static void tearDown() throws InterruptedException {
    if (client != null) {
      client.closeGracefully();
    }
    if (server != null) {
      server.stop();
    }
    draining.set(false);
    if (drainer != null) {
      drainer.join(2_000);
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  void saveModelOverMcpWritesAFileAndMovesTheLinkedModel() {
    File target = new File(mediaPath.toFile(), "integration-rig.lxm");
    McpSchema.CallToolResult result =
        client.callTool(new McpSchema.CallToolRequest("save_model", Map.of(
            "path", target.getAbsolutePath())));
    assertNotEquals(Boolean.TRUE, result.isError(), "expected a success result");
    Map<String, Object> payload = (Map<String, Object>) result.structuredContent();

    assertEquals(target.getAbsolutePath(), payload.get("path"));
    assertTrue(target.isFile(), "save_model must write the resolved file");

    Map<String, Object> model = (Map<String, Object>) payload.get("model");
    assertEquals(target.getAbsolutePath(), model.get("file"),
        "the echoed model block must reflect the moved link, not the prior (absent) one");
    assertEquals(target.getAbsolutePath(), lx.structure.getModelFile().getAbsolutePath(),
        "save_model must actually move LXStructure's linked model file");
  }

  @Test
  void saveModelOverwriteGuardOverMcpReturnsInvalidArgumentAndLeavesTheFileUnchanged(
      @TempDir java.nio.file.Path tempDir) throws IOException {
    File target = tempDir.resolve("existing.lxm").toFile();
    Files.writeString(target.toPath(), "not a model file");
    byte[] before = Files.readAllBytes(target.toPath());

    McpSchema.CallToolResult result =
        client.callTool(new McpSchema.CallToolRequest("save_model", Map.of(
            "path", target.getAbsolutePath())));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
    assertTrue(text.text().contains(target.getAbsolutePath()), "message names the resolved path");
    assertArrayEquals(before, Files.readAllBytes(target.toPath()),
        "overwrite=false must leave the existing file's bytes untouched");
  }
}
