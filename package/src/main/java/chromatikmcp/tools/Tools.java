package chromatikmcp.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import chromatikmcp.domain.Resolve;
import chromatikmcp.engine.EngineExecutor;

/**
 * The MCP seam: turns each {@link LxTool} into the SDK's tool specification.
 *
 * <p>Wire shape (decided in PR-3, see {@code docs/tool-conventions.md}): success returns
 * {@code structuredContent} (always a JSON object) plus a TextContent mirror of the same
 * JSON for clients that don't read structured output; expected failure returns
 * {@code isError=true} with a {@code "code: message"} text. {@code outputSchema} is
 * deliberately not declared until the SDK GA bump.
 *
 * <p>Every handler is marshalled onto the LX engine thread via
 * {@link EngineExecutor#call} — Tomcat worker threads never touch {@code lx.*} directly.
 */
public final class Tools {

  /** Server-level MCP {@code instructions}, returned in the initialize result. */
  public static final String INSTRUCTIONS =
      "LX mixer semantics: a channel's patternMode is 'playlist' (one active pattern shows) "
          + "or 'blend' (all enabled patterns composite simultaneously, each scaled by its "
          + "compositeLevel parameter, 0-1). For pixels to reach fixtures, the whole chain must "
          + "be on: pattern contributing → channel enabled and fader > 0 → master "
          + "fader > 0 → engine output enabled (see get_project_info's output object). "
          + "Every component and parameter is addressed by its canonical LX path (e.g. "
          + "/lx/mixer/channel/1/fader); use list_parameters on any component path to discover "
          + "its parameters instead of guessing names. Scene colors flow from the global "
          + "palette (get_palette) to palette-linked patterns and effects; recall a saved "
          + "swatch via fire_trigger on its recallPath. A parameter with live modulations "
          + "reports its effective value plus baseValue; set_parameter moves the base. A new "
          + "wire_modulator wiring needs depth: pass its range argument or set rangePath "
          + "afterwards. Views are named model subsets (see get_views), created via add_view; a "
          + "device's view selector clips its rendering to that subset — map a device by "
          + "set_parameter on its 'view' path to the view's label (discrete/selector "
          + "parameters accept an option name string as well as an integer index) — and "
          + "'Default' inherits the view from the parent device/channel instead. describe_model reports "
          + "the model tree those view selectors match against, depth-limited (re-call with a "
          + "child's path or a higher depth to keep descending) — its pointIndexRange fields "
          + "index the same global color buffer get_frame reports. list_fixtures/get_fixture "
          + "report the physical wiring layer beneath that model tree — one entry per fixture, "
          + "with its output protocol (universe/channel/host) and geometry transform; "
          + "get_output_map is the diagnostic companion, reporting a declared/derived "
          + "universe/channel footprint per fixture (disclosed in its own description as "
          + "NOT LX's resolved allocation) plus LX's own collision report — useful after "
          + "add_fixture/set_fixture_params wiring changes; a "
          + "registered fixture parameter (wiring, placement, or a fixture-type-specific one "
          + "like a GridFixture's numRows) is settable via set_parameter on "
          + "'<fixture path>/<param>', but set_fixture_params is the batched, undo-grouped way "
          + "to set several at once and the only way to reach a JsonFixture's .lxf-declared "
          + "'jsonParameters' (e.g. controller IP strings), which have no canonical path at "
          + "all. set_fixture_tags sets a fixture's model tags (the get_views selector "
          + "vocabulary) with pre-write validation, since LX itself silently drops invalid "
          + "tags. reload_fixtures picks up .lxf edits made on disk — nothing does so "
          + "automatically. add_fixture/remove_fixture/move_fixture/duplicate_fixture "
          + "instantiate, delete, reorder, and clone fixtures (list_available_fixtures "
          + "reports what's addable); every fixture path is POSITIONAL "
          + "(/lx/structure/fixture/N, 1-indexed) and shifts after any of these calls — "
          + "re-list with list_fixtures rather than reuse a path from an earlier response. "
          + "get_tempo "
          + "reports the engine tempo (bpm, clock source, beat position) and its "
          + "launchQuantization: with quantization set, a fire_trigger on a quantized "
          + "trigger (pattern/clip launch) may report pending:true instead of firing "
          + "immediately, deferring to the next tempo boundary. Snapshots (list_snapshots, "
          + "add_snapshot, recall_snapshot) capture and recall whole-look state — mixer, "
          + "pattern, effect, and modulation values together — with an optional fade "
          + "controlled by the engine's transition settings. Every mutation tool above "
          + "changes only the running engine's in-memory state — nothing reaches disk until "
          + "save_project writes it; a restart or crash before that call loses the work.";

  private Tools() {}

  /**
   * The plain tool instances, independent of any live {@link LX} or executor — every
   * constructor argument here is either stateless or (for {@code getStatus}) supplied by
   * the caller. Used both to build MCP specifications and to dump the tool catalog for the
   * docs site (see {@code chromatikmcp.ToolCatalogDump}).
   */
  public static List<LxTool> allTools(GetStatus getStatus) {
    List<LxTool> tools = new ArrayList<>(List.of(
            new GetProjectInfo(),
            new SaveProject(),
            new SaveModel(),
            getStatus,
            new ListChannels(),
            new GetChannel(),
            new ListAvailable(ListAvailable.Kind.PATTERNS),
            new ListAvailable(ListAvailable.Kind.EFFECTS),
            new ListAvailable(ListAvailable.Kind.MODULATORS),
            new GetParameter(),
            new ListParameters(),
            new SetParameter(),
            new AddModulator(),
            new WireModulator(),
            new WireTrigger(),
            new RemoveModulation(),
            new RemoveModulator(),
            new MoveModulator(),
            new ListModulations(),
            new FireTrigger(),
            new GetComponentDoc(),
            new GetFixtureFormat(),
            new GetFrame(),
            new GetPalette(),
            new DescribeModel(),
            new SaveSwatch(),
            new SetSwatch(),
            new RemoveSwatch(),
            new MoveSwatch(),
            new AddColor(),
            new RemoveColor(),
            new AddChannel(),
            new RemoveChannel(),
            new AddPattern(),
            new RemovePattern(),
            new ActivatePattern(),
            new MovePattern(),
            new AddEffect(),
            new RemoveEffect(),
            new MoveEffect(),
            new GetViews(),
            new AddView(),
            new RemoveView(),
            new ListFixtures(),
            new GetFixture(),
            new GetOutputMap(),
            new ListAvailableFixtures(),
            new AddFixture(),
            new RemoveFixture(),
            new MoveFixture(),
            new DuplicateFixture(),
            new SetFixtureParams(),
            new SetFixtureTags(),
            new ReloadFixtures(),
            new GetTempo(),
            new ListMidiDevices(),
            new ListMidiMappings(),
            new ListMidiSurfaces(),
            new AddMidiMapping(),
            new RemoveMidiMapping(),
            new SetMidiInput(),
            new SetMidiSurfaceEnabled(),
            new ListSnapshots(),
            new AddSnapshot(),
            new RecallSnapshot(),
            new UpdateSnapshot(),
            new RemoveSnapshot()));

    // Built from the list above, not a static registry: appending after gives ApplyOperations
    // no way to see itself, and filtering readOnly() gives it no way to see a read tool either
    // — nested batches and read-in-batch are rejected structurally, not by a special case.
    Map<String, LxTool> mutationTools = new LinkedHashMap<>();
    for (LxTool tool : tools) {
      if (!tool.readOnly()) {
        mutationTools.put(tool.name(), tool);
      }
    }
    tools.add(new ApplyOperations(mutationTools));
    return tools;
  }

  public static List<McpServerFeatures.SyncToolSpecification> specifications(
      LX lx, EngineExecutor executor, GetStatus getStatus) {
    return allTools(getStatus).stream()
        .map(tool -> specification(tool, lx, executor))
        .toList();
  }

  // Package-private so the seam's exception mapping is testable without HTTP.
  static McpServerFeatures.SyncToolSpecification specification(LxTool tool, LX lx, EngineExecutor executor) {
    McpSchema.Tool spec = McpSchema.Tool.builder()
        .name(tool.name())
        .description(tool.description())
        .inputSchema(tool.inputSchema())
        .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(tool.readOnly()).build())
        .build();
    return McpServerFeatures.SyncToolSpecification.builder()
        .tool(spec)
        .callHandler((exchange, request) -> call(tool, lx, executor, request))
        .build();
  }

  private static McpSchema.CallToolResult call(
      LxTool tool, LX lx, EngineExecutor executor, McpSchema.CallToolRequest request) {
    Map<String, Object> args = (request.arguments() == null) ? Map.of() : request.arguments();
    Result<Map<String, Object>> result;
    try {
      result = executor.call(() -> invoke(tool, lx, args));
    } catch (Resolve.ResolveException e) {
      // Expected failure, not a defect: typed resolver errors map to wire codes, no log.
      result = Result.error(
          (e.failure == Resolve.Failure.NOT_FOUND) ? Result.NOT_FOUND : Result.INVALID_ARGUMENT,
          e.getMessage());
    } catch (RuntimeException e) {
      LX.error(e, "[Chromatik-MCP] Tool " + tool.name() + " failed");
      result = Result.error(Result.INTERNAL,
          (e.getMessage() == null) ? e.getClass().getSimpleName() : e.getMessage());
    }
    return switch (result) {
      case Result.Ok<Map<String, Object>> ok -> success(tool, ok.value(), null);
      case Result.OkImage<Map<String, Object>> ok -> {
        // Encoded here, on the HTTP worker thread — executor.call has already returned,
        // so the engine thread never pays for rasterization or PNG compression. The
        // supplier's contract (Result.OkImage) is to close only over immutable data.
        byte[] png;
        try {
          png = ok.png().get();
        } catch (RuntimeException e) {
          LX.error(e, "[Chromatik-MCP] Tool " + tool.name() + " failed to encode image");
          yield McpSchema.CallToolResult.builder()
              .isError(true)
              .addTextContent(Result.INTERNAL + ": failed to encode image")
              .build();
        }
        yield success(tool, ok.value(), png);
      }
      case Result.Error<Map<String, Object>> error -> McpSchema.CallToolResult.builder()
          .isError(true)
          .addTextContent(error.code() + ": " + error.message())
          .build();
    };
  }

  /**
   * Runs {@code tool.handle(lx, args)} and maps whatever it throws to a {@link Result},
   * exactly as the top-level MCP call handler does. Package-private re-entry seam: {@link
   * ApplyOperations} calls this directly (never {@link EngineExecutor#call}, whose javadoc
   * forbids re-entry from the engine thread it already holds) to dispatch each batched
   * operation through the same exception mapping every individual tool call gets, without
   * reimplementing any mutation.
   */
  static Result<Map<String, Object>> invoke(LxTool tool, LX lx, Map<String, Object> args) {
    try {
      return tool.handle(lx, args);
    } catch (Resolve.ResolveException e) {
      // Expected failure, not a defect: typed resolver errors map to wire codes, no log.
      return Result.error(
          (e.failure == Resolve.Failure.NOT_FOUND) ? Result.NOT_FOUND : Result.INVALID_ARGUMENT,
          e.getMessage());
    } catch (RuntimeException e) {
      LX.error(e, "[Chromatik-MCP] Tool " + tool.name() + " failed");
      return Result.error(Result.INTERNAL,
          (e.getMessage() == null) ? e.getClass().getSimpleName() : e.getMessage());
    }
  }

  private static McpSchema.CallToolResult success(LxTool tool, Map<String, Object> value, byte[] png) {
    try {
      // The SDK's own mapper, so the text mirror serializes identically to
      // structuredContent (Gson diverges on HTML escaping and null values).
      var builder = McpSchema.CallToolResult.builder()
          .structuredContent(value)
          .addTextContent(McpJsonDefaults.getMapper().writeValueAsString(value));
      if (png != null) {
        builder.addContent(new McpSchema.ImageContent(
            null, Base64.getEncoder().encodeToString(png), "image/png"));
      }
      return builder.build();
    } catch (IOException e) {
      LX.error(e, "[Chromatik-MCP] Tool " + tool.name() + " produced an unserializable payload");
      return McpSchema.CallToolResult.builder()
          .isError(true)
          .addTextContent(Result.INTERNAL + ": failed to serialize tool payload")
          .build();
    }
  }
}
