package lxmcp.tools;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import lxmcp.domain.Resolve;
import lxmcp.engine.EngineExecutor;

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
          + "afterwards.";

  private Tools() {}

  public static List<McpServerFeatures.SyncToolSpecification> specifications(
      LX lx, EngineExecutor executor, GetStatus getStatus) {
    return List.of(
            new GetProjectInfo(),
            getStatus,
            new ListChannels(),
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
            new ListModulations(),
            new FireTrigger(),
            new GetComponentDoc(),
            new GetFrame(),
            new GetPalette(),
            new AddChannel(),
            new RemoveChannel(),
            new AddPattern(),
            new RemovePattern(),
            new ActivatePattern(),
            new MovePattern(),
            new AddEffect(),
            new RemoveEffect(),
            new MoveEffect())
        .stream()
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
    Result<Map<String, Object>> result;
    try {
      Map<String, Object> args = (request.arguments() == null) ? Map.of() : request.arguments();
      result = executor.call(() -> tool.handle(lx, args));
    } catch (Resolve.ResolveException e) {
      // Expected failure, not a defect: typed resolver errors map to wire codes, no log.
      result = Result.error(
          (e.failure == Resolve.Failure.NOT_FOUND) ? Result.NOT_FOUND : Result.INVALID_ARGUMENT,
          e.getMessage());
    } catch (RuntimeException e) {
      LX.error(e, "[LX-MCP] Tool " + tool.name() + " failed");
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
          LX.error(e, "[LX-MCP] Tool " + tool.name() + " failed to encode image");
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
      LX.error(e, "[LX-MCP] Tool " + tool.name() + " produced an unserializable payload");
      return McpSchema.CallToolResult.builder()
          .isError(true)
          .addTextContent(Result.INTERNAL + ": failed to serialize tool payload")
          .build();
    }
  }
}
