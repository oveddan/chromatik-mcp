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
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.model.GridModel;
import heronarts.lx.modulator.MacroKnobs;
import heronarts.lx.pattern.color.GradientPattern;

import lxmcp.domain.Modulators;

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

  private static final class ImageTool implements LxTool {
    private final java.util.function.Supplier<byte[]> png;

    ImageTool(java.util.function.Supplier<byte[]> png) {
      this.png = png;
    }

    @Override
    public String name() {
      return "image_tool";
    }

    @Override
    public String description() {
      return "returns an image result";
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
      return Result.okImage(Map.of("width", 2), this.png);
    }
  }

  @Test
  void okImageCarriesStructuredContentTextMirrorAndPng() {
    byte[] bytes = {1, 2, 3, 4};
    McpServerFeatures.SyncToolSpecification spec =
        Tools.specification(new ImageTool(() -> bytes), this.lx, new EngineExecutor(this.lx));

    McpSchema.CallToolResult result =
        spec.callHandler().apply(null, new McpSchema.CallToolRequest("image_tool", Map.of()));

    assertEquals(Boolean.FALSE, result.isError());
    assertEquals(Map.of("width", 2), result.structuredContent());
    McpSchema.TextContent text =
        assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().contains("\"width\""), "text mirror of structuredContent: " + text.text());
    McpSchema.ImageContent image =
        assertInstanceOf(McpSchema.ImageContent.class, result.content().get(1));
    assertEquals("image/png", image.mimeType());
    assertEquals(java.util.Base64.getEncoder().encodeToString(bytes), image.data());
  }

  @Test
  void throwingPngSupplierMapsToInternalIsError() {
    McpServerFeatures.SyncToolSpecification spec = Tools.specification(
        new ImageTool(() -> {
          throw new IllegalStateException("encode boom");
        }),
        this.lx, new EngineExecutor(this.lx));

    McpSchema.CallToolResult result =
        spec.callHandler().apply(null, new McpSchema.CallToolRequest("image_tool", Map.of()));

    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text =
        assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INTERNAL + ":"),
        "stable code, no stack trace: " + text.text());
  }

  @Test
  void getFrameInvalidViewIsInvalidArgument() {
    // The SDK's inputSchema enum rejects bad values over the wire; this pins the
    // handler's own valueOf fallback directly (belt and suspenders for schema drift).
    Result<Map<String, Object>> result =
        new GetFrame().handle(this.lx, Map.of("view", "diagonal"));
    Result.Error<Map<String, Object>> error =
        assertInstanceOf(Result.Error.class, result);
    assertEquals(Result.INVALID_ARGUMENT, error.code());
  }

  @Test
  void wireModulatorRangeNaNIsInvalidArgument() {
    // HTTP JSON cannot carry NaN, so test the handler directly. NaN fails both >= and <=,
    // but the inverted check !(range >= -1.0 && range <= 1.0) properly rejects it.
    LXChannel channel = this.lx.engine.mixer.addChannel();
    channel.addPattern(new GradientPattern(this.lx));
    MacroKnobs knobs =
        (MacroKnobs) Modulators.addModulator(this.lx, this.lx.engine.modulation, MacroKnobs.class);

    Result<Map<String, Object>> result = new WireModulator()
        .handle(this.lx, Map.of(
            "source", knobs.macro1.getCanonicalPath(),
            "target", channel.fader.getCanonicalPath(),
            "range", Double.NaN));
    Result.Error<Map<String, Object>> error =
        assertInstanceOf(Result.Error.class, result);
    assertEquals(Result.INVALID_ARGUMENT, error.code());
    assertTrue(error.message().contains("range must be between"), "error message describes bounds");
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
