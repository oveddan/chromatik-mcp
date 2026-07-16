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
import heronarts.lx.effect.BlurEffect;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.model.GridModel;
import heronarts.lx.modulator.MacroKnobs;
import heronarts.lx.modulator.MacroTriggers;
import heronarts.lx.pattern.color.GradientPattern;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

import lxmcp.ServerStatus;
import lxmcp.domain.Registry;
import lxmcp.domain.Resolve;
import lxmcp.engine.EngineExecutor;
import lxmcp.mcp.ConnectionTracker;
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
  private static ServerStatus status;
  private static McpSyncClient client;
  private static final AtomicBoolean draining = new AtomicBoolean(true);
  private static Thread drainer;

  @BeforeAll
  static void setUp() {
    // reindexPoints: the immutable-model LX constructor does not reindex (only
    // LXStructure.setStaticModel does), and LXPoint indices come from a JVM-global
    // counter — required for per-point readback (get_frame) to index buffers correctly.
    lx = new LX(new GridModel(8, 8).reindexPoints());
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

    status = new ServerStatus();
    ConnectionTracker connectionTracker = new ConnectionTracker();
    GetStatus getStatus = new GetStatus(
        status, () -> connectionTracker.snapshot(System.currentTimeMillis()));
    server = EmbeddedMcpServer.start("LX-MCP", "0.0.1-test", 0, "127.0.0.1",
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
    if (lx != null) {
      lx.dispose();
    }
  }

  private static McpSchema.CallToolResult call(String tool, Map<String, Object> args) {
    try {
      return client.callTool(new McpSchema.CallToolRequest(tool, args));
    } catch (RuntimeException e) {
      // The JDK HttpClient occasionally reuses a pooled keep-alive connection the server
      // side already closed between tests, surfacing as a wrapped IOException ("HTTP/1.1
      // header parser received no bytes") rather than a real product failure. One retry on
      // a fresh connection is sound here even for mutating tools: the failure signature
      // means zero response bytes were parsed (stale connection died before the request
      // landed), and each test's own assertions verify resulting state against live
      // lx.engine.* regardless of how many attempts the call took.
      if (isHeaderParserNoBytes(e)) {
        return client.callTool(new McpSchema.CallToolRequest(tool, args));
      }
      throw e;
    }
  }

  private static boolean isHeaderParserNoBytes(Throwable t) {
    for (Throwable cause = t; cause != null; cause = cause.getCause()) {
      if (cause instanceof java.io.IOException
          && cause.getMessage() != null
          && cause.getMessage().contains("header parser received no bytes")) {
        return true;
      }
    }
    return false;
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
        Set.of("get_project_info", "get_status", "list_channels", "list_available_patterns",
            "list_available_effects", "list_available_modulators", "get_parameter",
            "list_parameters", "set_parameter", "add_modulator", "wire_modulator", "wire_trigger",
            "remove_modulation", "list_modulations", "fire_trigger", "get_component_doc",
            "get_frame", "get_palette",
            "add_channel", "remove_channel", "add_pattern", "remove_pattern",
            "activate_pattern", "move_pattern", "add_effect", "remove_effect", "move_effect"),
        names);
    Set<String> mutators = Set.of("set_parameter", "add_modulator", "wire_modulator",
        "wire_trigger", "remove_modulation", "fire_trigger",
        "add_channel", "remove_channel", "add_pattern", "remove_pattern",
        "activate_pattern", "move_pattern", "add_effect", "remove_effect", "move_effect");
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
  void wireModulatorWithRangeAppliesInitialDepth() {
    Map<String, Object> knobs = structured(
        call("add_modulator", Map.of("type", MacroKnobs.class.getName())));
    String macro1 = knobs.get("path") + "/macro1";

    Map<String, Object> wired = structured(call("wire_modulator", Map.of(
        "source", macro1, "target", channel.fader.getCanonicalPath(), "range", 0.75)));
    assertEquals(0.75, ((Number) wired.get("range")).doubleValue(), 1e-9);

    Map<String, Object> rangeParam = structured(
        call("get_parameter", Map.of("path", wired.get("rangePath"))));
    assertEquals(0.75, ((Number) rangeParam.get("value")).doubleValue(), 1e-9);

    structured(call("remove_modulation", Map.of("path", wired.get("path"))));
  }

  @Test
  void wireModulatorRangeOutOfBoundsIsInvalidArgument() {
    Map<String, Object> knobs = structured(
        call("add_modulator", Map.of("type", MacroKnobs.class.getName())));
    String macro1 = knobs.get("path") + "/macro1";

    McpSchema.CallToolResult result = call("wire_modulator", Map.of(
        "source", macro1, "target", channel.fader.getCanonicalPath(), "range", 2.0));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
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

  // ── Channel tool integration tests ──────────────────────────────────────────

  @Test
  @SuppressWarnings("unchecked")
  void addChannelAndRemoveChannel() {
    int before = lx.engine.mixer.channels.size();

    Map<String, Object> added = structured(call("add_channel", Map.of()));
    String channelPath = (String) added.get("path");
    try {
      assertNotNull(channelPath);
      assertEquals(before + 1, lx.engine.mixer.channels.size());
    } finally {
      Map<String, Object> removed = structured(call("remove_channel", Map.of("path", channelPath)));
      assertEquals(channelPath, removed.get("removed"));
    }
    assertEquals(before, lx.engine.mixer.channels.size());
  }

  @Test
  @SuppressWarnings("unchecked")
  void addChannelWithPatternSeededOnCreation() {
    int before = lx.engine.mixer.channels.size();

    Map<String, Object> added = structured(call("add_channel",
        Map.of("pattern", GradientPattern.class.getName())));
    String channelPath = (String) added.get("path");
    try {
      assertNotNull(channelPath);
      assertEquals(before + 1, lx.engine.mixer.channels.size());

      // Verify pattern seeded — the channel's patterns list should have one entry
      Map<String, Object> channels = structured(call("list_channels", Map.of()));
      List<Map<String, Object>> channelList = (List<Map<String, Object>>) channels.get("channels");
      Map<String, Object> newChannel = channelList.stream()
          .filter(c -> channelPath.equals(c.get("path")))
          .findFirst().orElseThrow();
      assertEquals("playlist", newChannel.get("patternMode"));
      List<Map<String, Object>> patterns = (List<Map<String, Object>>) newChannel.get("patterns");
      assertEquals(1, patterns.size());
      assertEquals(GradientPattern.class.getName(), patterns.get(0).get("class"));
      assertEquals(Boolean.TRUE, patterns.get(0).get("enabled"));
      assertEquals(Boolean.TRUE, patterns.get(0).get("contributing"));
      assertFalse(patterns.get(0).containsKey("compositeLevel"),
          "compositeLevel is only emitted for blend-mode channels");
    } finally {
      structured(call("remove_channel", Map.of("path", channelPath)));
    }
    assertEquals(before, lx.engine.mixer.channels.size());
  }

  @Test
  @SuppressWarnings("unchecked")
  void addPatternActivatePatternFlow() {
    // Add a fresh channel for isolation
    Map<String, Object> ch = structured(call("add_channel", Map.of()));
    String channelPath = (String) ch.get("path");
    try {
      // Add first pattern
      Map<String, Object> p1 = structured(call("add_pattern", Map.of(
          "channel", channelPath, "type", GradientPattern.class.getName())));
      String p1path = (String) p1.get("path");
      assertNotNull(p1path);

      // Add second pattern
      Map<String, Object> p2 = structured(call("add_pattern", Map.of(
          "channel", channelPath, "type", GradientPattern.class.getName())));
      String p2path = (String) p2.get("path");

      // Activate the second pattern
      Map<String, Object> activated = structured(
          call("activate_pattern", Map.of("path", p2path)));
      assertEquals(p2path, activated.get("path"));
      assertEquals(Boolean.TRUE, activated.get("active"));
    } finally {
      structured(call("remove_channel", Map.of("path", channelPath)));
    }
  }

  @Test
  void activatePatternUnknownPathIsNotFound() {
    McpSchema.CallToolResult result =
        call("activate_pattern", Map.of("path", "/lx/mixer/channel/999/pattern/1"));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.NOT_FOUND));
  }

  @Test
  @SuppressWarnings("unchecked")
  void addEffectMoveEffectRemoveEffect() {
    Map<String, Object> ch = structured(call("add_channel", Map.of()));
    String channelPath = (String) ch.get("path");
    try {
      Map<String, Object> e1 = structured(call("add_effect", Map.of(
          "container", channelPath, "type", BlurEffect.class.getName())));
      String e1path = (String) e1.get("path");
      assertNotNull(e1path);

      Map<String, Object> e2 = structured(call("add_effect", Map.of(
          "container", channelPath, "type", BlurEffect.class.getName())));
      String e2path = (String) e2.get("path");

      // Move e1 to index 1
      Map<String, Object> moved = structured(
          call("move_effect", Map.of("path", e1path, "index", 1)));
      // After moving e1 to index 1, its path may change (index-based paths shift)
      assertNotNull(moved.get("path"));
      assertEquals(1, ((Number) moved.get("index")).intValue());

      // Remove e2 (still at index 0 after e1 moved)
      structured(call("remove_effect", Map.of("path", e2path)));
    } finally {
      structured(call("remove_channel", Map.of("path", channelPath)));
    }
  }

  @Test
  void addEffectInvalidContainerIsInvalidArgument() {
    McpSchema.CallToolResult result = call("add_effect", Map.of(
        "container", "/lx/mixer", "type", BlurEffect.class.getName()));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  void removeChannelUnknownPathIsNotFound() {
    McpSchema.CallToolResult result =
        call("remove_channel", Map.of("path", "/lx/mixer/channel/999"));
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

    @SuppressWarnings("unchecked")
    Map<String, Object> output = (Map<String, Object>) payload.get("output");
    assertNotNull(output, "get_project_info reports engine output state");
    assertEquals(lx.engine.output.enabled.isOn(), output.get("enabled"));
    assertEquals(lx.engine.output.enabled.getCanonicalPath(), output.get("enabledPath"));
    assertEquals(lx.engine.output.brightness.getValue(),
        ((Number) output.get("brightness")).doubleValue());
    assertEquals(lx.engine.output.brightness.getCanonicalPath(), output.get("brightnessPath"));

    assertFalse(result.content().isEmpty(), "success also carries a text mirror");
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith("{"), "text mirror is the serialized JSON payload");
  }

  @Test
  @SuppressWarnings("unchecked")
  void getStatusReportsServerAndConnectionState() {
    Map<String, Object> payload = structured(call("get_status", Map.of()));
    assertEquals("127.0.0.1", payload.get("host"));
    assertEquals(server.port(), ((Number) payload.get("port")).intValue());
    assertEquals("http://127.0.0.1:" + server.port() + EmbeddedMcpServer.ENDPOINT, payload.get("url"));
    assertNotNull(payload.get("startedAt"));
    assertNotNull(payload.get("uptimeSeconds"));

    Map<String, Object> connection = (Map<String, Object>) payload.get("connection");
    assertNotNull(connection, "connection state is reported");
    // This test's own call is itself MCP activity, so the server sees an open stream.
    assertEquals(Boolean.TRUE, connection.get("connected"));
    assertNotNull(connection.get("activeStreams"));
  }

  @Test
  void initializeResultCarriesServerInstructions() {
    String instructions = client.getServerInstructions();
    assertNotNull(instructions, "initialize result should carry mixer-semantics instructions");
    assertTrue(instructions.contains("patternMode"), "instructions cover mixer semantics");
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
    assertEquals("playlist", entry.get("patternMode"));
    List<Map<String, Object>> patterns = (List<Map<String, Object>>) entry.get("patterns");
    assertEquals(channel.patterns.size(), patterns.size());
    for (Map<String, Object> pattern : patterns) {
      assertNotNull(pattern.get("active"));
      assertNotNull(pattern.get("enabled"));
      assertNotNull(pattern.get("contributing"));
    }

    assertNotNull(payload.get("master"), "master bus is always present");
  }

  @Test
  @SuppressWarnings("unchecked")
  void listChannelsIncludesEffectsHostedOnAPattern() {
    Map<String, Object> ch = structured(call("add_channel", Map.of()));
    String channelPath = (String) ch.get("path");
    try {
      String patternPath = (String) structured(call("add_pattern", Map.of(
          "channel", channelPath, "type", GradientPattern.class.getName()))).get("path");

      // Assert empty effects list before adding effect
      Map<String, Object> beforeAddEffectPayload = structured(call("list_channels", Map.of()));
      List<Map<String, Object>> beforeAddEffectChannels = (List<Map<String, Object>>) beforeAddEffectPayload.get("channels");
      Map<String, Object> beforeAddEffectChannelEntry = beforeAddEffectChannels.stream()
          .filter(c -> channelPath.equals(c.get("path")))
          .findFirst()
          .orElseThrow(() -> new AssertionError("channel not found in list_channels"));
      List<Map<String, Object>> beforeAddEffectPatterns = (List<Map<String, Object>>) beforeAddEffectChannelEntry.get("patterns");
      Map<String, Object> beforeAddEffectPatternEntry = beforeAddEffectPatterns.stream()
          .filter(p -> patternPath.equals(p.get("path")))
          .findFirst()
          .orElseThrow(() -> new AssertionError("pattern not found in list_channels"));
      List<Map<String, Object>> beforeAddEffectEffects = (List<Map<String, Object>>) beforeAddEffectPatternEntry.get("effects");
      assertEquals(0, beforeAddEffectEffects.size(), "pattern should have empty effects list before add_effect");

      Map<String, Object> effect = structured(call("add_effect", Map.of(
          "container", patternPath, "type", BlurEffect.class.getName())));
      String effectPath = (String) effect.get("path");

      Map<String, Object> payload = structured(call("list_channels", Map.of()));
      List<Map<String, Object>> channels = (List<Map<String, Object>>) payload.get("channels");
      Map<String, Object> channelEntry = channels.stream()
          .filter(c -> channelPath.equals(c.get("path")))
          .findFirst()
          .orElseThrow(() -> new AssertionError("channel not found in list_channels"));
      List<Map<String, Object>> patterns = (List<Map<String, Object>>) channelEntry.get("patterns");
      Map<String, Object> patternEntry = patterns.stream()
          .filter(p -> patternPath.equals(p.get("path")))
          .findFirst()
          .orElseThrow(() -> new AssertionError("pattern not found in list_channels"));

      List<Map<String, Object>> patternEffects = (List<Map<String, Object>>) patternEntry.get("effects");
      assertEquals(1, patternEffects.size());
      assertEquals(effectPath, patternEffects.get(0).get("path"));
      assertEquals(BlurEffect.class.getName(), patternEffects.get(0).get("class"));
    } finally {
      structured(call("remove_channel", Map.of("path", channelPath)));
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void listChannelsBlendModeEmitsCompositeLevelAndContributing() {
    Map<String, Object> ch = structured(call("add_channel", Map.of()));
    String channelPath = (String) ch.get("path");
    try {
      String p1path = (String) structured(call("add_pattern", Map.of(
          "channel", channelPath, "type", GradientPattern.class.getName()))).get("path");
      String p2path = (String) structured(call("add_pattern", Map.of(
          "channel", channelPath, "type", GradientPattern.class.getName()))).get("path");

      // Flip the channel to BLEND (CompositeMode ordinal 1 on the pattern engine's
      // compositeMode EnumParameter, registered on the channel).
      structured(call("set_parameter", Map.of(
          "path", channelPath + "/compositeMode", "value", 1)));
      structured(call("set_parameter", Map.of("path", p1path + "/enabled", "value", false)));
      structured(call("set_parameter", Map.of("path", p2path + "/enabled", "value", true)));
      structured(call("set_parameter", Map.of("path", p2path + "/compositeLevel", "value", 0.8)));

      Map<String, Object> payload = structured(call("list_channels", Map.of()));
      Map<String, Object> entry = ((List<Map<String, Object>>) payload.get("channels")).stream()
          .filter(c -> channelPath.equals(c.get("path")))
          .findFirst().orElseThrow();
      assertEquals("blend", entry.get("patternMode"));

      List<Map<String, Object>> patterns = (List<Map<String, Object>>) entry.get("patterns");
      Map<String, Object> p1 = patterns.stream()
          .filter(p -> p1path.equals(p.get("path"))).findFirst().orElseThrow();
      assertEquals(Boolean.FALSE, p1.get("enabled"));
      assertNotNull(p1.get("compositeLevel"), "blend mode emits compositeLevel");
      assertEquals(Boolean.FALSE, p1.get("contributing"),
          "disabled pattern does not contribute in blend mode");

      Map<String, Object> p2 = patterns.stream()
          .filter(p -> p2path.equals(p.get("path"))).findFirst().orElseThrow();
      assertEquals(Boolean.TRUE, p2.get("enabled"));
      assertEquals(0.8, ((Number) p2.get("compositeLevel")).doubleValue(), 1e-9);
      assertEquals(Boolean.TRUE, p2.get("contributing"),
          "enabled pattern with compositeLevel > 0 contributes in blend mode");
    } finally {
      structured(call("remove_channel", Map.of("path", channelPath)));
    }
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
  void getParameterReportsEffectiveModulatedValueOverHttp() {
    double originalFader = channel.fader.getValue();
    Map<String, Object> knobs = structured(
        call("add_modulator", Map.of("type", MacroKnobs.class.getName())));
    String macro1 = knobs.get("path") + "/macro1";
    structured(call("set_parameter",
        Map.of("path", channel.fader.getCanonicalPath(), "value", 0.0)));
    // A deterministic source value (unlike an LFO) so the effective reading is exact.
    structured(call("set_parameter", Map.of("path", macro1, "value", 0.5)));
    Map<String, Object> wired = structured(call("wire_modulator",
        Map.of("source", macro1, "target", channel.fader.getCanonicalPath())));
    try {
      structured(call("set_parameter",
          Map.of("path", (String) wired.get("rangePath"), "value", 1.0)));

      Map<String, Object> payload = structured(
          call("get_parameter", Map.of("path", channel.fader.getCanonicalPath())));
      assertEquals(Boolean.TRUE, payload.get("modulated"));
      assertEquals(0.5, ((Number) payload.get("value")).doubleValue(), 1e-9,
          "value is the live effective reading, not a frozen base snapshot");
      assertEquals(0.0, ((Number) payload.get("baseValue")).doubleValue(), 1e-9);
      assertEquals(0.5, ((Number) payload.get("normalized")).doubleValue(), 1e-9);
      assertEquals(0.0, ((Number) payload.get("baseNormalized")).doubleValue(), 1e-9);

      // Unmodulated parameters carry none of the new fields — no noise on the common case.
      Map<String, Object> unmodulated =
          structured(call("get_parameter", Map.of("path", macro1)));
      assertFalse(unmodulated.containsKey("modulated"));
      assertFalse(unmodulated.containsKey("baseValue"));
      assertFalse(unmodulated.containsKey("baseNormalized"));
    } finally {
      structured(call("remove_modulation", Map.of("path", wired.get("path"))));
      structured(call("set_parameter",
          Map.of("path", channel.fader.getCanonicalPath(), "value", originalFader)));
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void listParametersDescribesChannel() {
    Map<String, Object> payload =
        structured(call("list_parameters", Map.of("path", channel.getCanonicalPath())));
    assertEquals(channel.getCanonicalPath(), payload.get("path"));
    assertEquals(channel.getId(), ((Number) payload.get("id")).intValue());
    assertEquals(channel.getLabel(), payload.get("label"));

    List<Map<String, Object>> parameters = (List<Map<String, Object>>) payload.get("parameters");
    assertFalse(parameters.isEmpty());
    Map<String, Object> fader = parameters.stream()
        .filter(p -> channel.fader.getCanonicalPath().equals(p.get("path")))
        .findFirst().orElseThrow(() -> new AssertionError("fader not listed"));
    assertEquals(channel.fader.getValue(), ((Number) fader.get("value")).doubleValue(), 1e-9);
    Map<String, Object> enabled = parameters.stream()
        .filter(p -> channel.enabled.getCanonicalPath().equals(p.get("path")))
        .findFirst().orElseThrow(() -> new AssertionError("enabled not listed"));
    assertEquals(channel.enabled.isOn(), enabled.get("value"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void listParametersDescribesChannelChildPatterns() {
    Map<String, Object> payload =
        structured(call("list_parameters", Map.of("path", channel.getCanonicalPath())));

    List<Map<String, Object>> children = (List<Map<String, Object>>) payload.get("children");
    assertFalse(children.isEmpty());
    Map<String, Object> patternChild = children.stream()
        .filter(c -> "pattern".equals(c.get("key")))
        .findFirst().orElseThrow(() -> new AssertionError("channel's pattern child not listed"));
    assertEquals(channel.getActivePattern().getCanonicalPath(), patternChild.get("path"));
    assertEquals(channel.getActivePattern().getClass().getName(), patternChild.get("class"));
  }

  @Test
  void listParametersUnknownPathIsNotFound() {
    McpSchema.CallToolResult result =
        call("list_parameters", Map.of("path", "/lx/nope/nothing"));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.NOT_FOUND));
  }

  @Test
  void listParametersOnAParameterPathIsInvalidArgument() {
    McpSchema.CallToolResult result =
        call("list_parameters", Map.of("path", channel.fader.getCanonicalPath()));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
    assertTrue(text.text().contains("get_parameter"), "points the caller at get_parameter");
  }

  @Test
  void getParameterUnknownPathIsResultError() {
    McpSchema.CallToolResult result = call("get_parameter", Map.of("path", "/lx/nope/nothing"));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.NOT_FOUND), "error text leads with the stable code");
  }

  @Test
  @SuppressWarnings("unchecked")
  void getComponentDocDocumentedClass() {
    Map<String, Object> payload = structured(
        call("get_component_doc", Map.of("class", GradientPattern.class.getName())));
    assertEquals(GradientPattern.class.getName(), payload.get("class"));
    assertEquals(Boolean.TRUE, payload.get("documented"));
    assertNotNull(payload.get("summary"), "summary is present");
    assertFalse(((String) payload.get("summary")).isEmpty(), "summary is non-empty");

    Map<String, Object> catalog = (Map<String, Object>) payload.get("catalog");
    assertNotNull(catalog, "catalog metadata present for documented class");
    assertNotNull(catalog.get("generatedAt"));
    assertNotNull(catalog.get("lxVersion"));
    assertNotNull(catalog.get("stale"), "stale field always present");
    // Test classpath uses the same ~/.m2 jar as generation: stale should be false.
    // If bytes differ (fresh rebuild), assert that the stale value is a boolean.
    Object stale = catalog.get("stale");
    assertTrue(stale instanceof Boolean, "stale is a boolean when bytecode is readable");
    assertEquals("class-jar", catalog.get("source"));
  }

  @Test
  void getComponentDocUndocumentedClass() {
    // MacroKnobs is registered but has no catalog entry in the lx-mcp jar.
    Map<String, Object> payload = structured(
        call("get_component_doc", Map.of("class", MacroKnobs.class.getName())));
    assertEquals(MacroKnobs.class.getName(), payload.get("class"));
    assertEquals(Boolean.FALSE, payload.get("documented"));
    // No error — undocumented is a valid, expected state.
    assertFalse(payload.containsKey("summary"), "no summary for undocumented class");
    assertFalse(payload.containsKey("catalog"), "no catalog metadata for undocumented class");
  }

  @Test
  void getComponentDocUnknownClassIsNotFound() {
    McpSchema.CallToolResult result = call("get_component_doc",
        Map.of("class", "com.example.NoSuchPattern"));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.NOT_FOUND));
  }

  @Test
  void getComponentDocMissingArgIsRejected() {
    McpSchema.CallToolResult result = call("get_component_doc", Map.of());
    assertEquals(Boolean.TRUE, result.isError());
  }

  @Test
  @SuppressWarnings("unchecked")
  void listAvailablePatternsCarryDocumentedFlag() {
    Map<String, Object> payload = structured(call("list_available_patterns", Map.of()));
    List<Map<String, Object>> patterns = (List<Map<String, Object>>) payload.get("patterns");
    assertFalse(patterns.isEmpty(), "at least one pattern is registered");
    for (Map<String, Object> entry : patterns) {
      assertTrue(entry.containsKey("documented"),
          "every pattern entry carries a documented flag: " + entry.get("class"));
    }
    // GradientPattern has a catalog entry — verify it's flagged documented.
    Map<String, Object> gradient = patterns.stream()
        .filter(p -> GradientPattern.class.getName().equals(p.get("class")))
        .findFirst().orElseThrow(() -> new AssertionError("GradientPattern not in registry"));
    assertEquals(Boolean.TRUE, gradient.get("documented"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void listAvailableModulatorsCarryDocumentedFlag() {
    Map<String, Object> payload = structured(call("list_available_modulators", Map.of()));
    List<Map<String, Object>> modulators = (List<Map<String, Object>>) payload.get("modulators");
    Map<String, Object> knobs = modulators.stream()
        .filter(m -> MacroKnobs.class.getName().equals(m.get("class")))
        .findFirst().orElseThrow();
    assertEquals(Boolean.FALSE, knobs.get("documented"),
        "MacroKnobs has no catalog entry");
  }

  @Test
  @SuppressWarnings("unchecked")
  void getFrameReturnsImageAndSummary() throws java.io.IOException {
    McpSchema.CallToolResult result =
        call("get_frame", Map.of("width", 128, "grid", 2, "include_image", true));
    Map<String, Object> payload = structured(result);
    assertEquals("main", payload.get("bus"));
    assertEquals("front", payload.get("view"));
    assertEquals(64, ((Number) payload.get("points")).intValue());
    assertNotNull(payload.get("nonBlackFraction"));
    assertNotNull(payload.get("dominantColors"));
    List<List<String>> grid = (List<List<String>>) payload.get("grid");
    assertEquals(2, grid.size());
    assertEquals(2, grid.get(0).size());

    // Content: text mirror first, then the PNG as ImageContent.
    McpSchema.ImageContent image =
        assertInstanceOf(McpSchema.ImageContent.class, result.content().get(1));
    assertEquals("image/png", image.mimeType());
    byte[] png = java.util.Base64.getDecoder().decode(image.data());
    java.awt.image.BufferedImage decoded =
        javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
    assertNotNull(decoded, "base64 decodes to a readable PNG");
    assertEquals(128, decoded.getWidth(), "requested width honored");
  }

  @Test
  void getFrameSummaryOnlySkipsImage() {
    McpSchema.CallToolResult result = call("get_frame", Map.of("include_image", false));
    Map<String, Object> payload = structured(result);
    assertEquals(64, ((Number) payload.get("points")).intValue());
    assertFalse(payload.containsKey("imageWidth"), "no image metadata in summary-only mode");
    for (McpSchema.Content content : result.content()) {
      assertFalse(content instanceof McpSchema.ImageContent, "no ImageContent when opted out");
    }
  }

  @Test
  void getFrameDefaultsToSummaryOnly() {
    McpSchema.CallToolResult result = call("get_frame", Map.of());
    Map<String, Object> payload = structured(result);
    assertEquals(64, ((Number) payload.get("points")).intValue());
    assertFalse(payload.containsKey("imageWidth"), "no image metadata by default");
    for (McpSchema.Content content : result.content()) {
      assertFalse(content instanceof McpSchema.ImageContent, "no ImageContent unless requested");
    }
  }

  @Test
  void getFrameBadViewIsInvalidArgument() {
    // The SDK rejects non-enum values via inputSchema before the handler; an empty string
    // for a defaulted arg falls back rather than erroring, so exercise the handler check
    // directly with a value the schema can't catch — none exists for enums, so pin the
    // SDK-side rejection instead.
    McpSchema.CallToolResult result = call("get_frame", Map.of("view", "diagonal"));
    assertEquals(Boolean.TRUE, result.isError());
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

  @Test
  @SuppressWarnings("unchecked")
  void getPaletteDescribesActiveSwatchAndSavedSwatches() {
    heronarts.lx.color.LXSwatch saved = lx.engine.palette.saveSwatch();
    saved.label.setValue("Cool");
    try {
      Map<String, Object> payload = structured(call("get_palette", Map.of()));

      Map<String, Object> activeSwatch = (Map<String, Object>) payload.get("activeSwatch");
      assertEquals(lx.engine.palette.swatch.getCanonicalPath(), activeSwatch.get("path"));
      List<Map<String, Object>> colors = (List<Map<String, Object>>) activeSwatch.get("colors");
      assertEquals(lx.engine.palette.swatch.colors.size(), colors.size());
      Map<String, Object> firstColor = colors.get(0);
      assertNotNull(firstColor.get("path"));
      assertNotNull(firstColor.get("mode"));
      assertTrue(((String) firstColor.get("effectiveColor")).startsWith("0x"));
      assertNotNull(firstColor.get("primaryPath"));
      assertNotNull(firstColor.get("secondaryPath"));

      List<Map<String, Object>> swatches = (List<Map<String, Object>>) payload.get("swatches");
      Map<String, Object> savedEntry = swatches.stream()
          .filter(s -> saved.getCanonicalPath().equals(s.get("path")))
          .findFirst().orElseThrow(() -> new AssertionError("saved swatch not listed"));
      assertEquals("Cool", savedEntry.get("label"));
      assertEquals(saved.recall.getCanonicalPath(), savedEntry.get("recallPath"));
      assertEquals(saved.autoCycleEligible.isOn(), savedEntry.get("autoCycleEligible"));

      Map<String, Object> transition = (Map<String, Object>) payload.get("transition");
      assertEquals(lx.engine.palette.transitionEnabled.isOn(), transition.get("enabled"));
      assertEquals(lx.engine.palette.transitionTimeSecs.getValue(),
          ((Number) transition.get("timeSecs")).doubleValue(), 1e-9);

      Map<String, Object> autoCycle = (Map<String, Object>) payload.get("autoCycle");
      assertEquals(lx.engine.palette.autoCycleEnabled.isOn(), autoCycle.get("enabled"));

      // Recall via fire_trigger loads the saved swatch's colors onto the active swatch.
      lx.engine.palette.transitionEnabled.setValue(false);
      saved.colors.get(0).primary.setColor(0xff123456);
      Map<String, Object> fired = structured(call("fire_trigger", Map.of("path", saved.recall.getCanonicalPath())));
      assertEquals(Boolean.TRUE, fired.get("fired"));
      assertEquals(0xff123456, lx.engine.palette.swatch.colors.get(0).getColor(),
          "recall (not transitioning) loads the saved swatch's colors immediately");
    } finally {
      lx.engine.palette.removeSwatch(saved);
    }
  }
}
