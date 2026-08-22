package chromatikmcp.tools;

import chromatikmcp.domain.Cameras;
import chromatikmcp.domain.PointStyle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import heronarts.lx.LX;
import heronarts.lx.model.GridModel;

import io.modelcontextprotocol.spec.McpSchema;

import chromatikmcp.ServerStatus;
import chromatikmcp.StreamableHttpTestHarness;
import chromatikmcp.engine.EngineExecutor;
import chromatikmcp.mcp.ConnectionTracker;

/** Wire-level regression coverage for issue #198's one-step undo/redo tools. */
@Timeout(60)
class CommandHistoryToolsTest {

  @AutoClose("dispose")
  private static LX lx;
  private static StreamableHttpTestHarness harness;

  @BeforeAll
  static void setUp() {
    lx = new LX(new GridModel(4, 4).reindexPoints());
    ConnectionTracker connectionTracker = new ConnectionTracker();
    GetStatus getStatus = new GetStatus(new ServerStatus(),
        () -> connectionTracker.snapshot(System.currentTimeMillis()));
    harness = StreamableHttpTestHarness.startMcp(
        lx, Tools.specifications(
            lx, new EngineExecutor(lx), getStatus, new Cameras(), new PointStyle()),
        Tools.INSTRUCTIONS,
        connectionTracker);
  }

  @AfterAll
  static void tearDown() {
    if (harness != null) {
      harness.close();
    }
  }

  private static Map<String, Object> call(String tool) {
    McpSchema.CallToolResult response = harness.call(tool, Map.of());
    assertEquals(Boolean.FALSE, response.isError());
    return harness.structured(response);
  }

  @Test
  void toolsUndoAndRedoTheNewestCommandAndExposeHistoryState() {
    lx.command.clear();
    double before = lx.engine.speed.getValue();
    harness.structured(harness.call("set_parameter", Map.of(
        "path", lx.engine.speed.getCanonicalPath(), "value", 0.4)));

    Map<String, Object> undone = call("undo");
    assertEquals("undo", undone.get("action"));
    assertEquals(Boolean.TRUE, undone.get("changed"));
    assertTrue(((String) undone.get("command")).contains("Speed"));
    assertEquals(Boolean.TRUE, undone.get("canRedo"));
    assertEquals(before, lx.engine.speed.getValue(), 1e-9);

    Map<String, Object> redone = call("redo");
    assertEquals("redo", redone.get("action"));
    assertEquals(Boolean.TRUE, redone.get("changed"));
    assertEquals(undone.get("command"), redone.get("command"));
    assertEquals(Boolean.TRUE, redone.get("canUndo"));
    assertEquals(0.4, lx.engine.speed.getValue(), 1e-9);
  }

  @Test
  void emptyHistoryReturnsChangedFalseWithoutACommand() {
    lx.command.clear();

    Map<String, Object> result = call("undo");

    assertEquals(Boolean.FALSE, result.get("changed"));
    assertFalse(result.containsKey("command"));
    assertEquals(Boolean.FALSE, result.get("canUndo"));
    assertEquals(Boolean.FALSE, result.get("canRedo"));
  }

  @Test
  void toolsUndoAndRedoStructuralChannelCreation() {
    lx.command.clear();
    int before = lx.engine.mixer.channels.size();
    harness.structured(harness.call("add_channel", Map.of()));
    assertEquals(before + 1, lx.engine.mixer.channels.size());

    Map<String, Object> undone = call("undo");
    assertEquals(Boolean.TRUE, undone.get("changed"));
    assertTrue(((String) undone.get("command")).contains("Channel"));
    assertEquals(before, lx.engine.mixer.channels.size());

    Map<String, Object> redone = call("redo");
    assertEquals(Boolean.TRUE, redone.get("changed"));
    assertEquals(undone.get("command"), redone.get("command"));
    assertEquals(before + 1, lx.engine.mixer.channels.size());
  }
}
