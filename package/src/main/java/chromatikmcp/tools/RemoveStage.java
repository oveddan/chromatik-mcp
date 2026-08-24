package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.modulator.MultiStageEnvelope;
import heronarts.lx.modulator.MultiStageEnvelope.Stage;

import chromatikmcp.domain.Envelopes;
import chromatikmcp.domain.Envelopes.StageInfo;
import chromatikmcp.domain.Resolve;

public final class RemoveStage implements LxTool {

  @Override
  public String name() {
    return "remove_stage";
  }

  @Override
  public String description() {
    return "Remove an interior stage from a MultiStageEnvelope modulator, addressed by "
        + "{path, index} (index from list_stages). Only interior stages may be removed — "
        + "the fixed first/last stage (basis 0/1, initial:true/last:true) is rejected with "
        + "invalid_argument before anything changes. Returns the removed stage (same shape "
        + "as list_stages) plus the envelope's resulting stages read back from the engine. "
        + "Stage indices are POSITIONAL: every later stage shifts down after a removal — "
        + "use the returned stages array or re-run list_stages rather than reuse an index "
        + "from an earlier response. MultiStageEnvelope has no LXCommand for stage "
        + "mutation, so this is a direct engine edit (marks the project dirty itself, "
        + "since stages are saved into it). Not undoable with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the MultiStageEnvelope modulator"));
    properties.put("index", Schemas.integer(
        "0-based index of the stage in the envelope's stage list (from list_stages)",
        0, Integer.MAX_VALUE));
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

    // Identity of what's about to go away — unreadable after the removal commits.
    Stage stage = Envelopes.stageAt(envelope, index);
    StageInfo removed = Envelopes.summary(envelope, stage);

    Envelopes.removeStage(lx, envelope, index);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", Resolve.canonicalPath(envelope));
    payload.put("removed", Payloads.stageSummary(removed));
    payload.put("stageCount", envelope.stages.size());
    payload.put("stages", Payloads.stageSummaries(Envelopes.list(envelope)));
    return Result.ok(payload);
  }
}
