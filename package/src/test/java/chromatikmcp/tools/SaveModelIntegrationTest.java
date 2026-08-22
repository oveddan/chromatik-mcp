package chromatikmcp.tools;

import chromatikmcp.domain.Cameras;
import chromatikmcp.domain.PointStyle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import heronarts.lx.LX;

import io.modelcontextprotocol.spec.McpSchema;

import chromatikmcp.ServerStatus;
import chromatikmcp.StreamableHttpTestHarness;
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
  private static StreamableHttpTestHarness harness;

  @BeforeAll
  static void setUp() {
    LX.Flags flags = new LX.Flags();
    flags.mediaPath = mediaPath.toString();
    lx = new LX(flags);

    ServerStatus status = new ServerStatus();
    ConnectionTracker connectionTracker = new ConnectionTracker();
    GetStatus getStatus = new GetStatus(
        status, () -> connectionTracker.snapshot(System.currentTimeMillis()));
    harness = StreamableHttpTestHarness.startMcp(
        lx, Tools.specifications(
            lx, new EngineExecutor(lx), getStatus, new Cameras(), new PointStyle()),
        Tools.INSTRUCTIONS,
        connectionTracker);
    status.initialize(
        "127.0.0.1", harness.port(), System.currentTimeMillis(), EmbeddedMcpServer.ENDPOINT);
  }

  @AfterAll
  static void tearDown() {
    if (harness != null) {
      harness.close();
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  void saveModelOverMcpWritesAFileAndMovesTheLinkedModel() {
    File target = new File(mediaPath.toFile(), "integration-rig.lxm");
    McpSchema.CallToolResult result =
        harness.call("save_model", Map.of(
            "path", target.getAbsolutePath()));
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
        harness.call("save_model", Map.of(
            "path", target.getAbsolutePath()));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
    assertTrue(text.text().contains(target.getAbsolutePath()), "message names the resolved path");
    assertArrayEquals(before, Files.readAllBytes(target.toPath()),
        "overwrite=false must leave the existing file's bytes untouched");
  }
}
