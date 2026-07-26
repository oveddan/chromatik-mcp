package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.snapshot.LXGlobalSnapshot;

import chromatikmcp.domain.Resolve;
import chromatikmcp.domain.Snapshots;

public final class RecallSnapshot implements LxTool {

  @Override
  public String name() {
    return "recall_snapshot";
  }

  @Override
  public String description() {
    return "Recall a snapshot's captured state, restoring the mixer/pattern/effect/"
        + "modulation values it holds. By default the recall follows the snapshot engine's "
        + "own transitionEnabled/transitionTimeSecs settings (see list_snapshots) — when "
        + "transitions are enabled, values fade in over transitionTimeSecs; pass "
        + "immediate=true to force an instant recall for this call regardless of that "
        + "setting. The engine's recallMixer/recallPattern/recallEffect/recallModulation/"
        + "recallMaster/recallOutput booleans (also from list_snapshots) limit which "
        + "categories of state the recall is allowed to touch. It lands on the Chromatik "
        + "undo stack, but Cmd-Z after a recall does not reliably restore plain parameter "
        + "values to their pre-recall state (an LX-side ordering quirk in how it builds "
        + "the undo entry) — recall_snapshot again, or another snapshot, to get back to a "
        + "known state instead of relying on undo here.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the snapshot, as returned by add_snapshot/list_snapshots"));
    properties.put("immediate", Schemas.bool(
        "Force an instant recall, bypassing transitionEnabled/transitionTimeSecs for this "
            + "call; defaults to false (follow the engine's transition setting)"));
    return Schemas.object(properties, List.of("path"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String path = Args.requireString(args, "path");
    Object immediateArg = args.get("immediate");
    if (immediateArg != null && !(immediateArg instanceof Boolean)) {
      return Result.error(Result.INVALID_ARGUMENT, "immediate must be a boolean");
    }
    boolean immediate = Boolean.TRUE.equals(immediateArg);
    LXGlobalSnapshot snapshot = Resolve.component(lx, path, LXGlobalSnapshot.class);
    Snapshots.recall(lx, snapshot, immediate);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("recalled", path);
    payload.put("immediate", immediate);
    return Result.ok(payload);
  }
}
