package chromatikmcp.tools;

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
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import heronarts.lx.LX;
import heronarts.lx.LXPath;
import heronarts.lx.color.LXDynamicColor;
import heronarts.lx.color.LXSwatch;
import heronarts.lx.command.LXCommand;
import heronarts.lx.effect.BlurEffect;
import heronarts.lx.midi.MidiControlChange;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXGroup;
import heronarts.lx.model.GridModel;
import heronarts.lx.modulator.MacroKnobs;
import heronarts.lx.modulator.MacroTriggers;
import heronarts.lx.pattern.PatternRack;
import heronarts.lx.pattern.color.GradientPattern;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

import chromatikmcp.ServerStatus;
import chromatikmcp.domain.Registry;
import chromatikmcp.domain.Resolve;
import chromatikmcp.engine.EngineExecutor;
import chromatikmcp.mcp.ConnectionTracker;
import chromatikmcp.mcp.EmbeddedMcpServer;

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

  // @AutoClose runs after the @AfterAll method, so the drainer is stopped before disposal.
  @AutoClose("dispose")
  private static LX lx;
  private static LXChannel channel;
  private static heronarts.lx.structure.view.LXViewDefinition view;
  private static EmbeddedMcpServer server;
  private static ServerStatus status;
  private static McpSyncClient client;
  private static final AtomicBoolean draining = new AtomicBoolean(true);
  private static Thread drainer;

  /**
   * Registered but never cataloged: the fixture for undocumented-class assertions.
   * Every stock DEFAULT_* class now ships a catalog entry, so an undocumented state
   * must come from a class that can never gain one.
   */
  public static class UndocumentedModulator extends heronarts.lx.modulator.LXModulator {
    public UndocumentedModulator() {
      super("Undocumented");
    }

    @Override
    protected double computeValue(double deltaMs) {
      return 0;
    }
  }

  @BeforeAll
  static void setUp() {
    // reindexPoints: the immutable-model LX constructor does not reindex (only
    // LXStructure.setStaticModel does), and LXPoint indices come from a JVM-global
    // counter — required for per-point readback (get_frame) to index buffers correctly.
    lx = new LX(new GridModel(8, 8).reindexPoints());
    lx.registry.addModulator(UndocumentedModulator.class);
    channel = lx.engine.mixer.addChannel();
    channel.addPattern(new GradientPattern(lx));
    // GridModel's root carries the "grid" tag (LXModel.Tag.GRID) but has no submodel
    // children, so a "grid" selector round-trips through the resolver/get_views wire shape
    // without matching any fixtures (LXView selectors only ever match descendant submodels).
    view = lx.structure.views.addView();
    view.label.setValue("Whole Grid");
    view.selector.setValue("grid");

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
        Set.of("get_project_info", "get_status", "list_channels", "get_channel", "list_available_patterns",
            "list_available_effects", "list_available_modulators", "get_parameter",
            "list_parameters", "set_parameter", "add_modulator", "wire_modulator", "wire_trigger",
            "remove_modulation", "remove_modulator", "list_modulations", "fire_trigger",
            "get_component_doc", "get_fixture_format",
            "get_frame", "get_palette", "describe_model", "get_views", "add_view", "remove_view",
            "list_fixtures", "get_fixture", "get_output_map", "list_available_fixtures", "add_fixture",
            "remove_fixture", "move_fixture", "duplicate_fixture",
            "set_fixture_params", "set_fixture_tags", "reload_fixtures",
            "add_channel", "remove_channel", "add_pattern", "remove_pattern",
            "activate_pattern", "move_pattern", "add_effect", "remove_effect", "move_effect",
            "get_tempo",
            "list_midi_devices", "list_midi_mappings", "list_midi_surfaces",
            "add_midi_mapping", "remove_midi_mapping", "set_midi_input", "set_midi_surface_enabled",
            "save_swatch", "set_swatch", "remove_swatch", "move_swatch", "add_color",
            "remove_color",
            "list_snapshots", "add_snapshot", "recall_snapshot",
            "update_snapshot", "remove_snapshot",
            "apply_operations"),
        names);
    Set<String> mutators = Set.of("set_parameter", "add_modulator", "wire_modulator",
        "wire_trigger", "remove_modulation", "remove_modulator", "fire_trigger",
        "add_view", "remove_view", "add_fixture", "remove_fixture", "move_fixture",
        "duplicate_fixture", "set_fixture_params", "set_fixture_tags", "reload_fixtures",
        "add_channel", "remove_channel", "add_pattern", "remove_pattern",
        "activate_pattern", "move_pattern", "add_effect", "remove_effect", "move_effect",
        "save_swatch", "set_swatch", "remove_swatch", "move_swatch", "add_color",
        "remove_color",
        "add_snapshot", "recall_snapshot", "update_snapshot", "remove_snapshot",
        "add_midi_mapping", "remove_midi_mapping", "set_midi_input", "set_midi_surface_enabled",
        "apply_operations");
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
    assertEquals(channel.fader.getLabel(), payload.get("label"), "same full field set as get_parameter");
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
        call("add_modulator", Map.of("class", MacroKnobs.class.getName())));

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
  void addModulatorAcceptsShortTypeName() {
    int before = lx.engine.modulation.modulators.size();

    // The short name list_available_modulators advertises for MacroKnobs is also
    // MacroKnobs — this exercises the same simple-name lookup path used by any short name.
    Map<String, Object> payload = structured(
        call("add_modulator", Map.of("class", "MacroKnobs")));

    assertEquals(before + 1, lx.engine.modulation.modulators.size());
    assertEquals(MacroKnobs.class.getName(), payload.get("class"),
        "the payload still reports the resolved full class name");
  }

  @Test
  @SuppressWarnings("unchecked")
  void addModulatorScopedToDeviceLandsInItsChain() {
    var pattern = channel.patterns.get(0);
    int before = pattern.modulation.modulators.size();

    Map<String, Object> payload = structured(call("add_modulator", Map.of(
        "class", MacroKnobs.class.getName(),
        "scope", pattern.getCanonicalPath())));

    assertEquals(before + 1, pattern.modulation.modulators.size(),
        "the modulator lands in the device's own engine");
    assertTrue(((String) payload.get("path")).startsWith(pattern.getCanonicalPath()));
  }

  @Test
  void wireModulatorThenRemoveModulation() {
    Map<String, Object> knobs = structured(
        call("add_modulator", Map.of("class", MacroKnobs.class.getName())));
    String macro1 = knobs.get("path") + "/macro1";
    int before = lx.engine.modulation.modulations.size();

    Map<String, Object> wired = structured(call("wire_modulator", Map.of(
        "sourcePath", macro1, "targetPath", channel.fader.getCanonicalPath())));
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
        call("add_modulator", Map.of("class", MacroKnobs.class.getName())));
    McpSchema.CallToolResult result = call("wire_modulator", Map.of(
        "sourcePath", knobs.get("path") + "/macro1",
        "targetPath", channel.enabled.getCanonicalPath()));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT),
        "a boolean cannot receive continuous modulation");
  }

  @Test
  void wireModulatorScopeViolationIsInvalidArgument() {
    var pattern = channel.patterns.get(0);
    Map<String, Object> knobs = structured(call("add_modulator", Map.of(
        "class", MacroKnobs.class.getName(), "scope", pattern.getCanonicalPath())));
    // Device knob -> global fader: out of the device engine's scope.
    McpSchema.CallToolResult result = call("wire_modulator", Map.of(
        "sourcePath", knobs.get("path") + "/macro1",
        "targetPath", channel.fader.getCanonicalPath(),
        "scope", pattern.getCanonicalPath()));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  void wireModulatorInfersDeviceEngineFromSource() {
    var pattern = channel.patterns.get(0);
    Map<String, Object> knobs = structured(call("add_modulator", Map.of(
        "class", MacroKnobs.class.getName(), "scope", pattern.getCanonicalPath())));
    int before = pattern.modulation.modulations.size();

    // No scope arg: the device source's own engine hosts the wiring.
    Map<String, Object> wired = structured(call("wire_modulator", Map.of(
        "sourcePath", knobs.get("path") + "/macro1",
        "targetPath", knobs.get("path") + "/macro2")));
    assertEquals(pattern.modulation.getCanonicalPath(), wired.get("enginePath"));
    assertEquals(before + 1, pattern.modulation.modulations.size());

    structured(call("remove_modulation", Map.of("path", wired.get("path"))));
  }

  @Test
  void wireModulatorWithRangeAppliesInitialDepth() {
    Map<String, Object> knobs = structured(
        call("add_modulator", Map.of("class", MacroKnobs.class.getName())));
    String macro1 = knobs.get("path") + "/macro1";

    Map<String, Object> wired = structured(call("wire_modulator", Map.of(
        "sourcePath", macro1, "targetPath", channel.fader.getCanonicalPath(), "range", 0.75)));
    assertEquals(0.75, ((Number) wired.get("range")).doubleValue(), 1e-9);

    Map<String, Object> rangeParam = structured(
        call("get_parameter", Map.of("path", wired.get("rangePath"))));
    assertEquals(0.75, ((Number) rangeParam.get("value")).doubleValue(), 1e-9);

    structured(call("remove_modulation", Map.of("path", wired.get("path"))));
  }

  @Test
  void wireModulatorRangeOutOfBoundsIsInvalidArgument() {
    Map<String, Object> knobs = structured(
        call("add_modulator", Map.of("class", MacroKnobs.class.getName())));
    String macro1 = knobs.get("path") + "/macro1";

    McpSchema.CallToolResult result = call("wire_modulator", Map.of(
        "sourcePath", macro1, "targetPath", channel.fader.getCanonicalPath(), "range", 2.0));
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
        call("add_modulator", Map.of("class", MacroTriggers.class.getName())));
    int before = lx.engine.modulation.triggers.size();

    Map<String, Object> wired = structured(call("wire_trigger", Map.of(
        "sourcePath", triggers.get("path") + "/macro1",
        "targetPath", channel.enabled.getCanonicalPath())));
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
        call("add_modulator", Map.of("class", MacroKnobs.class.getName())));
    Map<String, Object> wired = structured(call("wire_modulator", Map.of(
        "sourcePath", knobs.get("path") + "/macro1",
        "targetPath", channel.fader.getCanonicalPath())));
    Map<String, Object> triggerBank = structured(
        call("add_modulator", Map.of("class", MacroTriggers.class.getName())));
    Map<String, Object> wiredTrigger = structured(call("wire_trigger", Map.of(
        "sourcePath", triggerBank.get("path") + "/macro1",
        "targetPath", channel.enabled.getCanonicalPath())));

    Map<String, Object> payload = structured(call("list_modulations", Map.of("detail", "full")));
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
  @SuppressWarnings("unchecked")
  void listModulationsDefaultSummaryOmitsDepthFields() {
    Map<String, Object> knobs = structured(
        call("add_modulator", Map.of("class", MacroKnobs.class.getName())));
    Map<String, Object> wired = structured(call("wire_modulator", Map.of(
        "sourcePath", knobs.get("path") + "/macro1",
        "targetPath", channel.fader.getCanonicalPath())));

    try {
      Map<String, Object> payload = structured(call("list_modulations", Map.of()));
      List<Map<String, Object>> modulators = (List<Map<String, Object>>) payload.get("modulators");
      Map<String, Object> modulatorEntry = modulators.stream()
          .filter(m -> knobs.get("path").equals(m.get("path"))).findFirst().orElseThrow();
      assertFalse(modulatorEntry.containsKey("id"));
      assertFalse(modulatorEntry.containsKey("running"));
      assertFalse(modulatorEntry.containsKey("oscAddress"));
      assertNotNull(modulatorEntry.get("label"));
      assertNotNull(modulatorEntry.get("class"));

      String sourcePath = knobs.get("path") + "/macro1";
      List<Map<String, Object>> modulations = (List<Map<String, Object>>) payload.get("modulations");
      Map<String, Object> modulationEntry = modulations.stream()
          .filter(m -> sourcePath.equals(m.get("sourcePath"))).findFirst().orElseThrow();
      assertFalse(modulationEntry.containsKey("range"));
      assertFalse(modulationEntry.containsKey("polarity"));
      assertFalse(modulationEntry.containsKey("rangePath"));
      assertFalse(modulationEntry.containsKey("id"));
      assertNotNull(modulationEntry.get("path"), "summary includes path for drill-down/mutation");
      assertNotNull(modulationEntry.get("sourcePath"));
      assertEquals(channel.fader.getCanonicalPath(), modulationEntry.get("targetPath"));
    } finally {
      structured(call("remove_modulation", Map.of("path", wired.get("path"))));
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void listModulationsFullIncludesDepthFields() {
    Map<String, Object> knobs = structured(
        call("add_modulator", Map.of("class", MacroKnobs.class.getName())));
    Map<String, Object> wired = structured(call("wire_modulator", Map.of(
        "sourcePath", knobs.get("path") + "/macro1",
        "targetPath", channel.fader.getCanonicalPath())));

    try {
      Map<String, Object> payload = structured(call("list_modulations", Map.of("detail", "full")));
      List<Map<String, Object>> modulations = (List<Map<String, Object>>) payload.get("modulations");
      Map<String, Object> entry = modulations.stream()
          .filter(m -> wired.get("path").equals(m.get("path"))).findFirst().orElseThrow();
      assertNotNull(entry.get("range"));
      assertNotNull(entry.get("polarity"));
      assertNotNull(entry.get("rangePath"));

      assertFalse(payload.containsKey("modulatorCount"), "full detail matches today's shape except isAutoMuted.path is now omitted");
      assertFalse(payload.containsKey("modulationCount"));
      assertFalse(payload.containsKey("triggerCount"));
    } finally {
      structured(call("remove_modulation", Map.of("path", wired.get("path"))));
    }
  }

  @Test
  void fireTriggerPulsesAMomentaryMacro() {
    Map<String, Object> triggers = structured(
        call("add_modulator", Map.of("class", MacroTriggers.class.getName())));

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
  void removeModulatorOverMcpMutatesEngineState() {
    Map<String, Object> knobs = structured(
        call("add_modulator", Map.of("class", MacroKnobs.class.getName())));
    String path = (String) knobs.get("path");
    int before = lx.engine.modulation.modulators.size();

    Map<String, Object> removed = structured(call("remove_modulator", Map.of("path", path)));

    assertEquals(path, removed.get("removed"));
    assertEquals("modulator", removed.get("kind"));
    assertEquals(before - 1, lx.engine.modulation.modulators.size());
  }

  @Test
  void removeModulatorRemovesDependentWirings() {
    Map<String, Object> knobs = structured(
        call("add_modulator", Map.of("class", MacroKnobs.class.getName())));
    String macro1 = knobs.get("path") + "/macro1";
    structured(call("wire_modulator", Map.of(
        "sourcePath", macro1, "targetPath", channel.fader.getCanonicalPath())));
    assertEquals(1, lx.engine.modulation.modulations.size());

    structured(call("remove_modulator", Map.of("path", knobs.get("path"))));

    assertEquals(0, lx.engine.modulation.modulations.size(),
        "the dependent wiring is removed along with its source");
  }

  @Test
  void removeModulatorUnknownPathIsNotFound() {
    McpSchema.CallToolResult result =
        call("remove_modulator", Map.of("path", "/lx/modulation/modulator/99"));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.NOT_FOUND));
  }

  @Test
  void removeModulatorWrongTypeIsInvalidArgument() {
    McpSchema.CallToolResult result =
        call("remove_modulator", Map.of("path", channel.fader.getCanonicalPath()));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
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
      assertEquals("channel", removed.get("kind"));
    }
    assertEquals(before, lx.engine.mixer.channels.size());
  }

  @Test
  @SuppressWarnings("unchecked")
  void addChannelWithPatternSeededOnCreation() {
    int before = lx.engine.mixer.channels.size();

    Map<String, Object> added = structured(call("add_channel",
        Map.of("class", GradientPattern.class.getName())));
    String channelPath = (String) added.get("path");
    try {
      assertNotNull(channelPath);
      assertEquals(before + 1, lx.engine.mixer.channels.size());

      // Verify pattern seeded — the channel's patterns list should have one entry
      Map<String, Object> channels = structured(call("list_channels", Map.of("detail", "full")));
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
          "containerPath", channelPath, "class", GradientPattern.class.getName())));
      String p1path = (String) p1.get("path");
      assertNotNull(p1path);

      // Add second pattern
      Map<String, Object> p2 = structured(call("add_pattern", Map.of(
          "containerPath", channelPath, "class", GradientPattern.class.getName())));
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
          "containerPath", channelPath, "class", BlurEffect.class.getName())));
      String e1path = (String) e1.get("path");
      assertNotNull(e1path);

      Map<String, Object> e2 = structured(call("add_effect", Map.of(
          "containerPath", channelPath, "class", BlurEffect.class.getName())));
      String e2path = (String) e2.get("path");

      // Move e1 to index 1
      Map<String, Object> moved = structured(
          call("move_effect", Map.of("path", e1path, "index", 1)));
      // After moving e1 to index 1, its path may change (index-based paths shift)
      assertNotNull(moved.get("path"));
      assertEquals(1, ((Number) moved.get("index")).intValue());
      assertOscChanges(moved.get("oscChanges"));

      // Remove e2 (still at index 0 after e1 moved)
      Map<String, Object> removed = structured(call("remove_effect", Map.of("path", e2path)));
      assertEquals(e2path, removed.get("removed"));
      assertEquals("effect", removed.get("kind"));
    } finally {
      structured(call("remove_channel", Map.of("path", channelPath)));
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void movePatternReportsOscChanges() {
    Map<String, Object> ch = structured(call("add_channel", Map.of()));
    String channelPath = (String) ch.get("path");
    try {
      Map<String, Object> p0 = structured(call("add_pattern", Map.of(
          "containerPath", channelPath, "class", GradientPattern.class.getName())));
      structured(call("add_pattern", Map.of(
          "containerPath", channelPath, "class", GradientPattern.class.getName())));
      structured(call("add_pattern", Map.of(
          "containerPath", channelPath, "class", GradientPattern.class.getName())));
      String p0path = (String) p0.get("path");

      // Move p0 past both siblings — every one of the three patterns' paths shifts.
      Map<String, Object> moved = structured(
          call("move_pattern", Map.of("path", p0path, "index", 2)));
      assertEquals(2, ((Number) moved.get("index")).intValue());

      List<Map<String, Object>> oscChanges = (List<Map<String, Object>>) assertOscChanges(moved.get("oscChanges"));
      assertEquals(3, oscChanges.size(), "moved pattern plus the two shifted siblings");
    } finally {
      structured(call("remove_channel", Map.of("path", channelPath)));
    }
  }

  /** Asserts oscChanges is a non-null array of {componentId, before, after} objects. */
  @SuppressWarnings("unchecked")
  private static Object assertOscChanges(Object oscChangesObj) {
    assertInstanceOf(List.class, oscChangesObj);
    List<Map<String, Object>> oscChanges = (List<Map<String, Object>>) oscChangesObj;
    for (Map<String, Object> entry : oscChanges) {
      assertInstanceOf(Number.class, entry.get("componentId"));
      assertInstanceOf(String.class, entry.get("before"));
      assertInstanceOf(String.class, entry.get("after"));
      assertNotEquals(entry.get("before"), entry.get("after"));
    }
    return oscChanges;
  }

  @Test
  void addEffectInvalidContainerIsInvalidArgument() {
    McpSchema.CallToolResult result = call("add_effect", Map.of(
        "containerPath", "/lx/mixer", "class", BlurEffect.class.getName()));
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
    assertEquals(lx.engine.output.gamma.getValue(), ((Number) output.get("gamma")).doubleValue());
    assertEquals(lx.engine.output.gamma.getCanonicalPath(), output.get("gammaPath"));
    assertEquals(lx.engine.output.gammaMode.getEnum().name(), output.get("gammaMode"));
    assertEquals(lx.engine.output.gammaMode.getCanonicalPath(), output.get("gammaModePath"));

    @SuppressWarnings("unchecked")
    Map<String, Object> engine = (Map<String, Object>) payload.get("engine");
    assertNotNull(engine, "get_project_info reports engine-global playback state");
    assertEquals(lx.engine.speed.getValue(), ((Number) engine.get("speed")).doubleValue());
    assertEquals(lx.engine.speed.getCanonicalPath(), engine.get("speedPath"));
    assertEquals(lx.engine.framesPerSecond.getValue(),
        ((Number) engine.get("framesPerSecond")).doubleValue());
    assertEquals(lx.engine.framesPerSecond.getCanonicalPath(), engine.get("framesPerSecondPath"));

    assertFalse(result.content().isEmpty(), "success also carries a text mirror");
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith("{"), "text mirror is the serialized JSON payload");
  }

  @Test
  @SuppressWarnings("unchecked")
  void getStatusReportsServerAndConnectionState() {
    Map<String, Object> payload = structured(call("get_status", Map.of()));
    assertEquals("Chromatik-MCP", payload.get("serverName"));
    assertEquals(chromatikmcp.mcp.BuildInfo.version(), payload.get("serverVersion"));
    assertEquals(chromatikmcp.mcp.BuildInfo.buildTime(), payload.get("buildTime"));
    assertFalse(((String) payload.get("serverVersion")).isBlank());
    assertFalse(((String) payload.get("buildTime")).isBlank());
    assertNotNull(payload.get("lxVersion"));
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
    Map<String, Object> payload = structured(call("list_channels", Map.of("detail", "full")));
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

    Map<String, Object> controls = (Map<String, Object>) entry.get("controls");
    assertNotNull(controls, "channel entries carry a performance-surface controls block");
    assertNotNull(controls.get("crossfadeGroup"));
    assertNotNull(controls.get("blendMode"));
    assertNotNull(controls.get("autoMute"));
    assertNotNull(controls.get("isAutoMuted"));
    assertNotNull(controls.get("cueActive"));
    assertNotNull(controls.get("auxActive"));
    Map<String, Object> patternEngine = (Map<String, Object>) controls.get("patternEngine");
    assertNotNull(patternEngine, "playlist/blend channels carry a patternEngine controls block");
    assertNotNull(patternEngine.get("autoCycleEnabled"));
    assertNotNull(patternEngine.get("transitionBlendMode"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void listChannelsMixerControlsExposeCrossfaderAndCuePreview() {
    Map<String, Object> payload = structured(call("list_channels", Map.of("detail", "full")));
    Map<String, Object> mixer = (Map<String, Object>) payload.get("mixer");
    assertNotNull(mixer, "the top-level mixer object carries the crossfader performance surface");

    Map<String, Object> crossfader = (Map<String, Object>) mixer.get("crossfader");
    assertEquals(lx.engine.mixer.crossfader.getValue(), ((Number) crossfader.get("value")).doubleValue(), 1e-9);
    assertEquals(lx.engine.mixer.crossfader.getCanonicalPath(), crossfader.get("path"));

    Map<String, Object> crossfaderBlendMode = (Map<String, Object>) mixer.get("crossfaderBlendMode");
    assertEquals(lx.engine.mixer.crossfaderBlendMode.getObject().getLabel(),
        crossfaderBlendMode.get("current"));
    assertFalse(((List<String>) crossfaderBlendMode.get("options")).isEmpty());

    for (String key : List.of("cueA", "cueB", "auxA", "auxB")) {
      Map<String, Object> field = (Map<String, Object>) mixer.get(key);
      assertNotNull(field, key + " is reported on the mixer");
      assertNotNull(field.get("path"));
    }

    assertFalse(((List<String>) mixer.get("blendModeOptions")).isEmpty());
    assertFalse(((List<String>) mixer.get("transitionBlendModeOptions")).isEmpty());
  }

  @Test
  @SuppressWarnings("unchecked")
  void listChannelsIncludesEffectsHostedOnAPattern() {
    Map<String, Object> ch = structured(call("add_channel", Map.of()));
    String channelPath = (String) ch.get("path");
    try {
      String patternPath = (String) structured(call("add_pattern", Map.of(
          "containerPath", channelPath, "class", GradientPattern.class.getName()))).get("path");

      // Assert empty effects list before adding effect
      Map<String, Object> beforeAddEffectPayload =
          structured(call("list_channels", Map.of("detail", "full")));
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
          "containerPath", patternPath, "class", BlurEffect.class.getName())));
      String effectPath = (String) effect.get("path");

      Map<String, Object> payload = structured(call("list_channels", Map.of("detail", "full")));
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
          "containerPath", channelPath, "class", GradientPattern.class.getName()))).get("path");
      String p2path = (String) structured(call("add_pattern", Map.of(
          "containerPath", channelPath, "class", GradientPattern.class.getName()))).get("path");

      // Flip the channel to BLEND (CompositeMode ordinal 1 on the pattern engine's
      // compositeMode EnumParameter, registered on the channel).
      structured(call("set_parameter", Map.of(
          "path", channelPath + "/compositeMode", "value", 1)));
      structured(call("set_parameter", Map.of("path", p1path + "/enabled", "value", false)));
      structured(call("set_parameter", Map.of("path", p2path + "/enabled", "value", true)));
      structured(call("set_parameter", Map.of("path", p2path + "/compositeLevel", "value", 0.8)));

      Map<String, Object> payload = structured(call("list_channels", Map.of("detail", "full")));
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
  @SuppressWarnings("unchecked")
  void listChannelsDefaultsToSummaryShape() {
    Map<String, Object> payload = structured(call("list_channels", Map.of()));
    List<Map<String, Object>> channels = (List<Map<String, Object>>) payload.get("channels");
    Map<String, Object> entry = channels.get(channel.getIndex());

    assertFalse(entry.containsKey("controls"), "summary omits the controls block");
    assertFalse(entry.containsKey("patterns"), "summary omits the full patterns array");
    assertFalse(entry.containsKey("effects"), "summary omits the effects array");
    assertNotNull(entry.get("patternCount"));
    assertNotNull(entry.get("effectCount"));
    assertNotNull(entry.get("path"));
    assertNotNull(entry.get("label"));
    assertEquals("playlist", entry.get("patternMode"));

    Map<String, Object> master = (Map<String, Object>) payload.get("master");
    assertFalse(master.containsKey("effects"), "master summary omits the effects array");
    assertNotNull(master.get("effectCount"));

    Map<String, Object> mixer = (Map<String, Object>) payload.get("mixer");
    assertFalse(mixer.containsKey("blendModeOptions"), "summary drops the shared option arrays");
    assertFalse(mixer.containsKey("transitionBlendModeOptions"));
    assertFalse(mixer.containsKey("cueA"), "summary drops the cue/aux toggles");
    assertNotNull(mixer.get("crossfader"), "summary keeps the performance-critical crossfader");

    // Same object shape as full detail, minus only the options array — a key whose JSON type
    // varied with `detail` would silently break clients reading .current or .path.
    Map<String, Object> blendMode = (Map<String, Object>) mixer.get("crossfaderBlendMode");
    assertEquals(lx.engine.mixer.crossfaderBlendMode.getObject().getLabel(),
        blendMode.get("current"));
    assertNotNull(blendMode.get("path"), "summary keeps the settable path");
    assertFalse(blendMode.containsKey("options"), "summary drops the long options array");
  }

  @Test
  void listChannelsSummaryIncludesGroupMembership() {
    // Regression: channelSummary omitted `group` while channelFull emitted it — the drift
    // that parallel hand-written summary/full builders invite. Group membership matters
    // when surveying a mixer, so both shapes carry it. Builds a real group: asserting only
    // that summary and full agree would pass vacuously when no channel is grouped.
    class MutableGroupHolder {
      LXChannel grouped;
      LXGroup group;
    }
    final MutableGroupHolder holder = new MutableGroupHolder();

    // Setup: add channel and group on engine thread.
    lx.engine.addTask(() -> {
      holder.grouped = lx.engine.mixer.addChannel();
      holder.group = lx.engine.mixer.addGroup(List.of(holder.grouped));
    });
    lx.engine.run();

    try {
      String groupPath = holder.group.getCanonicalPath();
      String channelPath = holder.grouped.getCanonicalPath();

      assertEquals(groupPath,
          channelEntry(structured(call("list_channels", Map.of())), channelPath).get("group"),
          "summary carries group membership");
      assertEquals(groupPath,
          channelEntry(structured(call("list_channels", Map.of("detail", "full"))), channelPath)
              .get("group"));
    } finally {
      // Teardown: remove group and channel on engine thread.
      lx.engine.addTask(() -> {
        if (lx.engine.mixer.channels.contains(holder.group)) {
          lx.engine.mixer.removeChannel(holder.group);
        }
        if (lx.engine.mixer.channels.contains(holder.grouped)) {
          lx.engine.mixer.removeChannel(holder.grouped);
        }
      });
      lx.engine.run();
    }
  }

  /**
   * Shared setup/teardown for tests that add one channel on the engine thread, run
   * assertions, then remove that channel on the engine thread — even if the assertions
   * throw. {@code setup} both builds the fixture (via engine-thread-only side effects on
   * whatever it closes over) and returns the channel to tear down.
   */
  private static void withChannel(
      java.util.function.Supplier<LXChannel> setup, Runnable assertions) {
    LXChannel[] created = new LXChannel[1];
    lx.engine.addTask(() -> created[0] = setup.get());
    lx.engine.run();
    try {
      assertions.run();
    } finally {
      lx.engine.addTask(() -> {
        if (lx.engine.mixer.channels.contains(created[0])) {
          lx.engine.mixer.removeChannel(created[0]);
        }
      });
      lx.engine.run();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void listChannelsExposesNestedPatternAndLocalModulationMarkersInBothDetailLevels() {
    // Issue #117: list_channels must not silently omit that a pattern is itself a
    // container (a PatternRack, whose own children this tool never walks) or that a
    // pattern/effect carries local (device-scoped) modulation invisible to a scope-less
    // list_modulations call. Both markers must be honest in whichever mode names the
    // entry — including summary's single 'activePattern'.
    class Holder {
      LXChannel channel;
      PatternRack rack;
      GradientPattern modulatedPattern;
      BlurEffect modulatedEffect;
    }
    Holder h = new Holder();

    withChannel(() -> {
      h.channel = lx.engine.mixer.addChannel();
      h.rack = new PatternRack(lx);
      // First pattern added to an empty channel auto-activates (LXPatternEngine.addPattern),
      // so the rack lands as summary's 'activePattern'.
      h.channel.addPattern(h.rack);
      h.rack.patternEngine.addPattern(new GradientPattern(lx));
      h.rack.patternEngine.addPattern(new GradientPattern(lx));

      h.modulatedPattern = new GradientPattern(lx);
      h.channel.addPattern(h.modulatedPattern);
      h.modulatedPattern.modulation.addModulator(new MacroKnobs());

      h.modulatedEffect = new BlurEffect(lx);
      h.channel.addEffect(h.modulatedEffect);
      h.modulatedEffect.modulation.addModulator(new MacroKnobs());
      return h.channel;
    }, () -> {
      String channelPath = h.channel.getCanonicalPath();
      String rackPath = h.rack.getCanonicalPath();
      String modulatedPatternPath = h.modulatedPattern.getCanonicalPath();
      String modulatedEffectPath = h.modulatedEffect.getCanonicalPath();

      Map<String, Object> full = channelEntry(structured(call("list_channels", Map.of("detail", "full"))), channelPath);
      List<Map<String, Object>> patterns = (List<Map<String, Object>>) full.get("patterns");
      Map<String, Object> rackEntry = patterns.stream()
          .filter(p -> rackPath.equals(p.get("path"))).findFirst().orElseThrow();
      assertEquals(2, ((Number) rackEntry.get("nestedPatternCount")).intValue(),
          "rack's own two child patterns are counted, not enumerated");
      assertEquals(Boolean.FALSE, rackEntry.get("hasLocalModulation"));

      Map<String, Object> modulatedPatternEntry = patterns.stream()
          .filter(p -> modulatedPatternPath.equals(p.get("path"))).findFirst().orElseThrow();
      assertEquals(0, ((Number) modulatedPatternEntry.get("nestedPatternCount")).intValue(),
          "an ordinary pattern hosts no nested patterns");
      assertEquals(Boolean.TRUE, modulatedPatternEntry.get("hasLocalModulation"));

      List<Map<String, Object>> effects = (List<Map<String, Object>>) full.get("effects");
      Map<String, Object> modulatedEffectEntry = effects.stream()
          .filter(e -> modulatedEffectPath.equals(e.get("path"))).findFirst().orElseThrow();
      assertEquals(Boolean.TRUE, modulatedEffectEntry.get("hasLocalModulation"));

      Map<String, Object> summary = channelEntry(structured(call("list_channels", Map.of())), channelPath);
      Map<String, Object> activePattern = (Map<String, Object>) summary.get("activePattern");
      assertEquals(rackPath, activePattern.get("path"), "rack is the auto-activated first pattern");
      assertEquals(2, ((Number) activePattern.get("nestedPatternCount")).intValue(),
          "summary's activePattern is honest about its own nested children too");
      assertEquals(Boolean.FALSE, activePattern.get("hasLocalModulation"));
    });
  }

  @Test
  @SuppressWarnings("unchecked")
  void listChannelsBlendModeRackExposesRollupsInSummary() {
    // Reviewer's exact #117 repro: a BLEND-mode channel whose only pattern is a
    // PatternRack gets no 'activePattern' at all (active is meaningless in BLEND), so the
    // channel-level rollups are the only summary-detail signal that a rack is present.
    class Holder {
      LXChannel channel;
      PatternRack rack;
    }
    Holder h = new Holder();
    withChannel(() -> {
      h.channel = lx.engine.mixer.addChannel();
      h.rack = new PatternRack(lx);
      h.channel.addPattern(h.rack);
      h.rack.patternEngine.addPattern(new GradientPattern(lx));
      h.channel.getPatternEngine().compositeMode.setValue(1);
      return h.channel;
    }, () -> {
      String channelPath = h.channel.getCanonicalPath();
      Map<String, Object> summary = channelEntry(structured(call("list_channels", Map.of())), channelPath);
      assertEquals("blend", summary.get("patternMode"));
      assertFalse(summary.containsKey("activePattern"), "blend mode never carries activePattern");
      assertEquals(1, ((Number) summary.get("containerPatternCount")).intValue(),
          "the rack must be counted even though blend mode has no activePattern to mark it on");
      assertEquals(Boolean.FALSE, summary.get("anyLocalModulation"));
    });
  }

  @Test
  @SuppressWarnings("unchecked")
  void listChannelsPlaylistModeNonActiveRackExposesRollupsInSummary() {
    // A rack that is not the active pattern is invisible to summary's single
    // 'activePattern' marker — the channel-level rollup must still catch it.
    class Holder {
      LXChannel channel;
      GradientPattern active;
      PatternRack rack;
    }
    Holder h = new Holder();
    withChannel(() -> {
      h.channel = lx.engine.mixer.addChannel();
      h.active = new GradientPattern(lx);
      h.channel.addPattern(h.active);
      h.rack = new PatternRack(lx);
      h.channel.addPattern(h.rack);
      h.rack.patternEngine.addPattern(new GradientPattern(lx));
      return h.channel;
    }, () -> {
      String channelPath = h.channel.getCanonicalPath();
      Map<String, Object> summary = channelEntry(structured(call("list_channels", Map.of())), channelPath);
      assertEquals("playlist", summary.get("patternMode"));
      Map<String, Object> activePattern = (Map<String, Object>) summary.get("activePattern");
      assertNotEquals(h.rack.getCanonicalPath(), activePattern.get("path"),
          "the rack is not the active pattern");
      assertEquals(1, ((Number) summary.get("containerPatternCount")).intValue(),
          "a non-active rack must still be flagged by the channel-level rollup");
    });
  }

  @Test
  @SuppressWarnings("unchecked")
  void listChannelsLocalModulationOnNonActivePatternOrEffectSetsSummaryRollup() {
    class Holder {
      LXChannel channel;
      GradientPattern active;
      GradientPattern modulatedPattern;
      BlurEffect modulatedEffect;
    }
    Holder h = new Holder();
    withChannel(() -> {
      h.channel = lx.engine.mixer.addChannel();
      h.active = new GradientPattern(lx);
      h.channel.addPattern(h.active);
      h.modulatedPattern = new GradientPattern(lx);
      h.channel.addPattern(h.modulatedPattern);
      h.modulatedPattern.modulation.addModulator(new MacroKnobs());
      return h.channel;
    }, () -> {
      String channelPath = h.channel.getCanonicalPath();
      Map<String, Object> summary = channelEntry(structured(call("list_channels", Map.of())), channelPath);
      assertEquals(Boolean.TRUE, summary.get("anyLocalModulation"),
          "non-active pattern's local modulation must still surface at channel scope");
    });

    Holder e = new Holder();
    withChannel(() -> {
      e.channel = lx.engine.mixer.addChannel();
      e.active = new GradientPattern(lx);
      e.channel.addPattern(e.active);
      e.modulatedEffect = new BlurEffect(lx);
      e.channel.addEffect(e.modulatedEffect);
      e.modulatedEffect.modulation.addModulator(new MacroKnobs());
      return e.channel;
    }, () -> {
      String channelPath = e.channel.getCanonicalPath();
      Map<String, Object> summary = channelEntry(structured(call("list_channels", Map.of())), channelPath);
      assertEquals(Boolean.TRUE, summary.get("anyLocalModulation"),
          "an effect's local modulation must still surface at channel scope");
    });
  }

  @Test
  void listChannelsLocalModulationOnPatternOwnedEffectSetsSummaryAndFullRollup() {
    class Holder {
      LXChannel channel;
      GradientPattern pattern;
      BlurEffect patternEffect;
    }
    Holder h = new Holder();
    withChannel(() -> {
      h.channel = lx.engine.mixer.addChannel();
      h.pattern = new GradientPattern(lx);
      h.channel.addPattern(h.pattern);
      h.patternEffect = new BlurEffect(lx);
      h.pattern.addEffect(h.patternEffect);
      h.patternEffect.modulation.addModulator(new MacroKnobs());
      return h.channel;
    }, () -> {
      String channelPath = h.channel.getCanonicalPath();
      Map<String, Object> summary = channelEntry(structured(call("list_channels", Map.of())), channelPath);
      assertEquals(Boolean.TRUE, summary.get("anyLocalModulation"),
          "a pattern-owned effect's local modulation must still surface at channel scope");

      Map<String, Object> full = channelEntry(structured(call("list_channels", Map.of("detail", "full"))), channelPath);
      assertEquals(Boolean.TRUE, full.get("anyLocalModulation"),
          "a pattern-owned effect's local modulation must still surface at channel scope in full detail");
    });
  }

  @Test
  void listChannelsOrdinaryChannelReportsZeroRollupsInSummaryAndFull() {
    // Uses its own freshly-added channel/pattern rather than the class-level shared
    // 'channel' fixture, which other tests in this suite mutate over the run.
    class Holder {
      LXChannel channel;
    }
    Holder h = new Holder();
    withChannel(() -> {
      h.channel = lx.engine.mixer.addChannel();
      h.channel.addPattern(new GradientPattern(lx));
      return h.channel;
    }, () -> {
      String channelPath = h.channel.getCanonicalPath();
      Map<String, Object> summary = channelEntry(structured(call("list_channels", Map.of())), channelPath);
      assertEquals(0, ((Number) summary.get("containerPatternCount")).intValue());
      assertEquals(Boolean.FALSE, summary.get("anyLocalModulation"));

      Map<String, Object> full = channelEntry(structured(call("list_channels", Map.of("detail", "full"))), channelPath);
      assertEquals(0, ((Number) full.get("containerPatternCount")).intValue());
      assertEquals(Boolean.FALSE, full.get("anyLocalModulation"));
    });
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> channelEntry(Map<String, Object> payload, String path) {
    for (Map<String, Object> entry : (List<Map<String, Object>>) payload.get("channels")) {
      if (path.equals(entry.get("path"))) {
        return entry;
      }
    }
    throw new AssertionError("no channel at " + path + " in list_channels payload");
  }


  @Test
  @SuppressWarnings("unchecked")
  void listChannelsSummaryIsDramaticallySmallerThanFull() {
    List<String> channelPaths = new java.util.ArrayList<>();
    try {
      for (int i = 0; i < 4; i++) {
        String channelPath = (String) structured(call("add_channel",
            Map.of("class", GradientPattern.class.getName()))).get("path");
        channelPaths.add(channelPath);
        structured(call("add_pattern", Map.of(
            "containerPath", channelPath, "class", GradientPattern.class.getName())));
        structured(call("add_effect", Map.of(
            "containerPath", channelPath, "class", BlurEffect.class.getName())));
      }

      String summaryJson = jsonSize(structured(call("list_channels", Map.of())));
      String fullJson = jsonSize(structured(call("list_channels", Map.of("detail", "full"))));

      assertTrue(summaryJson.length() < fullJson.length() * 0.4,
          "summary (" + summaryJson.length() + " bytes) should be well under 40% of full ("
              + fullJson.length() + " bytes)");
    } finally {
      // Paths are index-based and shift on removal — remove last-added first so earlier
      // paths stay valid.
      for (int i = channelPaths.size() - 1; i >= 0; i--) {
        structured(call("remove_channel", Map.of("path", channelPaths.get(i))));
      }
    }
  }

  private static String jsonSize(Map<String, Object> payload) {
    try {
      return new tools.jackson.databind.ObjectMapper().writeValueAsString(payload);
    } catch (RuntimeException e) {
      throw new IllegalStateException("Failed to serialize payload for size comparison", e);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void getChannelMatchesItsEntryInListChannelsFull() {
    String path = channel.getCanonicalPath();
    Map<String, Object> payload = structured(call("get_channel", Map.of("path", path)));

    Map<String, Object> listEntry = channelEntry(
        structured(call("list_channels", Map.of("detail", "full"))), path);
    assertEquals(listEntry, payload,
        "get_channel payload has the same keys/values as its list_channels{full} entry "
            + "(both call channelFull, so the shape is identical by construction)");

    assertFalse(payload.containsKey("channels"), "no collection envelope");
    assertFalse(payload.containsKey("master"), "no collection envelope");
    assertFalse(payload.containsKey("mixer"), "no collection envelope");
  }

  @Test
  @SuppressWarnings("unchecked")
  void getChannelOnMasterBusReturnsMasterDetail() {
    // #123's removed list_channels{path} drill-down 404'd on the master bus — LXMasterBus
    // extends LXBus, not LXAbstractChannel, so it needs its own explicit branch.
    String path = lx.engine.mixer.masterBus.getCanonicalPath();
    Map<String, Object> payload = structured(call("get_channel", Map.of("path", path)));

    Map<String, Object> listMaster = (Map<String, Object>)
        structured(call("list_channels", Map.of("detail", "full"))).get("master");
    assertEquals(listMaster, payload,
        "get_channel(master) has the same keys/values as list_channels{full}.master "
            + "(both call masterFull, so the shape is identical by construction)");

    assertFalse(payload.containsKey("channels"), "no collection envelope");
    assertFalse(payload.containsKey("mixer"), "no collection envelope");
  }

  @Test
  @SuppressWarnings("unchecked")
  void getChannelOnGroupMatchesItsEntryInListChannelsFull() {
    // list_channels' own group coverage (listChannelsSummaryIncludesGroupMembership) only
    // reads the `group` field off a member channel's entry — this exercises get_channel on
    // the group's own canonical path, the branch Channels.describe's GROUP case covers at
    // the domain level (ChannelsTest) but that no tool-layer test had reached.
    // Uses its own channel, not the shared `channel` fixture — grouping the shared
    // fixture would shift its index/path out from under every other test in the class.
    EngineExecutor executor = new EngineExecutor(lx);
    LXChannel grouped = executor.call(() -> lx.engine.mixer.addChannel());
    LXGroup group = executor.call(() -> lx.engine.mixer.addGroup(List.of(grouped)));
    try {
      String groupPath = group.getCanonicalPath();
      Map<String, Object> payload = structured(call("get_channel", Map.of("path", groupPath)));

      Map<String, Object> listEntry = channelEntry(
          structured(call("list_channels", Map.of("detail", "full"))), groupPath);
      assertEquals(listEntry, payload,
          "get_channel(group) has the same keys/values as its list_channels{full} entry "
              + "(both call channelFull, so the shape is identical by construction)");

      assertFalse(payload.containsKey("channels"), "no collection envelope");
      assertFalse(payload.containsKey("master"), "no collection envelope");
      assertFalse(payload.containsKey("mixer"), "no collection envelope");
    } finally {
      executor.execute(() -> {
        if (lx.engine.mixer.channels.contains(group)) {
          lx.engine.mixer.removeChannel(group);
        }
        if (lx.engine.mixer.channels.contains(grouped)) {
          lx.engine.mixer.removeChannel(grouped);
        }
      });
    }
  }

  @Test
  void getChannelUnknownPathIsNotFound() {
    McpSchema.CallToolResult result = call("get_channel", Map.of("path", "/lx/mixer/channel/99"));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.NOT_FOUND));
  }

  @Test
  void getChannelMalformedPathIsInvalidArgument() {
    // The defect #123 hit: a malformed path bypassing Resolve reported not_found instead
    // of invalid_argument. Resolve.component rejects this before any lookup.
    McpSchema.CallToolResult result = call("get_channel", Map.of("path", "not-a-path"));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  void getChannelNonBusPathIsInvalidArgument() {
    McpSchema.CallToolResult result = call("get_channel",
        Map.of("path", channel.patterns.get(0).getCanonicalPath()));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
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
  @SuppressWarnings("unchecked")
  void listMidiDevicesReturnsBothPortLists() {
    Map<String, Object> payload = structured(call("list_midi_devices", Map.of()));
    assertNotNull(payload.get("inputs"), "inputs list always present");
    assertNotNull(payload.get("outputs"), "outputs list always present");
    assertInstanceOf(List.class, payload.get("inputs"));
    assertInstanceOf(List.class, payload.get("outputs"));
    // Headless test JVM discovers no hardware ports; the counts still mirror live state.
    assertEquals(lx.engine.midi.inputs.size(), ((List<?>) payload.get("inputs")).size());
    assertEquals(lx.engine.midi.outputs.size(), ((List<?>) payload.get("outputs")).size());
  }

  @Test
  @SuppressWarnings("unchecked")
  void listMidiMappingsReturnsMappingList() {
    Map<String, Object> payload = structured(call("list_midi_mappings", Map.of()));
    assertNotNull(payload.get("mappings"), "mappings list always present");
    assertEquals(lx.engine.midi.mappings.size(),
        ((List<Map<String, Object>>) payload.get("mappings")).size());
  }

  @Test
  @SuppressWarnings("unchecked")
  void listMidiMappingsWireShapeForCcMapping() throws javax.sound.midi.InvalidMidiDataException {
    lx.command.perform(new LXCommand.Midi.AddMapping(new MidiControlChange(2, 20, 64), channel.fader));

    Map<String, Object> payload = structured(call("list_midi_mappings", Map.of()));
    List<Map<String, Object>> mappings = (List<Map<String, Object>>) payload.get("mappings");
    assertTrue(mappings.size() > 0, "at least one mapping");
    Map<String, Object> entry = mappings.get(0);
    assertEquals("cc", entry.get("type"));
    assertEquals(2, entry.get("channel"));
    assertEquals(20, entry.get("number"));
    assertEquals(channel.fader.getCanonicalPath(), entry.get("targetPath"));
    assertTrue(entry.containsKey("label"), "label field present");
    assertTrue(entry.containsKey("targetLabel"), "targetLabel field present");
    assertFalse(entry.containsKey("note"), "note key omitted for CC mapping");
  }

  @Test
  @SuppressWarnings("unchecked")
  void listMidiSurfacesReturnsSurfaceList() {
    Map<String, Object> payload = structured(call("list_midi_surfaces", Map.of()));
    assertNotNull(payload.get("surfaces"), "surfaces list always present");
    assertEquals(lx.engine.midi.surfaces.size(),
        ((List<Map<String, Object>>) payload.get("surfaces")).size());
  }

  @Test
  void addMidiMappingWireShapeAndRemoveRoundTrip() {
    int before = lx.engine.midi.mappings.size();

    Map<String, Object> added = structured(call("add_midi_mapping", Map.of(
        "type", "cc", "channel", 2, "number", 20,
        "targetPath", channel.fader.getCanonicalPath())));
    assertEquals("cc", added.get("type"));
    assertEquals(2, added.get("channel"));
    assertEquals(20, added.get("number"));
    assertEquals(channel.fader.getCanonicalPath(), added.get("targetPath"));
    assertTrue(added.containsKey("label"), "label field present");
    assertTrue(added.containsKey("targetLabel"), "targetLabel field present");
    assertFalse(added.containsKey("note"), "note key omitted for CC mapping");
    assertEquals(before + 1, lx.engine.midi.mappings.size());

    int index = ((Number) added.get("index")).intValue();
    Map<String, Object> removed = structured(call("remove_midi_mapping", Map.of("index", index)));
    assertEquals("cc", removed.get("type"));
    assertEquals(channel.fader.getCanonicalPath(), removed.get("targetPath"));
    assertEquals(before, lx.engine.midi.mappings.size(), "round trip leaves mapping count unchanged");
  }

  @Test
  void removeMidiMappingUnknownIndexIsInvalidArgument() {
    int outOfRange = lx.engine.midi.mappings.size() + 99;
    McpSchema.CallToolResult result = call("remove_midi_mapping", Map.of("index", outOfRange));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  @SuppressWarnings("unchecked")
  void setMidiInputOverMcpFlipsFlagVisibleInListMidiDevices() throws InterruptedException {
    awaitFirstMidiInput();

    Map<String, Object> updated = structured(
        call("set_midi_input", Map.of("index", 0, "channelEnabled", true)));
    assertEquals(Boolean.TRUE, updated.get("channelEnabled"));
    assertEquals(Boolean.TRUE, updated.get("enabled"), "enabled is the derived union");

    try {
      Map<String, Object> devices = structured(call("list_midi_devices", Map.of()));
      Map<String, Object> input = ((List<Map<String, Object>>) devices.get("inputs")).get(0);
      assertEquals(Boolean.TRUE, input.get("channelEnabled"));
      assertEquals(Boolean.TRUE, input.get("enabled"));
    } finally {
      // Leave the shared LX fixture's flag state as later tests found it.
      structured(call("set_midi_input", Map.of("index", 0, "channelEnabled", false)));
    }
  }

  @Test
  void setMidiInputRequiresAtLeastOneFlag() {
    McpSchema.CallToolResult result = call("set_midi_input", Map.of("index", 0));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  void setMidiSurfaceEnabledUnknownIndexIsInvalidArgument() {
    // No hardware surface can be instantiated headlessly, so only the invalid-index path
    // is testable here — the mutation itself is exercised only by a direct BooleanParameter
    // set, which is covered structurally by the analogous input-flag test above.
    McpSchema.CallToolResult result =
        call("set_midi_surface_enabled", Map.of("index", 99, "enabled", true));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  /**
   * {@code engine.midi.inputs} is populated by an async device-detection thread that
   * finishes with an {@code engine.addTask(...)} the class's drainer thread (standing in
   * for the engine thread) drains — poll rather than assume it has landed by the time this
   * test runs. The JDK's built-in "Real Time Sequencer" software device guarantees at
   * least one entry on any OS, without depending on real hardware.
   */
  private static void awaitFirstMidiInput() throws InterruptedException {
    long deadline = System.currentTimeMillis() + 5000;
    while (lx.engine.midi.inputs.isEmpty()) {
      if (System.currentTimeMillis() > deadline) {
        throw new IllegalStateException("No MIDI input discovered within 5s");
      }
      Thread.sleep(20);
    }
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
        call("add_modulator", Map.of("class", MacroKnobs.class.getName())));
    String macro1 = knobs.get("path") + "/macro1";
    structured(call("set_parameter",
        Map.of("path", channel.fader.getCanonicalPath(), "value", 0.0)));
    // A deterministic source value (unlike an LFO) so the effective reading is exact.
    structured(call("set_parameter", Map.of("path", macro1, "value", 0.5)));
    Map<String, Object> wired = structured(call("wire_modulator",
        Map.of("sourcePath", macro1, "targetPath", channel.fader.getCanonicalPath())));
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
    // Single-classpath test JVM resolves stock classes at tier 2; deployed runtime
    // resolves them at tier 3 via the plugin jar.
    assertTrue(
        Set.of("class-jar", "plugin-jar").contains(catalog.get("source")),
        "source is either class-jar (tier 2) or plugin-jar (tier 3)");

    // GradientPattern's parameter-interaction and usage-tip prose is curated (written from
    // live driving, carried across regenerations), so the class-bytes hash does not vouch
    // for it. The payload must say so, or `stale: false` implies more than it can.
    assertEquals("parameterInteractions, usageTips", catalog.get("curated"));
    assertNotNull(catalog.get("curatedAt"), "a curated entry stamps when it was curated");
  }

  @Test
  @SuppressWarnings("unchecked")
  void getComponentDocAlwaysEmitsCandidatesEvenForASingleVisibleCopy() {
    // The common case: exactly one copy of GradientPattern's entry is visible. candidates
    // must still be a one-element array, not omitted — its absence would otherwise be
    // ambiguous between "exactly one copy visible" and "this server predates the feature."
    // (The multi-candidate ranking behavior itself — an overlay/classpath entry losing to
    // a more-accurate one — is covered at the domain layer in CatalogTest, which can
    // fabricate a competing copy via a throwaway URLClassLoader; doing that here would mean
    // planting a file on the shared test classpath, which is what a prior version of this
    // test did and which risked poisoning CatalogFormatTest on a non-clean rebuild.)
    Map<String, Object> payload = structured(
        call("get_component_doc", Map.of("class", GradientPattern.class.getName())));
    Map<String, Object> catalog = (Map<String, Object>) payload.get("catalog");
    assertNotNull(catalog);

    List<Map<String, Object>> candidates = (List<Map<String, Object>>) catalog.get("candidates");
    assertNotNull(candidates, "candidates is always present, even with a single visible copy");
    assertEquals(1, candidates.size());
    Map<String, Object> only = candidates.get(0);
    assertEquals(catalog.get("source"), only.get("source"));
    assertNotNull(only.get("url"));
    Object bytesMatch = only.get("bytesMatch");
    assertTrue(bytesMatch instanceof Boolean || "unknown".equals(bytesMatch),
        "bytesMatch is three-valued like `stale`: true, false, or \"unknown\"");
  }

  @Test
  @SuppressWarnings("unchecked")
  void getComponentDocOmitsCuratedForGeneratedEntries() {
    // The curated keys appear only where the entry declares them — an ordinary generated
    // entry's whole body is hash-backed and must not imply otherwise.
    Map<String, Object> payload = structured(
        call("get_component_doc", Map.of("class", "heronarts.lx.effect.BlurEffect")));
    Map<String, Object> catalog = (Map<String, Object>) payload.get("catalog");
    assertNotNull(catalog);
    assertFalse(catalog.containsKey("curated"));
    assertFalse(catalog.containsKey("curatedAt"));
  }

  @Test
  void getComponentDocUndocumentedClass() {
    // UndocumentedModulator is registered but has no catalog entry anywhere.
    Map<String, Object> payload = structured(
        call("get_component_doc", Map.of("class", UndocumentedModulator.class.getName())));
    assertEquals(UndocumentedModulator.class.getName(), payload.get("class"));
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
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  void getComponentDocBothArgsIsRejected() {
    var pattern = channel.patterns.get(0);
    McpSchema.CallToolResult result = call("get_component_doc", Map.of(
        "class", GradientPattern.class.getName(),
        "path", pattern.getCanonicalPath()));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  void getComponentDocClassEmptyStringIsInvalidArgument() {
    McpSchema.CallToolResult result = call("get_component_doc", Map.of("class", ""));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    String errorMsg = text.text();
    assertTrue(errorMsg.startsWith(Result.INVALID_ARGUMENT), "empty string is invalid_argument");
    assertTrue(errorMsg.contains("class"), "error message names the offending argument");
  }

  @Test
  void getComponentDocPathEmptyStringIsInvalidArgument() {
    McpSchema.CallToolResult result = call("get_component_doc", Map.of("path", ""));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    String errorMsg = text.text();
    assertTrue(errorMsg.startsWith(Result.INVALID_ARGUMENT), "empty string is invalid_argument");
    assertTrue(errorMsg.contains("path"), "error message names the offending argument");
  }

  @Test
  void getComponentDocBothGivenWithValidValuesIsRejected() {
    var pattern = channel.patterns.get(0);
    McpSchema.CallToolResult result = call("get_component_doc", Map.of(
        "class", GradientPattern.class.getName(), "path", pattern.getCanonicalPath()));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    String errorMsg = text.text();
    assertTrue(errorMsg.startsWith(Result.INVALID_ARGUMENT),
        "both-given check rejects with valid values");
    assertTrue(errorMsg.contains("both"), "error message indicates both were given");
  }

  @Test
  void getComponentDocResolvesByPathSameAsByClass() {
    var pattern = channel.patterns.get(0);
    Map<String, Object> byClass = structured(
        call("get_component_doc", Map.of("class", GradientPattern.class.getName())));
    Map<String, Object> byPath = structured(
        call("get_component_doc", Map.of("path", pattern.getCanonicalPath())));
    assertEquals(byClass.get("class"), byPath.get("class"));
    assertEquals(byClass.get("summary"), byPath.get("summary"));
  }

  @Test
  void getComponentDocPathToParameterIsInvalidArgument() {
    McpSchema.CallToolResult result = call("get_component_doc",
        Map.of("path", channel.fader.getCanonicalPath()));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  void getComponentDocPathToNonDocumentableComponentIsNotFound() {
    McpSchema.CallToolResult result = call("get_component_doc",
        Map.of("path", channel.getCanonicalPath()));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.NOT_FOUND),
        "path to channel (not a pattern/effect/modulator) is not_found");
  }

  @Test
  void getComponentDocShortNameReturnsFullFqcn() {
    Map<String, Object> payload = structured(
        call("get_component_doc", Map.of("class", "GradientPattern")));
    assertEquals(GradientPattern.class.getName(), payload.get("class"),
        "short name is resolved to full FQCN");
  }

  @Test
  void getComponentDocEmptyClassWithValidPathIsRejected() {
    var pattern = channel.patterns.get(0);
    McpSchema.CallToolResult result = call("get_component_doc", Map.of(
        "class", "",
        "path", pattern.getCanonicalPath()));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    String errorMsg = text.text();
    assertTrue(errorMsg.startsWith(Result.INVALID_ARGUMENT),
        "both-given check rejects with empty class and valid path");
    assertTrue(errorMsg.contains("both"), "error message indicates both were given");
  }

  @Test
  void getFixtureFormatReturnsBundledDoc() {
    Map<String, Object> payload = structured(call("get_fixture_format", Map.of()));
    Object markdownObj = payload.get("markdown");
    assertInstanceOf(String.class, markdownObj);
    String markdown = (String) markdownObj;
    assertFalse(markdown.isEmpty(), "doc is non-empty");
    // Sentinels proving the real bundled doc loaded from the classpath, not a stub.
    assertTrue(markdown.contains("$instance"), "documents $instance");
    assertTrue(markdown.contains("segments"), "documents output segments");
    assertTrue(markdown.contains("kinet"), "documents the kinet protocol");
    assertTrue(markdown.contains("strip"), "documents the strip component type");
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
    assertEquals(Boolean.TRUE, knobs.get("documented"),
        "MacroKnobs ships a catalog entry");
    Map<String, Object> undocumented = modulators.stream()
        .filter(m -> UndocumentedModulator.class.getName().equals(m.get("class")))
        .findFirst().orElseThrow();
    assertEquals(Boolean.FALSE, undocumented.get("documented"),
        "UndocumentedModulator has no catalog entry");
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
    assertNotNull(payload.get("litFraction"));
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
  void getFrameLitThresholdDefaultsAndOverrides() {
    // Fixture is solid red (max channel 255): lit at the default threshold, unlit once
    // the caller raises litThreshold above 255.
    McpSchema.CallToolResult defaultResult = call("get_frame", Map.of());
    assertEquals(1.0, structured(defaultResult).get("litFraction"), "default threshold: solid red is lit");

    McpSchema.CallToolResult overriddenResult = call("get_frame", Map.of("litThreshold", 255));
    assertEquals(0.0, structured(overriddenResult).get("litFraction"),
        "max channel (255) does not exceed a litThreshold of 255");
  }

  @Test
  void getFrameLitThresholdOutOfRangeIsRejectedBySchema() {
    // 256 is outside the schema's declared 0-255 bound, so the SDK rejects it before the
    // handler runs; pin the SDK's own rejection text so this fails if the schema bound is
    // ever dropped, distinguishing it from a handler-side invalid_argument seam.
    McpSchema.CallToolResult result = call("get_frame", Map.of("litThreshold", 256));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertFalse(text.text().startsWith(Result.INVALID_ARGUMENT),
        "schema rejection happens in the SDK, before any handler-side check");
  }

  @Test
  void getFrameLitThresholdZeroEqualsNonBlackFraction() {
    // litThreshold=0 makes "max > litThreshold" identical to the nonBlack condition
    // ("max > 0"), so the two fields collapse into each other at this boundary.
    Map<String, Object> payload = structured(call("get_frame", Map.of("litThreshold", 0)));
    assertEquals(payload.get("nonBlackFraction"), payload.get("litFraction"));
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
  void getViewsDescribesViewsAndModelTags() {
    Map<String, Object> payload = structured(call("get_views", Map.of()));

    List<Map<String, Object>> views = (List<Map<String, Object>>) payload.get("views");
    Map<String, Object> viewEntry = views.stream()
        .filter(v -> Resolve.canonicalPath(view).equals(v.get("path")))
        .findFirst().orElseThrow(() -> new AssertionError("fixture view not listed"));
    assertEquals("Whole Grid", viewEntry.get("label"));
    assertEquals("grid", viewEntry.get("selector"));
    assertEquals(Boolean.TRUE, viewEntry.get("enabled"));
    assertNotNull(viewEntry.get("cuePath"));
    // GridModel(w, h) is a flat point list with no submodel children, so no tag selector
    // can match a fixture on it — live selector-match feedback (numFixtures > 0 for a
    // selector that matches submodels) is covered against a tagged fixture in ViewsTest.
    assertEquals(0, ((Number) viewEntry.get("numFixtures")).intValue());

    List<Map<String, Object>> modelTags = (List<Map<String, Object>>) payload.get("modelTags");
    assertTrue(modelTags.stream().anyMatch(t -> "grid".equals(t.get("tag"))),
        "modelTags: " + modelTags);

    // assertions on shape only — "assignments" is exercised for content in ViewsTest
    assertNotNull(payload.get("assignments"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void describeModelReportsRootShapeAndProjectLevelFacts() {
    Map<String, Object> payload = structured(call("describe_model", Map.of()));

    assertEquals(lx.getModel().getPath(), payload.get("path"));
    assertEquals(64, ((Number) payload.get("size")).intValue());
    List<Number> range = (List<Number>) payload.get("pointIndexRange");
    assertEquals(0, range.get(0).intValue());
    assertEquals(63, range.get(1).intValue());
    assertEquals(Boolean.TRUE, payload.get("contiguous"));
    assertNotNull(payload.get("bounds"));
    assertNotNull(payload.get("center"));
    // GridModel(8, 8) is flat: no submodel children, so childCount is 0; "children" is
    // still present (depth > 0) but empty.
    assertEquals(0, ((Number) payload.get("childCount")).intValue());
    assertTrue(((List<?>) payload.get("children")).isEmpty());

    assertNotNull(payload.get("modelName"));
    assertEquals(lx.structure.isStatic.isOn(), payload.get("isStatic"));
    assertEquals(64, ((Number) payload.get("totalPoints")).intValue());
    assertEquals(0, ((Number) payload.get("fixtureCount")).intValue());
    List<Map<String, Object>> tagVocabulary = (List<Map<String, Object>>) payload.get("tagVocabulary");
    assertTrue(tagVocabulary.stream().anyMatch(t -> "grid".equals(t.get("tag"))),
        "tagVocabulary: " + tagVocabulary);
  }

  @Test
  @SuppressWarnings("unchecked")
  void listFixturesReportsRootFactsWithNoFixturesOnAStaticModel() {
    // The class-level LX is constructed with an immutable GridModel (isStatic true),
    // against which LXStructure.addFixture throws — full fixture-add coverage (index
    // range, transform, submodels, undo) lives in FixturesTest's dynamic-structure LX.
    Map<String, Object> payload = structured(call("list_fixtures", Map.of()));

    assertEquals(lx.structure.isStatic.isOn(), payload.get("isStatic"));
    assertNotNull(payload.get("modelName"));
    assertEquals(64, ((Number) payload.get("totalPoints")).intValue());
    assertNotNull(payload.get("outputError"));
    assertTrue(((List<Map<String, Object>>) payload.get("fixtures")).isEmpty());
  }

  @Test
  void getFixtureUnknownPathIsNotFound() {
    McpSchema.CallToolResult result = call("get_fixture", Map.of("path", "/lx/structure/fixture/1"));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.NOT_FOUND));
  }

  @Test
  void getFixtureNonFixturePathIsInvalidArgument() {
    McpSchema.CallToolResult result = call("get_fixture", Map.of("path", channel.getCanonicalPath()));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  void getFixtureNegativeDepthIsInvalidArgument() {
    // channel.getCanonicalPath() isn't a fixture path either, but depth validation runs
    // before path resolution — a bad depth is rejected regardless of what path was given.
    McpSchema.CallToolResult result = call("get_fixture",
        Map.of("path", "/lx/structure/fixture/1", "depth", -1));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  void getOutputMapWithNoFixturesReturnsEmptyList() {
    // The shared LX's model is static-immutable (see the field javadoc), so there is nothing
    // to add — mutation-based happy paths (a real ARTNET/JsonFixture footprint) live in
    // FixturesTest against a dynamic-structure LX.
    Map<String, Object> payload = structured(call("get_output_map", Map.of()));
    assertNotNull(payload.get("note"));
    assertNotNull(payload.get("outputError"));
    assertTrue(((List<Map<String, Object>>) payload.get("fixtures")).isEmpty());
  }

  @Test
  void getOutputMapUnknownPathIsNotFound() {
    McpSchema.CallToolResult result = call("get_output_map", Map.of("path", "/lx/structure/fixture/1"));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.NOT_FOUND));
  }

  @Test
  void getOutputMapNonFixturePathIsInvalidArgument() {
    McpSchema.CallToolResult result = call("get_output_map", Map.of("path", channel.getCanonicalPath()));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  void setFixtureParamsUnknownPathIsNotFound() {
    McpSchema.CallToolResult result = call("set_fixture_params",
        Map.of("path", "/lx/structure/fixture/1", "params", Map.of("x", 1.0)));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.NOT_FOUND));
  }

  @Test
  void setFixtureParamsNonFixturePathIsInvalidArgument() {
    McpSchema.CallToolResult result = call("set_fixture_params",
        Map.of("path", channel.getCanonicalPath(), "params", Map.of("x", 1.0)));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  void setFixtureParamsEmptyParamsIsInvalidArgument() {
    McpSchema.CallToolResult result = call("set_fixture_params",
        Map.of("path", "/lx/structure/fixture/1", "params", Map.of()));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  void setFixtureTagsUnknownPathIsNotFound() {
    McpSchema.CallToolResult result = call("set_fixture_tags",
        Map.of("path", "/lx/structure/fixture/1", "tags", List.of("cube")));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.NOT_FOUND));
  }

  @Test
  void setFixtureTagsNonFixturePathIsInvalidArgument() {
    McpSchema.CallToolResult result = call("set_fixture_tags",
        Map.of("path", channel.getCanonicalPath(), "tags", List.of("cube")));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  void setFixtureTagsEmptyTagsIsInvalidArgument() {
    McpSchema.CallToolResult result = call("set_fixture_tags",
        Map.of("path", "/lx/structure/fixture/1", "tags", List.of()));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  @SuppressWarnings("unchecked")
  void listAvailableFixturesOverMcpReportsClassesAndFixturesDirectory() {
    Map<String, Object> payload = structured(call("list_available_fixtures", Map.of()));
    List<String> classes = (List<String>) payload.get("classes");
    assertTrue(classes.contains("GridFixture"), "classes: " + classes);
    assertTrue(payload.containsKey("jsonTypes"));
    assertTrue(payload.containsKey("errors"));
    assertNotNull(payload.get("fixturesDirectory"));
  }

  @Test
  void addFixtureWithBothClassAndTypeIsInvalidArgument() {
    McpSchema.CallToolResult result = call("add_fixture",
        Map.of("class", "GridFixture", "type", "SomeType"));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  void addFixtureWithNeitherClassNorTypeIsInvalidArgument() {
    McpSchema.CallToolResult result = call("add_fixture", Map.of());
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  void addFixtureWithUnknownClassIsInvalidArgument() {
    McpSchema.CallToolResult result = call("add_fixture", Map.of("class", "NoSuchFixtureClass"));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  void addFixtureWithUnknownTypeIsInvalidArgument() {
    McpSchema.CallToolResult result = call("add_fixture", Map.of("type", "NoSuchJsonType"));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  void addFixtureWithTypeAndIndexIsInvalidArgumentAndAddsNothing() {
    // 'type' always appends via a single-command AddFixture(String); an explicit 'index'
    // has no single-command way to be honored, so the combination is rejected outright
    // rather than faked with a second MoveFixture command (see Fixtures.addFixtureByType).
    McpSchema.CallToolResult result = call("add_fixture",
        Map.of("type", "SomeType", "index", 0));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  void addFixtureOnAStaticStructureIsInvalidArgument() {
    // lx's model is static/immutable (see class javadoc) — the happy-path add lives in
    // FixtureLifecycleTest against a dynamic-structure LX; this exercises the static-guard
    // rejection end-to-end over real HTTP, distinct from the "not_found"/"invalid_argument"
    // path-resolution failures above.
    McpSchema.CallToolResult result = call("add_fixture", Map.of("class", "GridFixture"));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  void removeFixtureUnknownPathIsNotFound() {
    McpSchema.CallToolResult result = call("remove_fixture", Map.of("path", "/lx/structure/fixture/1"));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.NOT_FOUND));
  }

  @Test
  void removeFixtureNonFixturePathIsInvalidArgument() {
    McpSchema.CallToolResult result = call("remove_fixture", Map.of("path", channel.getCanonicalPath()));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  void moveFixtureUnknownPathIsNotFound() {
    McpSchema.CallToolResult result = call("move_fixture",
        Map.of("path", "/lx/structure/fixture/1", "index", 0));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.NOT_FOUND));
  }

  @Test
  void moveFixtureNonFixturePathIsInvalidArgument() {
    McpSchema.CallToolResult result = call("move_fixture",
        Map.of("path", channel.getCanonicalPath(), "index", 0));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  void duplicateFixtureUnknownPathIsNotFound() {
    McpSchema.CallToolResult result = call("duplicate_fixture", Map.of("path", "/lx/structure/fixture/1"));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.NOT_FOUND));
  }

  @Test
  void duplicateFixtureNonFixturePathIsInvalidArgument() {
    McpSchema.CallToolResult result = call("duplicate_fixture", Map.of("path", channel.getCanonicalPath()));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  @SuppressWarnings("unchecked")
  void reloadFixturesOverMcpReturnsRefreshedTypeListAndFixtures() {
    // lx's model is static/immutable (see class javadoc), against which LXStructure.reload()
    // is a documented no-op (checkStaticModel-style early return) — this exercises the tool
    // end-to-end over real HTTP without needing a dynamic structure; the actual reload
    // behavior (picking up a disk edit) is covered in FixtureEditingTest.
    Map<String, Object> payload = structured(call("reload_fixtures", Map.of()));
    assertTrue(payload.containsKey("jsonTypes"));
    assertTrue(payload.containsKey("errors"));
    assertTrue(((List<Map<String, Object>>) payload.get("fixtures")).isEmpty());
  }

  @Test
  @SuppressWarnings("unchecked")
  void listFixturesReportsChildCountAndSubmodelCountSeparatelyAndOmitsSubfixturesAvailableFlagWhenHealthy() {
    // No fixtures exist on this static-model LX (full fixture-tree coverage against a
    // dynamic-structure LX lives in SubfixtureTreeTest/FixturesTest), but the payload shape
    // — no fixtures listed, and no degraded-reflection flag on a healthy run — is still
    // exercised over the real HTTP transport here.
    Map<String, Object> payload = structured(call("list_fixtures", Map.of()));
    assertFalse(payload.containsKey("subfixturesAvailable"),
        "only present when the reflective accessor failed to initialize");
    assertTrue(((List<Map<String, Object>>) payload.get("fixtures")).isEmpty());
  }

  @Test
  void describeModelUnknownPathIsNotFound() {
    McpSchema.CallToolResult result = call("describe_model", Map.of("path", "/no/such/node"));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.NOT_FOUND));
  }

  @Test
  void describeModelNegativeDepthIsInvalidArgument() {
    McpSchema.CallToolResult result = call("describe_model", Map.of("depth", -1));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
  }

  @Test
  void describeModelAddressesTheRootItselfByItsOwnPath() {
    Map<String, Object> payload = structured(
        call("describe_model", Map.of("path", lx.getModel().getPath())));
    assertEquals(lx.getModel().getPath(), payload.get("path"));
    assertNotNull(payload.get("modelName"), "addressing the root by its own path is still the root");
  }

  @Test
  @SuppressWarnings("unchecked")
  void addViewOverMcpComposesAViewAndWarnsOnNoMatch() {
    int before = lx.structure.views.views.size();

    // GridModel's root carries the "grid" tag but has no descendant submodels — a tag
    // selector never matches a fixture on it (LXView selectors only match descendants),
    // so this is the warning path.
    Map<String, Object> matching = structured(call("add_view",
        Map.of("label", "All Grid", "selector", "grid")));
    String matchingPath = (String) matching.get("path");
    try {
      assertEquals(before + 1, lx.structure.views.views.size());
      heronarts.lx.structure.view.LXViewDefinition added =
          lx.structure.views.views.get(lx.structure.views.views.size() - 1);
      assertEquals(Resolve.canonicalPath(added), matchingPath);
      assertEquals("All Grid", matching.get("label"));
      assertEquals("grid", matching.get("selector"));
      assertEquals("relative", matching.get("normalization"), "default normalization");
      assertEquals("global", matching.get("orientation"), "default orientation");
      assertNotNull(matching.get("cuePath"));
      assertEquals(0, ((Number) matching.get("numFixtures")).intValue());
      assertEquals("selector matched no fixtures — check modelTags from get_views",
          matching.get("warning"));
    } finally {
      structured(call("remove_view", Map.of("path", matchingPath)));
    }

    Map<String, Object> withEnums = structured(call("add_view", Map.of(
        "label", "Absolute Group", "selector", "grid",
        "normalization", "absolute", "orientation", "group")));
    try {
      assertEquals("absolute", withEnums.get("normalization"));
      assertEquals("group", withEnums.get("orientation"));
    } finally {
      structured(call("remove_view", Map.of("path", withEnums.get("path"))));
    }
    assertEquals(before, lx.structure.views.views.size());
  }

  @Test
  void addViewRejectsAnUnknownEnumValue() {
    // The SDK rejects non-enum values against inputSchema before the handler runs (see
    // getFrameBadViewIsInvalidArgument) — with its own message, not our invalid_argument
    // wire code, so this pins the SDK-side rejection rather than the handler's own check.
    McpSchema.CallToolResult result = call("add_view", Map.of(
        "label", "Bad", "selector", "grid", "normalization", "sideways"));
    assertEquals(Boolean.TRUE, result.isError());
  }

  @Test
  void removeViewOverMcpRemovesItAndDeviceViewMappingRoundTripsByLabel() {
    Map<String, Object> added = structured(
        call("add_view", Map.of("label", "Removable", "selector", "grid")));
    String viewPath = (String) added.get("path");

    // set_parameter maps the pattern's view by the created view's label, not its index.
    var pattern = channel.patterns.get(0);
    try {
      Map<String, Object> mapped = structured(call("set_parameter",
          Map.of("path", pattern.view.getCanonicalPath(), "value", "Removable")));
      assertEquals("Removable", mapped.get("formatted"));
      assertEquals(viewPath, Resolve.canonicalPath(pattern.view.getObject()));
    } finally {
      Map<String, Object> removed = structured(call("remove_view", Map.of("path", viewPath)));
      assertEquals(viewPath, removed.get("removed"));
      assertEquals("view", removed.get("kind"));
      // Leave the shared fixture pattern back on Default for later tests.
      structured(call("set_parameter", Map.of("path", pattern.view.getCanonicalPath(), "value", 0)));
    }

    McpSchema.CallToolResult stale = call("get_parameter", Map.of("path", viewPath));
    assertEquals(Boolean.TRUE, stale.isError());
  }

  @Test
  void setParameterAcceptsAnOptionNameForADiscreteParameter() {
    Map<String, Object> added = structured(
        call("add_view", Map.of("label", "Named", "selector", "grid")));
    try {
      Map<String, Object> result = structured(call("set_parameter",
          Map.of("path", channel.view.getCanonicalPath(), "value", "Named")));
      assertEquals("Named", result.get("formatted"));
      assertEquals(Resolve.canonicalPath(channel.view.getObject()), added.get("path"));

      McpSchema.CallToolResult unknown = call("set_parameter",
          Map.of("path", channel.view.getCanonicalPath(), "value", "NotAView"));
      assertEquals(Boolean.TRUE, unknown.isError());
      McpSchema.TextContent text =
          assertInstanceOf(McpSchema.TextContent.class, unknown.content().get(0));
      assertTrue(text.text().startsWith(Result.INVALID_ARGUMENT));
    } finally {
      // Reset so later tests aren't affected by this channel's view mapping.
      structured(call("set_parameter", Map.of("path", channel.view.getCanonicalPath(), "value", 0)));
      structured(call("remove_view", Map.of("path", added.get("path"))));
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void listParametersResolvesIntoTheStructureViewTree() {
    String viewPath = Resolve.canonicalPath(view);
    Map<String, Object> payload = structured(call("list_parameters", Map.of("path", viewPath)));
    assertEquals(viewPath, payload.get("path"));
    List<Map<String, Object>> parameters = (List<Map<String, Object>>) payload.get("parameters");
    assertTrue(parameters.stream().anyMatch(p -> "selector".equals(paramKey(p))),
        "parameters: " + parameters);

    Map<String, Object> selector = structured(
        call("get_parameter", Map.of("path", Resolve.canonicalPath(view.selector))));
    assertEquals("grid", selector.get("value"));
  }

  private static String paramKey(Map<String, Object> parameterEntry) {
    String path = (String) parameterEntry.get("path");
    return path.substring(path.lastIndexOf('/') + 1);
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

  @Test
  @SuppressWarnings("unchecked")
  void getTempoDescribesTransportStateAndPaths() {
    lx.engine.tempo.bpm.setValue(140);

    Map<String, Object> payload = structured(call("get_tempo", Map.of()));

    Map<String, Object> bpm = (Map<String, Object>) payload.get("bpm");
    assertEquals(lx.engine.tempo.bpm.getCanonicalPath(), bpm.get("path"));
    assertEquals(140.0, ((Number) bpm.get("value")).doubleValue(), 1e-9);

    Map<String, Object> clockSource = (Map<String, Object>) payload.get("clockSource");
    assertEquals(lx.engine.tempo.clockSource.getCanonicalPath(), clockSource.get("path"));

    Map<String, Object> launchQuantization = (Map<String, Object>) payload.get("launchQuantization");
    assertEquals(lx.engine.tempo.launchQuantization.getCanonicalPath(), launchQuantization.get("path"));
    assertNotNull(launchQuantization.get("options"));

    assertEquals(lx.engine.tempo.tap.getCanonicalPath(), payload.get("tapPath"));
    assertEquals(lx.engine.tempo.nudgeUp.getCanonicalPath(), payload.get("nudgeUpPath"));
    assertEquals(lx.engine.tempo.nudgeDown.getCanonicalPath(), payload.get("nudgeDownPath"));
    assertEquals(lx.engine.tempo.getTriggerSource().getCanonicalPath(), payload.get("triggerSourcePath"));
    assertNotNull(payload.get("beatCount"));
    assertNotNull(payload.get("periodMs"));
  }

  @Test
  void saveSwatchSetSwatchAndRemoveSwatchFlowOverMcp() {
    Map<String, Object> saved = structured(call("save_swatch", Map.of()));
    String path = (String) saved.get("path");
    assertNotNull(path);
    assertNotNull(saved.get("recallPath"));
    LXSwatch swatch = (LXSwatch) LXPath.get(lx, path);
    try {
      lx.engine.palette.transitionEnabled.setValue(false);
      swatch.colors.get(0).primary.setColor(0xff445566);

      Map<String, Object> applied = structured(call("set_swatch", Map.of("path", path)));
      assertEquals(path, applied.get("applied"));
      assertEquals(lx.engine.palette.swatch.getCanonicalPath(), applied.get("activeSwatch"));
      assertEquals(0xff445566, lx.engine.palette.swatch.colors.get(0).getColor(),
          "set_swatch applied the saved colors onto the active swatch");

      lx.command.undo();
      assertNotEquals(0xff445566, lx.engine.palette.swatch.colors.get(0).getColor(),
          "undo restores the active swatch's prior colors");
    } finally {
      Map<String, Object> removed = structured(call("remove_swatch", Map.of("path", path)));
      assertEquals(path, removed.get("removed"));
      assertFalse(lx.engine.palette.swatches.contains(swatch));
    }
  }

  @Test
  void moveSwatchReordersTheSavedSwatchList() {
    int before = lx.engine.palette.swatches.size();
    Map<String, Object> first = structured(call("save_swatch", Map.of()));
    Map<String, Object> second = structured(call("save_swatch", Map.of()));
    LXSwatch firstSwatch = (LXSwatch) LXPath.get(lx, (String) first.get("path"));
    LXSwatch secondSwatch = (LXSwatch) LXPath.get(lx, (String) second.get("path"));
    try {
      assertEquals(before, firstSwatch.getIndex());
      assertEquals(before + 1, secondSwatch.getIndex());

      Map<String, Object> moved = structured(
          call("move_swatch", Map.of("path", firstSwatch.getCanonicalPath(), "index", before + 1)));
      assertEquals(before + 1, ((Number) moved.get("index")).intValue());
      assertEquals(before + 1, firstSwatch.getIndex());
      assertEquals(before, secondSwatch.getIndex());

      lx.command.undo();
      assertEquals(before, firstSwatch.getIndex(), "undo restores the original order");
    } finally {
      lx.engine.palette.removeSwatch(firstSwatch);
      lx.engine.palette.removeSwatch(secondSwatch);
    }
  }

  @Test
  void moveSwatchOutOfRangeIsInvalidArgument() {
    Map<String, Object> saved = structured(call("save_swatch", Map.of()));
    LXSwatch swatch = (LXSwatch) LXPath.get(lx, (String) saved.get("path"));
    try {
      McpSchema.CallToolResult result = call("move_swatch",
          Map.of("path", swatch.getCanonicalPath(), "index", 99));
      assertEquals(Boolean.TRUE, result.isError());
    } finally {
      lx.engine.palette.removeSwatch(swatch);
    }
  }

  @Test
  void addColorThenRemoveColorOnTheActiveSwatchOverMcp() {
    int before = lx.engine.palette.swatch.colors.size();

    Map<String, Object> added = structured(call("add_color", Map.of()));
    assertEquals(lx.engine.palette.swatch.getCanonicalPath(), added.get("swatch"));
    assertEquals(before + 1, lx.engine.palette.swatch.colors.size());
    assertNotNull(added.get("primaryPath"));

    lx.command.undo();
    assertEquals(before, lx.engine.palette.swatch.colors.size(), "undo removes the added color");

    LXDynamicColor readded = addColorViaTool();
    String readdedPath = readded.getCanonicalPath();

    Map<String, Object> removed = structured(call("remove_color", Map.of()));
    assertEquals(readdedPath, removed.get("removed"));
    assertEquals(before, lx.engine.palette.swatch.colors.size());

    lx.command.undo();
    assertEquals(before + 1, lx.engine.palette.swatch.colors.size(), "undo restores the removed color");
    lx.command.undo();
    assertEquals(before, lx.engine.palette.swatch.colors.size(), "clean up the leftover add");
  }

  private LXDynamicColor addColorViaTool() {
    Map<String, Object> added = structured(call("add_color", Map.of()));
    return (LXDynamicColor) LXPath.get(lx, (String) added.get("path"));
  }

  @Test
  void removeColorRejectsTheLastColorInASwatch() {
    Map<String, Object> saved = structured(call("save_swatch", Map.of()));
    LXSwatch swatch = (LXSwatch) LXPath.get(lx, (String) saved.get("path"));
    try {
      assertEquals(1, swatch.colors.size());
      McpSchema.CallToolResult result = call("remove_color",
          Map.of("swatch", swatch.getCanonicalPath()));
      assertEquals(Boolean.TRUE, result.isError());
      assertEquals(1, swatch.colors.size());
    } finally {
      lx.engine.palette.removeSwatch(swatch);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void listSnapshotsReportsEngineSettingsWithPaths() {
    Map<String, Object> payload = structured(call("list_snapshots", Map.of()));
    assertTrue(payload.get("snapshots") instanceof List);

    List<Map<String, Object>> settings = (List<Map<String, Object>>) payload.get("settings");
    assertEquals(8, settings.size());
    Set<String> paths = settings.stream().map(s -> (String) s.get("path")).collect(Collectors.toSet());
    assertEquals(Set.of(
            lx.engine.snapshots.recallMixer.getCanonicalPath(),
            lx.engine.snapshots.recallPattern.getCanonicalPath(),
            lx.engine.snapshots.recallEffect.getCanonicalPath(),
            lx.engine.snapshots.recallModulation.getCanonicalPath(),
            lx.engine.snapshots.recallMaster.getCanonicalPath(),
            lx.engine.snapshots.recallOutput.getCanonicalPath(),
            lx.engine.snapshots.transitionEnabled.getCanonicalPath(),
            lx.engine.snapshots.transitionTimeSecs.getCanonicalPath()),
        paths);
  }

  @Test
  @SuppressWarnings("unchecked")
  void snapshotLifecycleOverMcp() {
    int before = ((List<Object>) structured(call("list_snapshots", Map.of())).get("snapshots")).size();

    channel.fader.setValue(0.7);
    Map<String, Object> added = structured(call("add_snapshot", Map.of("label", "My Look")));
    String path = (String) added.get("path");
    try {
      assertEquals("My Look", added.get("label"));
      assertNotNull(added.get("id"));
      assertNotNull(added.get("transitionTimeSecs"));

      Map<String, Object> afterAdd = structured(call("list_snapshots", Map.of()));
      List<Map<String, Object>> snapshots = (List<Map<String, Object>>) afterAdd.get("snapshots");
      assertEquals(before + 1, snapshots.size());
      assertEquals(path, snapshots.get(snapshots.size() - 1).get("path"));

      // Recalling restores the captured fader value; immediate=true forces an instant
      // apply even with transitions enabled, so the assertion holds synchronously.
      channel.fader.setValue(0.2);
      lx.engine.snapshots.transitionEnabled.setValue(true);
      try {
        Map<String, Object> recalled = structured(
            call("recall_snapshot", Map.of("path", path, "immediate", true)));
        assertEquals(path, recalled.get("recalled"));
        assertEquals(Boolean.TRUE, recalled.get("immediate"));
        assertEquals(0.7, channel.fader.getValue(), 1e-9);
        assertTrue(lx.engine.snapshots.transitionEnabled.isOn(),
            "immediate recall restores the engine's transition setting afterwards");
      } finally {
        lx.engine.snapshots.transitionEnabled.setValue(false);
      }

      // update_snapshot recaptures the current state.
      channel.fader.setValue(0.3);
      Map<String, Object> updated = structured(call("update_snapshot", Map.of("path", path)));
      assertEquals(path, updated.get("updated"));

      channel.fader.setValue(0.9);
      structured(call("recall_snapshot", Map.of("path", path)));
      assertEquals(0.3, channel.fader.getValue(), 1e-9);
    } finally {
      Map<String, Object> removed = structured(call("remove_snapshot", Map.of("path", path)));
      assertEquals(path, removed.get("removed"));
      assertEquals("snapshot", removed.get("kind"));
      channel.fader.setValue(1.0);
    }

    Map<String, Object> afterRemove = structured(call("list_snapshots", Map.of()));
    assertEquals(before, ((List<Object>) afterRemove.get("snapshots")).size());
  }

  @Test
  void recallSnapshotUnknownPathIsNotFound() {
    McpSchema.CallToolResult result = call("recall_snapshot", Map.of("path", "/lx/snapshots/snapshot/99"));
    assertEquals(Boolean.TRUE, result.isError());
  }

  @Test
  @SuppressWarnings("unchecked")
  void listChannelsPayloadContainsNoNullPaths() {
    // Regression: unregistered parameters (e.g. isAutoMuted) have paths that contain "/null"
    // (LXPath.java:85-93). This test walks the entire payload recursively and asserts that
    // no string value equals or contains "/null" — catches the next unregistered parameter
    // without touching dozens of call sites.
    Map<String, Object> payload = structured(call("list_channels", Map.of("detail", "full")));
    StringBuilder errors = new StringBuilder();
    walkForNullPaths(payload, "", errors);
    assertEquals("", errors.toString(),
        "payload contains no unregistered-parameter \"/null\" paths: " + errors);
  }

  @Test
  void listModulationsPayloadContainsNoNullPaths() {
    Map<String, Object> knobs = structured(
        call("add_modulator", Map.of("class", MacroKnobs.class.getName())));
    Map<String, Object> wired = structured(call("wire_modulator", Map.of(
        "sourcePath", knobs.get("path") + "/macro1",
        "targetPath", channel.fader.getCanonicalPath())));
    Map<String, Object> triggerBank = structured(
        call("add_modulator", Map.of("class", MacroTriggers.class.getName())));
    Map<String, Object> wiredTrigger = structured(call("wire_trigger", Map.of(
        "sourcePath", triggerBank.get("path") + "/macro1",
        "targetPath", channel.enabled.getCanonicalPath())));
    // Regression guard, not a live reproduction: this PR didn't touch modulation wiring, and
    // every component reachable from list_modulations already overrides getPath() or is
    // addParameter'd/addChild'd at construction, so this can't currently fail. It protects
    // against future drift, not the hazard this PR fixes.
    try {
      Map<String, Object> payload = structured(call("list_modulations", Map.of("detail", "full")));
      StringBuilder errors = new StringBuilder();
      walkForNullPaths(payload, "", errors);
      assertEquals("", errors.toString(),
          "payload contains no unregistered-parameter \"/null\" paths: " + errors);
    } finally {
      structured(call("remove_modulation", Map.of("path", wiredTrigger.get("path"))));
      structured(call("remove_modulation", Map.of("path", wired.get("path"))));
    }
  }

  @Test
  void getPalettePayloadContainsNoNullPaths() {
    // Regression guard, not a live reproduction: every component reachable from get_palette
    // (LXDynamicColor, LXSwatch, ColorParameter sub-fields, LXPalette's own parameters) always
    // overrides getPath() or is addParameter'd at construction, so reverting GetPalette's
    // conditional-put guards would not fail this test. The only currently-reachable instance
    // of the "/null" hazard is LXAbstractChannel.isAutoMuted, covered by list_channels'
    // existing null-path walk test.
    lx.engine.palette.saveSwatch();
    Map<String, Object> payload = structured(call("get_palette", Map.of()));
    StringBuilder errors = new StringBuilder();
    walkForNullPaths(payload, "", errors);
    assertEquals("", errors.toString(),
        "payload contains no unregistered-parameter \"/null\" paths: " + errors);
  }

  @Test
  void getProjectInfoPayloadContainsNoNullPaths() {
    // Regression guard, not a live reproduction: every component reachable from
    // get_project_info (LXOutput's enabled/brightness/gamma/gammaMode, LXEngine's
    // speed/framesPerSecond) is addParameter'd at construction, so reverting
    // GetProjectInfo's conditional-put guards would not fail this test. See
    // getPalettePayloadContainsNoNullPaths for the one currently-reachable instance of the
    // hazard this PR guards against.
    Map<String, Object> payload = structured(call("get_project_info", Map.of()));
    StringBuilder errors = new StringBuilder();
    walkForNullPaths(payload, "", errors);
    assertEquals("", errors.toString(),
        "payload contains no unregistered-parameter \"/null\" paths: " + errors);
  }

  private static void walkForNullPaths(Object obj, String path, StringBuilder errors) {
    if (obj instanceof String str) {
      if (str.equals("/null") || str.contains("/null")) {
        errors.append("  ").append(path).append(": \"").append(str).append("\"\n");
      }
    } else if (obj instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        String key = entry.getKey().toString();
        String newPath = path.isEmpty() ? key : path + "." + key;
        Object value = entry.getValue();
        // A null path is supposed to omit the key entirely (docs/tool-conventions.md);
        // a literal JSON null under a path-carrying key is the same hazard wearing a
        // different disguise — canonicalPathOrNull forwarded a null straight into put().
        // Matched key-shaped rather than value-shaped (a bare `null` carries no signal of
        // what it would have been): "path"/"*Path" cover addressing fields, and "removed"
        // covers the wire convention every remove_* tool uses for the removed object's
        // canonical path (RemoveColor, RemoveChannel, RemoveModulator, RemoveView, …).
        if (value == null
            && (key.equals("path") || key.endsWith("Path") || key.equals("removed"))) {
          errors.append("  ").append(newPath).append(": null (key should be omitted)\n");
        }
        walkForNullPaths(value, newPath, errors);
      }
    } else if (obj instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        String newPath = path + "[" + i + "]";
        walkForNullPaths(list.get(i), newPath, errors);
      }
    }
  }

  @Test
  void unknownArgumentIsRejectedAsAnInputSchemaFailure() {
    // Regression for #115: list_modulations declares "scope", not "path" — the SDK
    // rejects the unrecognized argument before the handler runs. The raw SDK-worded
    // message from the pinned 2.0.0-RC1 validator hardcodes "structuredContent does not
    // match tool outputSchema" for this failure, misleadingly blaming the server's
    // response rather than the caller's arguments.
    McpSchema.CallToolResult result = call("list_modulations", Map.of("path", "/lx/mixer"));
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertFalse(text.text().contains("outputSchema"),
        "an input-argument failure must not blame the output schema: " + text.text());
    assertTrue(text.text().contains("input schema") || text.text().contains("inputSchema"),
        "the rewritten message should name the input schema: " + text.text());
    assertTrue(text.text().contains("path"),
        "the underlying validation error (naming the offending property) is preserved: "
            + text.text());
  }

  @Test
  void missingRequiredArgumentIsRejectedAsAnInputSchemaFailure() {
    // get_parameter requires "path"; omitting it hits the same SDK-side rejection path
    // as an unknown argument, before the handler ever runs.
    McpSchema.CallToolResult result = call("get_parameter", Map.of());
    assertEquals(Boolean.TRUE, result.isError());
    McpSchema.TextContent text = assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    assertFalse(text.text().contains("outputSchema"),
        "a missing-required-argument failure must not blame the output schema: " + text.text());
  }
}
