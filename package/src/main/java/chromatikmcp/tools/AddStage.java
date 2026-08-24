package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.modulator.MultiStageEnvelope;

import chromatikmcp.domain.Envelopes;
import chromatikmcp.domain.Envelopes.StageInfo;
import chromatikmcp.domain.Resolve;

public final class AddStage implements LxTool {

  @Override
  public String name() {
    return "add_stage";
  }

  @Override
  public String description() {
    return "Insert an interior stage on a MultiStageEnvelope modulator. basis (rejected "
        + "unless strictly between 0 and 1, and unless it differs from every existing "
        + "stage's basis — landing on the fixed first/last stage's position, or on "
        + "another interior stage's, would shadow it during interpolation instead of "
        + "creating a distinct point) and value (rejected outside [0,1], the class's "
        + "normalized output range) place the "
        + "new point; the stage is inserted in basis order — its resulting index depends "
        + "on where it lands among the existing stages, read it back from the response "
        + "rather than assuming it was appended. shape (default 1, linear; rejected if "
        + "negative — Math.pow(relativeBasis, shape) grows unbounded, and can hit "
        + "Infinity, as relativeBasis approaches 0 near a segment's start; 0 is valid and "
        + "produces an instant step to this stage's value) is the exponent applied to the "
        + "segment's relative basis (value = lerp(prevValue, value, relativeBasis^shape)) "
        + "— it does not map to convex/concave in a fixed way, since that also depends on "
        + "whether the segment rises or falls; a shape below 1 front-loads the value's "
        + "approach to this stage's value, above 1 back-loads it. Returns the created "
        + "stage plus the envelope's resulting stageCount. Stage indices are POSITIONAL: "
        + "every later stage shifts when one is added or removed — re-run list_stages "
        + "rather than reuse an index from an earlier response. MultiStageEnvelope has no "
        + "LXCommand for stage mutation, so this is a direct engine edit (marks the "
        + "project dirty itself, since stages are saved into it). Not undoable with "
        + "Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the MultiStageEnvelope modulator"));
    properties.put("basis", Schemas.number(
        "Position along the envelope, strictly between 0 and 1 exclusive, and distinct "
            + "from every existing stage's basis (rejected otherwise — a coinciding "
            + "basis would shadow that stage)"));
    properties.put("value", Schemas.number(
        "Envelope output value at this stage, normalized [0,1] (rejected outside that "
            + "range)"));
    properties.put("shape", Schemas.number(
        "Exponent applied to relativeBasis^shape for the segment arriving at this stage "
            + "(default 1, linear; rejected if negative — can drive the output to "
            + "Infinity near the segment's start; 0 is a valid instant step; below 1 "
            + "front-loads the approach to this stage's value, above 1 back-loads it)"));
    return Schemas.object(properties, List.of("path", "basis", "value"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String path = Args.requireString(args, "path");
    MultiStageEnvelope envelope = Envelopes.resolve(lx, path);
    double basis = Args.requireDouble(args, "basis");
    double value = Args.requireBoundedDouble(args, "value", 0, 1);
    Double shape = Args.optionalDouble(args, "shape");

    StageInfo added = Envelopes.addStage(lx, envelope, basis, value, shape);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", Resolve.canonicalPath(envelope));
    payload.put("stage", Payloads.stageSummary(added));
    payload.put("stageCount", envelope.stages.size());
    return Result.ok(payload);
  }
}
