package lxmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import heronarts.lx.LX;
import heronarts.lx.model.GridModel;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import lxmcp.engine.EngineExecutor;

/**
 * The Result.error mapping at the tool seam, exercised without HTTP: an unexpected
 * exception escaping a handler must come back as an isError result with the stable
 * {@code internal} code — never cross the MCP boundary as a stack trace. Invokes the
 * registered callHandler directly (the exchange argument is unused by the seam).
 */
@Timeout(60)
class ToolSeamTest {

  private LX lx;
  private final AtomicBoolean draining = new AtomicBoolean(true);
  private Thread drainer;

  @BeforeEach
  void setUp() {
    this.lx = new LX(new GridModel(8, 8));
    this.drainer = new Thread(() -> {
      while (this.draining.get()) {
        this.lx.engine.run();
        try {
          Thread.sleep(2);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }, "test-engine-drainer");
    this.drainer.start();
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    this.draining.set(false);
    if (this.drainer != null) {
      this.drainer.join(2_000);
    }
    if (this.lx != null) {
      this.lx.dispose();
    }
  }

  private static final class BoomTool implements LxTool {
    @Override
    public String name() {
      return "boom_tool";
    }

    @Override
    public String description() {
      return "always throws";
    }

    @Override
    public Map<String, Object> inputSchema() {
      return Schemas.noArgs();
    }

    @Override
    public boolean readOnly() {
      return true;
    }

    @Override
    public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
      throw new IllegalStateException("boom");
    }
  }

  @Test
  void unexpectedExceptionMapsToInternalIsError() {
    McpServerFeatures.SyncToolSpecification spec =
        Tools.specification(new BoomTool(), this.lx, new EngineExecutor(this.lx));

    McpSchema.CallToolResult result =
        spec.callHandler().apply(null, new McpSchema.CallToolRequest("boom_tool", Map.of()));

    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text =
        assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INTERNAL + ": boom"),
        "stable code + original message, no stack trace: " + text.text());
  }
}
