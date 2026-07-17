package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Snapshots;

public final class ListSnapshots implements LxTool {

  @Override
  public String name() {
    return "list_snapshots";
  }

  @Override
  public String description() {
    return "List saved snapshots — whole-look captures of mixer/pattern/effect/modulation "
        + "state, in recall order — plus the snapshot engine's settings. The settings include "
        + "the recallMixer/recallPattern/recallEffect/recallModulation/recallMaster/"
        + "recallOutput booleans, which scope what a recall_snapshot call is allowed to "
        + "touch, and transitionEnabled/transitionTimeSecs, which govern whether and how "
        + "long a recall fades between the current state and the snapshot's saved state. "
        + "Adjust any of them via set_parameter on the returned paths.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Schemas.noArgs();
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    Snapshots.EngineInfo info = Snapshots.list(lx);
    List<Map<String, Object>> snapshots = new ArrayList<>();
    for (Snapshots.SnapshotInfo s : info.snapshots()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("path", s.path());
      entry.put("id", s.id());
      entry.put("label", s.label());
      entry.put("transitionTimeSecs", s.transitionTimeSecs());
      entry.put("hasCustomTransitionTime", s.hasCustomTransitionTime());
      snapshots.add(entry);
    }
    List<Map<String, Object>> settings = new ArrayList<>();
    for (var parameter : info.settings()) {
      settings.add(parameter.toMap());
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("snapshots", snapshots);
    payload.put("settings", settings);
    return Result.ok(payload);
  }
}
