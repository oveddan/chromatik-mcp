package lxmcp.tools;

import java.io.IOException;
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

  private Tools() {}

  public static List<McpServerFeatures.SyncToolSpecification> specifications(LX lx, EngineExecutor executor) {
    return List.of(
            new GetProjectInfo(),
            new ListChannels(),
            new ListAvailable(ListAvailable.Kind.PATTERNS),
            new ListAvailable(ListAvailable.Kind.EFFECTS),
            new ListAvailable(ListAvailable.Kind.MODULATORS),
            new GetParameter(),
            new AddMacroKnob())
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
      case Result.Ok<Map<String, Object>> ok -> {
        try {
          // The SDK's own mapper, so the text mirror serializes identically to
          // structuredContent (Gson diverges on HTML escaping and null values).
          yield McpSchema.CallToolResult.builder()
              .structuredContent(ok.value())
              .addTextContent(McpJsonDefaults.getMapper().writeValueAsString(ok.value()))
              .build();
        } catch (IOException e) {
          LX.error(e, "[LX-MCP] Tool " + tool.name() + " produced an unserializable payload");
          yield McpSchema.CallToolResult.builder()
              .isError(true)
              .addTextContent(Result.INTERNAL + ": failed to serialize tool payload")
              .build();
        }
      }
      case Result.Error<Map<String, Object>> error -> McpSchema.CallToolResult.builder()
          .isError(true)
          .addTextContent(error.code() + ": " + error.message())
          .build();
    };
  }
}
