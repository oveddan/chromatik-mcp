package chromatikmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import heronarts.lx.LX;
import heronarts.lx.LXPath;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.model.GridModel;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.pattern.color.GradientPattern;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

import chromatikmcp.ServerStatus;
import chromatikmcp.engine.EngineExecutor;
import chromatikmcp.mcp.ConnectionTracker;
import chromatikmcp.mcp.EmbeddedMcpServer;

/**
 * Integration coverage for {@code apply_operations} (issue #118), over the same
 * headless-LX + drainer-thread harness {@link ToolsIntegrationTest} uses (see
 * {@code docs/qa-strategy.md}) — a separate LX instance/server so these tests (which add and
 * remove channels, and probe undo depth directly) don't interleave with that class's shared
 * fixtures.
 */
@Timeout(60)
class ApplyOperationsTest {

  @AutoClose("dispose")
  private static LX lx;
  private static LXChannel channel;
  private static EmbeddedMcpServer server;
  private static McpSyncClient client;
  private static final AtomicBoolean draining = new AtomicBoolean(true);
  private static Thread drainer;

  @BeforeAll
  static void setUp() {
    lx = new LX(new GridModel(4, 4).reindexPoints());
    channel = lx.engine.mixer.addChannel();

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
    }, "apply-operations-test-engine-drainer");
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

  private static McpSchema.CallToolResult call(String tool, Map<String, Object> args) {
    return client.callTool(new McpSchema.CallToolRequest(tool, args));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> structured(McpSchema.CallToolResult result) {
    assertNotEquals(Boolean.TRUE, result.isError(), "expected a success result");
    assertInstanceOf(Map.class, result.structuredContent(), "success carries structuredContent");
    return (Map<String, Object>) result.structuredContent();
  }

  private static String errorText(McpSchema.CallToolResult result) {
    assertEquals(Boolean.TRUE, result.isError());
    return ((McpSchema.TextContent) result.content().get(0)).text();
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> resultsOf(Map<String, Object> payload) {
    return (List<Map<String, Object>>) payload.get("results");
  }

  private static Map<String, Object> op(String tool, Map<String, Object> args) {
    return Map.of("tool", tool, "args", args);
  }

  @Test
  void happyPathAppliesEveryOperationAndUndoIsPerOperation() {
    int channelsBefore = lx.engine.mixer.channels.size();
    double speedBefore = lx.engine.speed.getValue();
    double newSpeed = 0.4;

    Map<String, Object> payload = structured(call("apply_operations", Map.of(
        "operations", List.of(
            op("add_channel", Map.of()),
            op("set_parameter", Map.of(
                "path", lx.engine.speed.getCanonicalPath(), "value", newSpeed))))));

    List<Map<String, Object>> results = resultsOf(payload);
    assertEquals(2, results.size());
    assertEquals(0, results.get(0).get("index"));
    assertEquals(Boolean.TRUE, results.get(0).get("ok"));
    @SuppressWarnings("unchecked")
    Map<String, Object> addResult = (Map<String, Object>) results.get(0).get("result");
    assertNotNull(addResult.get("path"), "add_channel's own payload shape rides through untouched");

    assertEquals(1, results.get(1).get("index"));
    assertEquals(Boolean.TRUE, results.get(1).get("ok"));

    assertEquals(channelsBefore + 1, lx.engine.mixer.channels.size(), "add_channel applied");
    assertEquals(newSpeed, lx.engine.speed.getValue(), 1e-9, "set_parameter applied");

    // The descope, demonstrated: two operations produced two undo-stack entries, not one.
    // undo() is LIFO, so the first press only reverts the LAST-performed operation
    // (set_parameter, pushed second) — add_channel's own entry hasn't been popped yet.
    lx.command.undo();
    assertEquals(speedBefore, lx.engine.speed.getValue(), 1e-9, "first undo reverts set_parameter");
    assertEquals(channelsBefore + 1, lx.engine.mixer.channels.size(),
        "...but add_channel is still applied — its undo entry is separate");

    lx.command.undo();
    assertEquals(channelsBefore, lx.engine.mixer.channels.size(), "second undo reverts add_channel");
  }

  @Test
  void continueOnErrorAppliesSurroundingOperations() {
    double before = channel.fader.getValue();

    Map<String, Object> payload = structured(call("apply_operations", Map.of(
        "operations", List.of(
            op("set_parameter", Map.of("path", channel.fader.getCanonicalPath(), "value", 0.3)),
            op("set_parameter", Map.of(
                "path", "/lx/mixer/channel/999/fader", "value", 0.9)),
            op("set_parameter", Map.of("path", channel.fader.getCanonicalPath(), "value", 0.7))))));

    List<Map<String, Object>> results = resultsOf(payload);
    assertEquals(3, results.size());
    assertEquals(Boolean.TRUE, results.get(0).get("ok"));
    assertEquals(Boolean.FALSE, results.get(1).get("ok"), "the unresolvable path failed");
    assertEquals(1, results.get(1).get("index"));
    assertEquals(Result.NOT_FOUND, results.get(1).get("code"));
    assertNotNull(results.get(1).get("message"));
    assertEquals(Boolean.TRUE, results.get(2).get("ok"),
        "an op AFTER the failure still ran (continue-on-error)");

    assertEquals(0.7, channel.fader.getValue(), 1e-9, "the last successful write is what's live");

    lx.command.undo();
    lx.command.undo();
    assertEquals(before, channel.fader.getValue(), 1e-9, "both successful ops each own an undo entry");
  }

  @Test
  void unknownToolNameRejectsTheWholeCallAndAppliesNothing() {
    int channelsBefore = lx.engine.mixer.channels.size();

    McpSchema.CallToolResult result = call("apply_operations", Map.of(
        "operations", List.of(
            op("add_channel", Map.of()),
            op("not_a_real_tool", Map.of()))));

    String text = errorText(result);
    assertTrue(text.startsWith(Result.INVALID_ARGUMENT), text);
    assertEquals(channelsBefore, lx.engine.mixer.channels.size(),
        "pre-validation rejected the batch before add_channel ran");
  }

  @Test
  void readOnlyToolNameIsRejected() {
    McpSchema.CallToolResult result = call("apply_operations", Map.of(
        "operations", List.of(op("get_project_info", Map.of()))));
    String text = errorText(result);
    assertTrue(text.startsWith(Result.INVALID_ARGUMENT), text);
  }

  @Test
  void nestedApplyOperationsIsRejectedTheSameWayAsAReadOnlyTool() {
    // No special-cased "am I nested?" check exists — apply_operations is simply absent from
    // the mutation-tool map it was built from (Tools.allTools), so it fails the same
    // unknown-tool-name path a read-only tool does.
    McpSchema.CallToolResult result = call("apply_operations", Map.of(
        "operations", List.of(op("apply_operations", Map.of(
            "operations", List.of(op("add_channel", Map.of())))))));
    String text = errorText(result);
    assertTrue(text.startsWith(Result.INVALID_ARGUMENT), text);
  }

  @Test
  void tooManyOperationsIsRejected() {
    double before = channel.fader.getValue();
    List<Map<String, Object>> operations = new ArrayList<>();
    for (int i = 0; i < ApplyOperations.MAX_OPERATIONS + 1; i++) {
      operations.add(op("set_parameter", Map.of("path", channel.fader.getCanonicalPath(), "value", 0.5)));
    }

    // The schema's own maxItems:50 fully expresses this cap, so — like a missing required
    // arg (see ToolsIntegrationTest#getParameterBadArgsAreRejected) — the SDK's inputSchema
    // validation rejects this before the handler's own (redundant, defense-in-depth) size
    // check ever runs; its error text isn't the "invalid_argument: ..." wire shape, just isError.
    McpSchema.CallToolResult result = call("apply_operations", Map.of("operations", operations));
    assertEquals(Boolean.TRUE, result.isError());
    assertEquals(before, channel.fader.getValue(), 1e-9, "nothing applied once the cap is exceeded");
  }

  @Test
  void multipleInvalidToolNamesAreAllReportedTogether() {
    McpSchema.CallToolResult result = call("apply_operations", Map.of(
        "operations", List.of(
            op("not_a_real_tool", Map.of()),
            op("add_channel", Map.of()),
            op("also_not_real", Map.of()))));

    String text = errorText(result);
    assertTrue(text.startsWith(Result.INVALID_ARGUMENT), text);
    assertTrue(text.contains("not_a_real_tool"), text);
    assertTrue(text.contains("also_not_real"), text);
  }

  @Test
  void handlerLevelCapRejectsOversizedBatchDirectly() {
    // tooManyOperationsIsRejected above goes through the MCP client, so the SDK's
    // maxItems:50 schema check intercepts before ApplyOperations.handle() ever runs — its
    // own MAX_OPERATIONS guard is never exercised. Call the handler directly to cover it.
    List<Map<String, Object>> operations = new ArrayList<>();
    for (int i = 0; i <= ApplyOperations.MAX_OPERATIONS; i++) {
      operations.add(op("set_parameter", Map.of("path", channel.fader.getCanonicalPath(), "value", 0.5)));
    }

    ApplyOperations tool = new ApplyOperations(Map.of("set_parameter", new SetParameter()));
    Result<Map<String, Object>> result =
        tool.handle(lx, Map.of("operations", operations));

    Result.Error<Map<String, Object>> error = assertInstanceOf(Result.Error.class, result);
    assertEquals(Result.INVALID_ARGUMENT, error.code());
    assertTrue(error.message().contains(String.valueOf(ApplyOperations.MAX_OPERATIONS)), error.message());
  }

  @Test
  void allInvalidEntriesAtTheMaxOperationsBoundaryAreAllReportedTogether() {
    // multipleInvalidToolNamesAreAllReportedTogether above only covers 2 of 3 entries;
    // this exercises the worst case the join at ApplyOperations.java:152-154 actually
    // faces — every one of MAX_OPERATIONS entries invalid — and confirms none is dropped
    // and the joined message stays bounded (roughly MAX_OPERATIONS lines, not unbounded).
    // Call the handler directly, like handlerLevelCapRejectsOversizedBatchDirectly, so the
    // SDK's own maxItems:50 schema check doesn't intercept first.
    List<Map<String, Object>> operations = new ArrayList<>();
    for (int i = 0; i < ApplyOperations.MAX_OPERATIONS; i++) {
      operations.add(op("not_a_real_tool_" + i, Map.of()));
    }

    ApplyOperations tool = new ApplyOperations(Map.of("set_parameter", new SetParameter()));
    Result<Map<String, Object>> result = tool.handle(lx, Map.of("operations", operations));

    Result.Error<Map<String, Object>> error = assertInstanceOf(Result.Error.class, result);
    assertEquals(Result.INVALID_ARGUMENT, error.code());
    for (int i = 0; i < ApplyOperations.MAX_OPERATIONS; i++) {
      assertTrue(error.message().contains("not_a_real_tool_" + i), error.message());
    }
    assertTrue(error.message().length() < 8_000,
        "joined message should stay bounded at the cap, not balloon: " + error.message().length());
  }

  @Test
  void missingRequiredArgOnABatchedOpIsInvalidArgumentNotInternal() {
    // The re-entrant Tools.invoke() path skips the SDK's own inputSchema pre-validation a
    // top-level call gets — this confirms remove_channel's handler-level guard still produces
    // the right wire code (invalid_argument) rather than an unhandled NPE/ClassCastException
    // surfacing as internal.
    Map<String, Object> payload = structured(call("apply_operations", Map.of(
        "operations", List.of(op("remove_channel", Map.of())))));

    List<Map<String, Object>> results = resultsOf(payload);
    assertEquals(1, results.size());
    assertEquals(Boolean.FALSE, results.get(0).get("ok"));
    assertEquals(Result.INVALID_ARGUMENT, results.get(0).get("code"),
        "remove_channel's own guard on a missing 'path' fires even called through invoke()");
  }

  @Test
  void batchedMovePatternFractionalIndexIsRejectedAndDoesNotMutate() {
    // apply_operations's re-entrant Tools.invoke() path (see
    // missingRequiredArgOnABatchedOpIsInvalidArgumentNotInternal above) skips the SDK's own
    // inputSchema validation for each sub-op's args — unlike a top-level move_pattern call,
    // where the SDK's integer-typed schema would reject a fractional index before our handler
    // ever saw it (see ToolsIntegrationTest#movePatternFractionalIndexIsInvalidArgumentAndDoesNotMutate).
    // This is the one reachable-over-real-HTTP path that bypasses that validator, so it must
    // still hit Args.requireInt's own check rather than truncating 1.5 into a silent move to
    // index 1.
    Map<String, Object> ch = structured(call("add_channel", Map.of()));
    String channelPath = (String) ch.get("path");
    try {
      Map<String, Object> p0 = structured(call("add_pattern", Map.of(
          "containerPath", channelPath, "class", GradientPattern.class.getName())));
      structured(call("add_pattern", Map.of(
          "containerPath", channelPath, "class", GradientPattern.class.getName())));
      String p0path = (String) p0.get("path");
      LXPattern pattern = (LXPattern) LXPath.get(lx, p0path);
      int indexBefore = pattern.getIndex();

      Map<String, Object> payload = structured(call("apply_operations", Map.of(
          "operations", List.of(op("move_pattern", Map.of("path", p0path, "index", 1.5))))));

      List<Map<String, Object>> results = resultsOf(payload);
      assertEquals(1, results.size());
      assertEquals(Boolean.FALSE, results.get(0).get("ok"),
          "a fractional index must not silently truncate through apply_operations");
      assertEquals(Result.INVALID_ARGUMENT, results.get(0).get("code"));
      assertEquals(indexBefore, pattern.getIndex(), "no mutation occurred");

      // The same batch path with a whole-valued double must still be accepted, exactly as a
      // top-level call is (moveEffectIntegralDoubleIndexIsAcceptedLikeInt et al.) — the fix
      // must not have broken legitimate JSON clients that send integers as doubles.
      Map<String, Object> acceptedPayload = structured(call("apply_operations", Map.of(
          "operations", List.of(op("move_pattern", Map.of("path", p0path, "index", 1.0))))));
      List<Map<String, Object>> acceptedResults = resultsOf(acceptedPayload);
      assertEquals(1, acceptedResults.size());
      assertEquals(Boolean.TRUE, acceptedResults.get(0).get("ok"),
          "a whole-valued double must still be accepted");
      assertEquals(1, pattern.getIndex(), "the whole-valued double actually moved the pattern");
    } finally {
      structured(call("remove_channel", Map.of("path", channelPath)));
    }
  }

  @Test
  void batchedMovePatternOutOfIntRangeIndexIsRejectedAndDoesNotMutate() {
    // Same bypass-the-SDK-schema path as the fractional-index case above, but for a JSON
    // integer literal past Integer.MAX_VALUE: Jackson hands Args.requireInt a Long, and
    // Long.intValue() narrows by taking the low 32 bits (4294967297L -> 1) rather than
    // failing — a client sending this value would otherwise see a silent move to index 1.
    Map<String, Object> ch = structured(call("add_channel", Map.of()));
    String channelPath = (String) ch.get("path");
    try {
      Map<String, Object> p0 = structured(call("add_pattern", Map.of(
          "containerPath", channelPath, "class", GradientPattern.class.getName())));
      structured(call("add_pattern", Map.of(
          "containerPath", channelPath, "class", GradientPattern.class.getName())));
      String p0path = (String) p0.get("path");
      LXPattern pattern = (LXPattern) LXPath.get(lx, p0path);
      int indexBefore = pattern.getIndex();

      Map<String, Object> payload = structured(call("apply_operations", Map.of(
          "operations", List.of(op("move_pattern", Map.of("path", p0path, "index", 4294967297L))))));

      List<Map<String, Object>> results = resultsOf(payload);
      assertEquals(1, results.size());
      assertEquals(Boolean.FALSE, results.get(0).get("ok"),
          "an out-of-int-range index must not silently narrow through apply_operations");
      assertEquals(Result.INVALID_ARGUMENT, results.get(0).get("code"));
      assertEquals(indexBefore, pattern.getIndex(), "no mutation occurred");
    } finally {
      structured(call("remove_channel", Map.of("path", channelPath)));
    }
  }
}
