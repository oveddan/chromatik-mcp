package chromatikmcp.tools;

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
import heronarts.lx.modulator.MacroKnobs;
import heronarts.lx.pattern.color.GradientPattern;

import chromatikmcp.HeadlessLxTest;
import chromatikmcp.domain.Modulators;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import chromatikmcp.engine.EngineExecutor;

/**
 * The Result.error mapping at the tool seam, exercised without HTTP: an unexpected
 * exception escaping a handler must come back as an isError result with the stable
 * {@code internal} code — never cross the MCP boundary as a stack trace. Invokes the
 * registered callHandler directly (the exchange argument is unused by the seam).
 */
@Timeout(60)
class ToolSeamTest extends HeadlessLxTest {

  private LX lx;
  private final AtomicBoolean draining = new AtomicBoolean(true);
  private Thread drainer;

  @BeforeEach
  void setUp() {
    this.lx = newHeadlessLx();
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

  // Runs before the inherited disposeLx(): the drainer must stop touching the engine
  // before the base fixture disposes the LX.
  @AfterEach
  void stopDrainer() throws InterruptedException {
    this.draining.set(false);
    if (this.drainer != null) {
      this.drainer.join(2_000);
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
            "sourcePath", knobs.macro1.getCanonicalPath(),
            "targetPath", channel.fader.getCanonicalPath(),
            "range", Double.NaN));
    Result.Error<Map<String, Object>> error =
        assertInstanceOf(Result.Error.class, result);
    assertEquals(Result.INVALID_ARGUMENT, error.code());
    assertTrue(error.message().contains("range must be between"), "error message describes bounds");
  }

  @Test
  void executorFailureMapsToInternalIsError() throws InterruptedException {
    // Regression for the PR that narrowed the try/catch into invoke(): EngineExecutor.call
    // itself can throw (timeout, interruption) from the *calling* thread, outside the
    // supplier invoke() wraps. Stop the drainer so the engine task never completes, then
    // interrupt the calling thread while it blocks in Future.get() inside executor.call.
    this.draining.set(false);
    this.drainer.join(2_000);

    McpServerFeatures.SyncToolSpecification spec =
        Tools.specification(new ImageTool(() -> new byte[0]), this.lx, new EngineExecutor(this.lx));

    java.util.concurrent.atomic.AtomicReference<McpSchema.CallToolResult> resultRef =
        new java.util.concurrent.atomic.AtomicReference<>();
    Thread caller = new Thread(() -> resultRef.set(
        spec.callHandler().apply(null, new McpSchema.CallToolRequest("image_tool", Map.of()))));
    caller.start();
    Thread.sleep(100); // let it reach Future.get() inside executor.call
    caller.interrupt();
    caller.join(5_000);

    McpSchema.CallToolResult result = resultRef.get();
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text =
        assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INTERNAL + ":"),
        "executor-level failure maps to internal, no thrown exception: " + text.text());
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
