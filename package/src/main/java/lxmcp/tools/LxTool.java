package lxmcp.tools;

import java.util.Map;

import heronarts.lx.LX;

/**
 * One MCP tool: a name + JSON-Schema input shape + a handler.
 *
 * <p>{@link #handle} runs on the LX engine thread ({@link Tools} marshals it through
 * {@code EngineExecutor}), so it may touch LX state freely — but must not block on
 * anything that itself needs an engine tick. Per the CLAUDE.md layering, handlers parse
 * args, call a domain primitive, and shape the payload; they construct no LXCommands and
 * never mutate the engine directly.
 */
public interface LxTool {

  String name();

  String description();

  /** JSON Schema for the tool arguments, in the Map shape the MCP SDK accepts. */
  Map<String, Object> inputSchema();

  /** Advertised via the MCP readOnlyHint tool annotation. */
  boolean readOnly();

  /** Runs on the LX engine thread; expected failures return {@code Result.error}. */
  Result<Map<String, Object>> handle(LX lx, Map<String, Object> args);
}
