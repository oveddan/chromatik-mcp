package lxmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import heronarts.lx.LX;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.model.GridModel;
import heronarts.lx.pattern.color.GradientPattern;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

import lxmcp.domain.Registry;
import lxmcp.engine.EngineExecutor;
import lxmcp.mcp.EmbeddedMcpServer;

/**
 * The PR-3 integration gate from the qa-strategy per-tool template: the six discovery
 * tools served over real streamable-HTTP against a headless LX. A drainer thread stands
 * in for LX's engine thread (the headless harness never starts {@code lx.engine}), so
 * the blocking {@code EngineExecutor.call(...)} inside each handler completes.
 *
 * <p>One LX + server + client for the whole class ({@code @BeforeAll}), not per test:
 * repeated {@code new LX(...)} construction in one JVM deadlocks on the JDK-global
 * javax.sound/CoreMIDI lock (a previous instance's MIDI device-scan thread holds it
 * while the next construction enters audio init — the same hazard the surefire
 * {@code reuseForks=false} comment documents). All LX fixture state is built before the
 * drainer starts; after that, tests only read.
 */
@Timeout(60)
class ToolsIntegrationTest {

  private static LX lx;
  private static LXChannel channel;
  private static EmbeddedMcpServer server;
  private static McpSyncClient client;
  private static final AtomicBoolean draining = new AtomicBoolean(true);
  private static Thread drainer;

  @BeforeAll
  static void setUp() {
    lx = new LX(new GridModel(8, 8));
    channel = lx.engine.mixer.addChannel();
    channel.addPattern(new GradientPattern(lx));

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
    }, "test-engine-drainer");
    drainer.start();

    server = EmbeddedMcpServer.start("LX-MCP", "0.0.1-test", 0,
        Tools.specifications(lx, new EngineExecutor(lx)));
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
    if (lx != null) {
      lx.dispose();
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

  @Test
  void advertisesAllDiscoveryTools() {
    McpSchema.ListToolsResult tools = client.listTools();
    Set<String> names = tools.tools().stream().map(McpSchema.Tool::name).collect(Collectors.toSet());
    assertEquals(
        Set.of("get_project_info", "list_channels", "list_available_patterns",
            "list_available_effects", "list_available_modulators", "get_parameter"),
        names);
    for (McpSchema.Tool tool : tools.tools()) {
      assertEquals(Boolean.TRUE, tool.annotations().readOnlyHint(),
          tool.name() + " is advertised read-only");
    }
  }

  @Test
  void getProjectInfoReturnsStructuredContentAndTextMirror() {
    McpSchema.CallToolResult result = call("get_project_info", Map.of());
    Map<String, Object> payload = structured(result);
    assertEquals(LX.VERSION, payload.get("lxVersion"));
    assertEquals(lx.engine.mixer.channels.size(),
        ((Number) payload.get("channelCount")).intValue());

    assertFalse(result.content().isEmpty(), "success also carries a text mirror");
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith("{"), "text mirror is the serialized JSON payload");
  }

  @Test
  @SuppressWarnings("unchecked")
  void listChannelsDescribesMixer() {
    Map<String, Object> payload = structured(call("list_channels", Map.of()));
    List<Map<String, Object>> channels = (List<Map<String, Object>>) payload.get("channels");
    assertEquals(lx.engine.mixer.channels.size(), channels.size());

    Map<String, Object> entry = channels.get(channel.getIndex());
    assertEquals(channel.getLabel(), entry.get("label"));
    assertEquals("channel", entry.get("type"));
    List<Map<String, Object>> patterns = (List<Map<String, Object>>) entry.get("patterns");
    assertEquals(channel.patterns.size(), patterns.size());

    assertNotNull(payload.get("master"), "master bus is always present");
  }

  @Test
  void listAvailableToolsMatchTheirRegistries() {
    assertEquals(Registry.patterns(lx).size(),
        ((List<?>) structured(call("list_available_patterns", Map.of())).get("patterns")).size());
    assertEquals(Registry.effects(lx).size(),
        ((List<?>) structured(call("list_available_effects", Map.of())).get("effects")).size());
    assertEquals(Registry.modulators(lx).size(),
        ((List<?>) structured(call("list_available_modulators", Map.of())).get("modulators")).size());
  }

  @Test
  void getParameterResolvesCanonicalPath() {
    String path = channel.fader.getCanonicalPath();
    Map<String, Object> payload = structured(call("get_parameter", Map.of("path", path)));
    assertEquals(path, payload.get("path"));
    assertEquals(channel.fader.getValue(), ((Number) payload.get("value")).doubleValue(), 1e-9);
  }

  @Test
  void getParameterUnknownPathIsResultError() {
    McpSchema.CallToolResult result = call("get_parameter", Map.of("path", "/lx/nope/nothing"));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.NOT_FOUND), "error text leads with the stable code");
  }

  @Test
  void getParameterBadArgsAreRejected() {
    // A missing required arg never reaches the handler: the SDK validates inputSchema
    // server-side and rejects it with its own message (pinned by the prefix check).
    McpSchema.CallToolResult missing = call("get_parameter", Map.of());
    assertEquals(Boolean.TRUE, missing.isError());
    McpSchema.TextContent missingText =
        assertInstanceOf(McpSchema.TextContent.class, missing.content().get(0));
    assertFalse(missingText.text().startsWith(Result.INVALID_ARGUMENT),
        "schema rejection happens in the SDK, before the handler's invalid_argument check");

    // An empty string passes the schema, so this exercises our invalid_argument seam.
    McpSchema.CallToolResult empty = call("get_parameter", Map.of("path", ""));
    assertEquals(Boolean.TRUE, empty.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, empty.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));

    // A component path is a typed TYPE_MISMATCH from the resolver — pin its wire code.
    McpSchema.CallToolResult mismatch = call("get_parameter", Map.of("path", "/lx/mixer"));
    assertEquals(Boolean.TRUE, mismatch.isError());
    McpSchema.TextContent mismatchText =
        assertInstanceOf(McpSchema.TextContent.class, mismatch.content().get(0));
    assertTrue(mismatchText.text().startsWith(Result.INVALID_ARGUMENT));
  }
}
