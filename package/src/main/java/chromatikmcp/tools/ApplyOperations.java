package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

/**
 * Applies several mutation-tool calls in one MCP round-trip. Every handler already runs on
 * the LX engine thread via a single {@code EngineExecutor.call} hop ({@link Tools#call}), so
 * batching operations here lands them all in the same engine frame — no intermediate
 * half-built state is ever rendered or output between operations, unlike calling the same
 * tools one MCP request at a time.
 *
 * <p>Deliberately descoped: this does <strong>not</strong> collapse the batch into one undo
 * step. LX has no compound-command primitive ({@code LXCommandEngine} holds flat {@code
 * Stack<LXCommand>} fields) — building one would mean splitting every domain primitive's
 * "build the command" from "perform it", which is out of scope here. Each operation still
 * performs (and undoes) independently, exactly as if called one at a time.
 *
 * <p>Composes existing tool handlers rather than reimplementing any mutation (CLAUDE.md
 * layering) — constructed with the same {@link LxTool} instances {@link Tools#allTools}
 * already built, filtered to mutations only, so this tool structurally cannot see itself or
 * any read-only tool.
 */
public final class ApplyOperations implements LxTool {

  static final int MAX_OPERATIONS = 50;

  private final Map<String, LxTool> mutationTools;

  public ApplyOperations(Map<String, LxTool> mutationTools) {
    this.mutationTools = mutationTools;
  }

  @Override
  public String name() {
    return "apply_operations";
  }

  @Override
  public String description() {
    return "Apply up to " + MAX_OPERATIONS + " mutation-tool calls in one MCP round-trip. "
        + "Every handler already runs on the LX engine thread, so a batch schedules onto it "
        + "once and every operation lands in the same engine frame — no intermediate "
        + "half-built state is ever rendered or output between operations, unlike issuing "
        + "the same calls one at a time. Each entry is {tool, args}: 'tool' names any "
        + "registered mutation tool (by its normal tool name) and 'args' is exactly the "
        + "argument object a top-level call to that tool would take. Every operations[i].tool "
        + "is validated up front — an unknown name, a read-only tool, or apply_operations "
        + "itself (batches cannot nest) fails the whole call with invalid_argument and "
        + "applies nothing. Once validated, execution is continue-on-error: an operation that "
        + "fails does not stop the ones after it. The response's results array has one entry "
        + "per operation, in order: {index, ok: true, result} on success or {index, ok: "
        + "false, code, message} on failure, using the same error codes a top-level call "
        + "would return. Two sharp edges this tool does NOT smooth over: (1) it does not "
        + "collapse the batch into one undo step — each operation still produces its own undo "
        + "entry (or entries) exactly as if called individually, so undoing an N-operation "
        + "batch takes N presses of Cmd-Z; (2) LX's lx.command.perform() wipes the entire "
        + "undo/redo history when a command fails — in a batch, one failing operation can "
        + "silently erase undo history for every earlier operation in the SAME batch, even "
        + "though those operations still report ok: true; (3) all operations run inside one "
        + "engine frame, so an I/O-heavy operation stalls the whole batch's cost onto that "
        + "single frame — save_project (full project serialization plus a disk write, plus a "
        + ".lxm write-through when syncModelFile is on) is now the worst case, ahead of "
        + "reload_fixtures (which re-reads every .lxf from disk); a large or slow batch can "
        + "hit the 30s executor timeout, after which the batch is NOT cancelled and still "
        + "applies once the engine drains it.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> operationProperties = new LinkedHashMap<>();
    operationProperties.put("tool", Schemas.string(
        "Name of a registered mutation tool to invoke (read-only tools and apply_operations "
            + "itself are rejected)"));
    Map<String, Object> argsSchema = new LinkedHashMap<>();
    argsSchema.put("type", "object");
    argsSchema.put("description",
        "Arguments for the tool, exactly as passed to a top-level call of it");
    operationProperties.put("args", argsSchema);
    Map<String, Object> operationSchema =
        Schemas.object(operationProperties, List.of("tool", "args"));

    Map<String, Object> operationsSchema = new LinkedHashMap<>();
    operationsSchema.put("type", "array");
    operationsSchema.put("description", "Operations to apply, in order");
    operationsSchema.put("items", operationSchema);
    operationsSchema.put("minItems", 1);
    operationsSchema.put("maxItems", MAX_OPERATIONS);

    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("operations", operationsSchema);
    return Schemas.object(properties, List.of("operations"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    if (!(args.get("operations") instanceof List<?> rawOperations)) {
      return Result.error(Result.INVALID_ARGUMENT, "Required array argument: operations");
    }
    if (rawOperations.isEmpty()) {
      return Result.error(Result.INVALID_ARGUMENT, "operations must not be empty");
    }
    if (rawOperations.size() > MAX_OPERATIONS) {
      return Result.error(Result.INVALID_ARGUMENT,
          "operations has " + rawOperations.size() + " entries, exceeding the cap of "
              + MAX_OPERATIONS);
    }

    // Pre-validate every operation's tool name before applying any of them (mirrors
    // SetFixtureParams's resolve-all-then-write discipline) — a batch with one bad entry
    // must not partially apply. Collect every invalid entry rather than stopping at the
    // first, so an agent fixing one bad tool name doesn't retry into the next.
    List<Operation> operations = new ArrayList<>(rawOperations.size());
    List<String> validationErrors = new ArrayList<>();
    for (int i = 0; i < rawOperations.size(); i++) {
      Object entry = rawOperations.get(i);
      if (!(entry instanceof Map<?, ?> entryMap)) {
        validationErrors.add("operations[" + i + "] must be an object");
        continue;
      }
      if (!(entryMap.get("tool") instanceof String toolName)) {
        validationErrors.add("operations[" + i + "].tool must be a string");
        continue;
      }
      LxTool subTool = this.mutationTools.get(toolName);
      if (subTool == null) {
        validationErrors.add(
            "operations[" + i + "].tool '" + toolName + "' is not a known mutation tool "
                + "(unknown, read-only, or apply_operations itself)");
        continue;
      }
      Object argsValue = entryMap.get("args");
      Map<String, Object> subArgs;
      if (argsValue == null) {
        subArgs = Map.of();
      } else if (argsValue instanceof Map<?, ?> argsMap) {
        subArgs = toStringKeyedMap(argsMap);
      } else {
        validationErrors.add("operations[" + i + "].args must be an object");
        continue;
      }
      operations.add(new Operation(subTool, subArgs));
    }
    if (!validationErrors.isEmpty()) {
      return Result.error(Result.INVALID_ARGUMENT, String.join("; ", validationErrors));
    }

    List<Map<String, Object>> results = new ArrayList<>(operations.size());
    for (int i = 0; i < operations.size(); i++) {
      Operation operation = operations.get(i);
      // Not EngineExecutor.call: handle() already runs on the engine thread (Tools.call
      // marshalled it there), and call() blocking-awaits a drain that can't happen from
      // inside the task it would be waiting on. Tools.invoke runs the sub-tool synchronously,
      // reusing the exact exception-to-Result mapping a top-level call gets.
      Result<Map<String, Object>> result = Tools.invoke(operation.tool(), lx, operation.args());
      results.add(resultEntry(i, result));
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("results", results);
    return Result.ok(payload);
  }

  private static Map<String, Object> resultEntry(int index, Result<Map<String, Object>> result) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("index", index);
    switch (result) {
      case Result.Ok<Map<String, Object>> ok -> {
        entry.put("ok", true);
        entry.put("result", ok.value());
      }
      case Result.OkImage<Map<String, Object>> ok -> {
        // No batched sub-tool returns an image today (the one image-bearing tool, get_frame,
        // is read-only and so excluded from mutationTools) — carry the JSON payload and drop
        // the PNG rather than encode it here, since Result.OkImage's contract is to encode on
        // the HTTP worker thread, not the engine thread this handler is running on.
        entry.put("ok", true);
        entry.put("result", ok.value());
      }
      case Result.Error<Map<String, Object>> error -> {
        entry.put("ok", false);
        entry.put("code", error.code());
        entry.put("message", error.message());
      }
    }
    return entry;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> toStringKeyedMap(Map<?, ?> raw) {
    return (Map<String, Object>) raw;
  }

  private record Operation(LxTool tool, Map<String, Object> args) {}
}
