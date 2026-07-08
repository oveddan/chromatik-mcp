package lxmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import heronarts.lx.modulator.MacroKnobs;
import heronarts.lx.modulator.MacroTriggers;
import heronarts.lx.pattern.color.GradientPattern;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

import lxmcp.domain.Registry;
import lxmcp.domain.Resolve;
import lxmcp.engine.EngineExecutor;
import lxmcp.mcp.EmbeddedMcpServer;

/**
 * The integration gate from the qa-strategy per-tool template: every registered tool
 * served over real streamable-HTTP against a headless LX. A drainer thread stands in
 * for LX's engine thread (the headless harness never starts {@code lx.engine}), so the
 * blocking {@code EngineExecutor.call(...)} inside each handler completes.
 *
 * <p>One LX + server + client for the whole class ({@code @BeforeAll}), not per test:
 * repeated {@code new LX(...)} construction in one JVM deadlocks on the JDK-global
 * javax.sound/CoreMIDI lock (a previous instance's MIDI device-scan thread holds it
 * while the next construction enters audio init — the same hazard the surefire
 * {@code reuseForks=false} comment documents). Direct LX fixture mutation happens only
 * before the drainer starts; afterwards tests touch LX state exclusively through tool
 * calls (engine-task-marshalled) and assert against live state, so they stay
 * order-independent.
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
  void advertisesAllToolsWithReadOnlyHints() {
    McpSchema.ListToolsResult tools = client.listTools();
    Set<String> names = tools.tools().stream().map(McpSchema.Tool::name).collect(Collectors.toSet());
    assertEquals(
        Set.of("get_project_info", "list_channels", "list_available_patterns",
            "list_available_effects", "list_available_modulators", "get_parameter",
            "set_parameter", "add_modulator", "wire_modulator", "wire_trigger",
            "remove_modulation", "list_modulations", "fire_trigger"),
        names);
    Set<String> mutators = Set.of("set_parameter", "add_modulator", "wire_modulator",
        "wire_trigger", "remove_modulation", "fire_trigger");
    for (McpSchema.Tool tool : tools.tools()) {
      boolean expectReadOnly = !mutators.contains(tool.name());
      assertEquals(expectReadOnly, tool.annotations().readOnlyHint(),
          tool.name() + " readOnlyHint");
    }
  }

  @Test
  void setParameterOverMcpMutatesEngineState() {
    Map<String, Object> payload = structured(
        call("set_parameter", Map.of("path", channel.fader.getCanonicalPath(), "value", 0.5)));
    assertEquals(channel.fader.getCanonicalPath(), payload.get("path"));
    assertEquals(0.5, ((Number) payload.get("value")).doubleValue(), 1e-9);
    assertEquals(0.5, channel.fader.getValue(), 1e-9, "the live parameter changed");
  }

  @Test
  void setParameterWrongTypeIsInvalidArgument() {
    McpSchema.CallToolResult result = call("set_parameter",
        Map.of("path", channel.fader.getCanonicalPath(), "value", "loud"));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT), "type mismatch is invalid_argument");
  }

  @Test
  @SuppressWarnings("unchecked")
  void addModulatorOverMcpMutatesEngineState() {
    int before = lx.engine.modulation.modulators.size();

    Map<String, Object> payload = structured(
        call("add_modulator", Map.of("type", MacroKnobs.class.getName())));

    assertEquals(before + 1, lx.engine.modulation.modulators.size());
    assertEquals(MacroKnobs.class.getName(), payload.get("class"));
    assertEquals(Boolean.TRUE, payload.get("running"));
    String path = (String) payload.get("path");
    assertNotNull(path);
    // The returned path addresses the new modulator (the resolver round-trip).
    assertSame(lx.engine.modulation.modulators.get(before), Resolve.component(lx, path));

    // Every macro knob comes back with a label-based OSC address — the user goal.
    List<Map<String, Object>> parameters = (List<Map<String, Object>>) payload.get("parameters");
    List<Map<String, Object>> macros = parameters.stream()
        .filter(p -> ((String) p.get("path")).matches(".*/macro[1-8]$"))
        .toList();
    assertEquals(8, macros.size());
    for (Map<String, Object> macro : macros) {
      String oscAddress = (String) macro.get("oscAddress");
      assertNotNull(oscAddress);
      assertNotEquals(macro.get("path"), oscAddress,
          "modulator OSC addresses are label-based, not index-based");
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void addModulatorScopedToDeviceLandsInItsChain() {
    var pattern = channel.patterns.get(0);
    int before = pattern.modulation.modulators.size();

    Map<String, Object> payload = structured(call("add_modulator", Map.of(
        "type", MacroKnobs.class.getName(),
        "scope", pattern.getCanonicalPath())));

    assertEquals(before + 1, pattern.modulation.modulators.size(),
        "the modulator lands in the device's own engine");
    assertTrue(((String) payload.get("path")).startsWith(pattern.getCanonicalPath()));
  }

  @Test
  void wireModulatorThenRemoveModulation() {
    Map<String, Object> knobs = structured(
        call("add_modulator", Map.of("type", MacroKnobs.class.getName())));
    String macro1 = knobs.get("path") + "/macro1";
    int before = lx.engine.modulation.modulations.size();

    Map<String, Object> wired = structured(call("wire_modulator", Map.of(
        "source", macro1, "target", channel.fader.getCanonicalPath())));
    assertEquals(before + 1, lx.engine.modulation.modulations.size());
    assertEquals(lx.engine.modulation.getCanonicalPath(), wired.get("enginePath"));
    assertNotNull(wired.get("rangePath"), "modulation depth is set_parameter-able");

    Map<String, Object> removed = structured(
        call("remove_modulation", Map.of("path", wired.get("path"))));
    assertEquals("modulation", removed.get("kind"));
    assertEquals(before, lx.engine.modulation.modulations.size());
  }

  @Test
  void wireModulatorRejectsNonCompoundTarget() {
    Map<String, Object> knobs = structured(
        call("add_modulator", Map.of("type", MacroKnobs.class.getName())));
    McpSchema.CallToolResult result = call("wire_modulator", Map.of(
        "source", knobs.get("path") + "/macro1",
        "target", channel.enabled.getCanonicalPath()));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT),
        "a boolean cannot receive continuous modulation");
  }

  @Test
  void wireModulatorScopeViolationIsInvalidArgument() {
    var pattern = channel.patterns.get(0);
    Map<String, Object> knobs = structured(call("add_modulator", Map.of(
        "type", MacroKnobs.class.getName(), "scope", pattern.getCanonicalPath())));
    // Device knob -> global fader: out of the device engine's scope.
    McpSchema.CallToolResult result = call("wire_modulator", Map.of(
        "source", knobs.get("path") + "/macro1",
        "target", channel.fader.getCanonicalPath(),
        "scope", pattern.getCanonicalPath()));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  void wireModulatorInfersDeviceEngineFromSource() {
    var pattern = channel.patterns.get(0);
    Map<String, Object> knobs = structured(call("add_modulator", Map.of(
        "type", MacroKnobs.class.getName(), "scope", pattern.getCanonicalPath())));
    int before = pattern.modulation.modulations.size();

    // No scope arg: the device source's own engine hosts the wiring.
    Map<String, Object> wired = structured(call("wire_modulator", Map.of(
        "source", knobs.get("path") + "/macro1",
        "target", knobs.get("path") + "/macro2")));
    assertEquals(pattern.modulation.getCanonicalPath(), wired.get("enginePath"));
    assertEquals(before + 1, pattern.modulation.modulations.size());

    structured(call("remove_modulation", Map.of("path", wired.get("path"))));
  }

  @Test
  @SuppressWarnings("unchecked")
  void listAvailableModulatorsAdvertisesScopeFlags() {
    Map<String, Object> payload = structured(call("list_available_modulators", Map.of()));
    List<Map<String, Object>> modulators = (List<Map<String, Object>>) payload.get("modulators");
    Map<String, Object> knobs = modulators.stream()
        .filter(m -> MacroKnobs.class.getName().equals(m.get("class")))
        .findFirst().orElseThrow();
    assertEquals(Boolean.TRUE, knobs.get("global"), "add_modulator gates on these flags");
    assertEquals(Boolean.TRUE, knobs.get("device"));
  }

  @Test
  void wireTriggerOverMcp() {
    Map<String, Object> triggers = structured(
        call("add_modulator", Map.of("type", MacroTriggers.class.getName())));
    int before = lx.engine.modulation.triggers.size();

    Map<String, Object> wired = structured(call("wire_trigger", Map.of(
        "source", triggers.get("path") + "/macro1",
        "target", channel.enabled.getCanonicalPath())));
    assertEquals(before + 1, lx.engine.modulation.triggers.size());

    Map<String, Object> removed = structured(
        call("remove_modulation", Map.of("path", wired.get("path"))));
    assertEquals("trigger", removed.get("kind"));
    assertEquals(before, lx.engine.modulation.triggers.size());
  }

  @Test
  @SuppressWarnings("unchecked")
  void listModulationsDiscoversWirings() {
    Map<String, Object> knobs = structured(
        call("add_modulator", Map.of("type", MacroKnobs.class.getName())));
    Map<String, Object> wired = structured(call("wire_modulator", Map.of(
        "source", knobs.get("path") + "/macro1",
        "target", channel.fader.getCanonicalPath())));
    Map<String, Object> triggerBank = structured(
        call("add_modulator", Map.of("type", MacroTriggers.class.getName())));
    Map<String, Object> wiredTrigger = structured(call("wire_trigger", Map.of(
        "source", triggerBank.get("path") + "/macro1",
        "target", channel.enabled.getCanonicalPath())));

    Map<String, Object> payload = structured(call("list_modulations", Map.of()));
    List<Map<String, Object>> modulators = (List<Map<String, Object>>) payload.get("modulators");
    assertTrue(modulators.stream().anyMatch(m -> knobs.get("path").equals(m.get("path"))),
        "the added bank is discoverable");
    List<Map<String, Object>> modulations = (List<Map<String, Object>>) payload.get("modulations");
    Map<String, Object> entry = modulations.stream()
        .filter(m -> wired.get("path").equals(m.get("path"))).findFirst().orElseThrow();
    assertEquals(channel.fader.getCanonicalPath(), entry.get("targetPath"));
    assertNotNull(entry.get("id"), "component id rides along per tool-conventions");
    assertNotNull(entry.get("rangePath"), "depth is adjustable via set_parameter");
    List<Map<String, Object>> triggers = (List<Map<String, Object>>) payload.get("triggers");
    Map<String, Object> triggerEntry = triggers.stream()
        .filter(t -> wiredTrigger.get("path").equals(t.get("path"))).findFirst().orElseThrow();
    assertEquals(channel.enabled.getCanonicalPath(), triggerEntry.get("targetPath"));

    structured(call("remove_modulation", Map.of("path", wiredTrigger.get("path"))));
    structured(call("remove_modulation", Map.of("path", wired.get("path"))));
  }

  @Test
  void fireTriggerPulsesAMomentaryMacro() {
    Map<String, Object> triggers = structured(
        call("add_modulator", Map.of("type", MacroTriggers.class.getName())));

    Map<String, Object> payload = structured(
        call("fire_trigger", Map.of("path", triggers.get("path") + "/macro1")));
    assertEquals(triggers.get("path") + "/macro1", payload.get("path"));
    assertEquals(Boolean.TRUE, payload.get("fired"));
    assertEquals(Boolean.FALSE, payload.get("value"), "auto-reset after the pulse");

    // A toggle is rejected toward set_parameter.
    McpSchema.CallToolResult mismatch =
        call("fire_trigger", Map.of("path", channel.enabled.getCanonicalPath()));
    assertEquals(Boolean.TRUE, mismatch.isError());
    McpSchema.TextContent mismatchText =
        assertInstanceOf(McpSchema.TextContent.class, mismatch.content().get(0));
    assertTrue(mismatchText.text().startsWith(Result.INVALID_ARGUMENT));

    // An unknown path maps to not_found at the seam.
    McpSchema.CallToolResult missing =
        call("fire_trigger", Map.of("path", "/lx/nope/nothing"));
    assertEquals(Boolean.TRUE, missing.isError());
    McpSchema.TextContent missingText =
        assertInstanceOf(McpSchema.TextContent.class, missing.content().get(0));
    assertTrue(missingText.text().startsWith(Result.NOT_FOUND));
  }

  @Test
  void removeModulationUnknownPathIsNotFound() {
    McpSchema.CallToolResult result =
        call("remove_modulation", Map.of("path", "/lx/modulation/modulation/99"));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.NOT_FOUND));
  }

  @Test
  void getProjectInfoReturnsStructuredContentAndTextMirror() {
    McpSchema.CallToolResult result = call("get_project_info", Map.of());
    Map<String, Object> payload = structured(result);
    assertEquals(LX.VERSION, payload.get("lxVersion"));
    assertEquals(lx.engine.mixer.channels.size(),
        ((Number) payload.get("channelCount")).intValue());
    @SuppressWarnings("unchecked")
    Map<String, Object> osc = (Map<String, Object>) payload.get("osc");
    assertEquals(lx.engine.osc.receivePort.getValuei(),
        ((Number) osc.get("receivePort")).intValue());
    assertNotNull(osc.get("receiveActive"));

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
