package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.modulator.MultiStageEnvelope;

import chromatikmcp.domain.Envelopes;
import chromatikmcp.domain.Envelopes.StageInfo;
import chromatikmcp.domain.Resolve;

public final class SetStage implements LxTool {

  @Override
  public String name() {
    return "set_stage";
  }

  @Override
  public String description() {
    return "Edit one existing stage on a MultiStageEnvelope modulator, addressed by "
        + "{path, index} (index from list_stages). Applies any combination of basis (new "
        + "position), value (in [0,1] normalized space — rejected outside that range, "
        + "matching the class's normalized output), and shape (curve exponent of the "
        + "segment arriving at this stage; rejected if negative — Math.pow(relativeBasis, "
        + "shape) grows unbounded, and can hit Infinity, as relativeBasis approaches 0 "
        + "near a segment's start; 0 is valid and produces an instant step; also rejected "
        + "on the fixed initial stage, index 0 — it has no preceding segment, so its "
        + "shape field is never read); at least one is required. On an "
        + "interior stage, basis is rejected unless it "
        + "lands strictly between its neighboring stages' basis values — a stage can "
        + "never reach or cross a neighbor, since landing exactly on one would shadow it "
        + "during interpolation (remove and re-add it to jump past one). On the fixed "
        + "first/last stage (initial:true/last:true), basis never moves — value still "
        + "applies. The payload echoes the stage read back from the engine (resulting "
        + "basis/value/shape), never the request. Stage indices "
        + "are POSITIONAL: they shift whenever a stage is added or removed — re-run "
        + "list_stages rather than reuse an index from an earlier response. "
        + "MultiStageEnvelope has no LXCommand for stage mutation, so this is a direct "
        + "engine edit (marks the project dirty itself, since stages are saved into it). "
        + "Not undoable with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the MultiStageEnvelope modulator"));
    properties.put("index", Schemas.integer(
        "0-based index of the stage in the envelope's stage list (from list_stages)",
        0, Integer.MAX_VALUE));
    properties.put("basis", Schemas.number(
        "New position: on an interior stage, rejected unless strictly between its "
            + "neighboring stages' basis values (landing on a neighbor would shadow it); "
            + "ignored on the fixed first/last stage"));
    properties.put("value", Schemas.number(
        "New envelope output value at this stage, normalized [0,1] (rejected outside "
            + "that range)"));
    properties.put("shape", Schemas.number(
        "New exponent applied to relativeBasis^shape for the segment arriving at this "
            + "stage (1 is linear; rejected if negative — can drive the output to "
            + "Infinity near the segment's start; 0 is a valid instant step; below 1 "
            + "front-loads the approach to this stage's value, above 1 back-loads it; "
            + "rejected on the fixed initial stage, index 0, which has no preceding "
            + "segment)"));
    return Schemas.object(properties, List.of("path", "index"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String path = Args.requireString(args, "path");
    int index = Args.requireInt(args, "index");
    MultiStageEnvelope envelope = Envelopes.resolve(lx, path);
    Double basis = Args.optionalDouble(args, "basis");
    Double value = Args.optionalBoundedNumber(args, "value", 0, 1);
    Double shape = Args.optionalDouble(args, "shape");
    if (basis == null && value == null && shape == null) {
      throw Resolve.invalidArgument(
          "set_stage requires at least one of: basis, value, shape");
    }

    StageInfo updated = Envelopes.setStage(lx, envelope, index, basis, value, shape);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", Resolve.canonicalPath(envelope));
    payload.put("stage", Payloads.stageSummary(updated));
    return Result.ok(payload);
  }
}
