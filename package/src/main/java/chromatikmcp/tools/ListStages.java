package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.modulator.MultiStageEnvelope;

import chromatikmcp.domain.Envelopes;
import chromatikmcp.domain.Resolve;

public final class ListStages implements LxTool {

  @Override
  public String name() {
    return "list_stages";
  }

  @Override
  public String description() {
    return "Every stage on a MultiStageEnvelope modulator (basis/value/shape point), in "
        + "basis order: 0-based index, basis, value, per-segment shape (exponent applied "
        + "to the segment arriving at this stage; 1 is linear), initial/last (true for the "
        + "fixed first/last stage, at basis 0/1 — never removable, basis never moves). "
        + "Stages are NOT LXComponents and have no canonical path — they don't appear in "
        + "list_parameters and are addressed positionally as {path, index} to "
        + "add_stage/remove_stage/set_stage. Indices are POSITIONAL: they shift whenever a "
        + "stage is added or removed — re-list rather than reuse an index from an earlier "
        + "response.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the MultiStageEnvelope modulator"));
    return Schemas.object(properties, List.of("path"));
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String path = Args.requireString(args, "path");
    MultiStageEnvelope envelope = Envelopes.resolve(lx, path);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", Resolve.canonicalPath(envelope));
    payload.put("stageCount", envelope.stages.size());
    payload.put("stages", Payloads.stageSummaries(Envelopes.list(envelope)));
    return Result.ok(payload);
  }
}
