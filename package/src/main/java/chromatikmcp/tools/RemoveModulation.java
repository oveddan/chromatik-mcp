package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.modulation.LXParameterModulation;

import chromatikmcp.domain.Modulators;
import chromatikmcp.domain.Resolve;

public final class RemoveModulation implements LxTool {

  @Override
  public String name() {
    return "remove_modulation";
  }

  @Override
  public String description() {
    return "Remove a modulation (continuous or trigger) by the canonical path returned "
        + "when it was wired (e.g. /lx/modulation/modulation/1). Remaining modulations in "
        + "the same engine reindex afterwards, so held paths can go stale. Undoable in "
        + "Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the modulation, as returned by wire_modulator/wire_trigger"));
    return Schemas.object(properties, List.of("path"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String path = Args.requireString(args, "path");
    LXParameterModulation modulation =
        Resolve.component(lx, path, LXParameterModulation.class);
    String kind = Modulators.remove(lx, modulation);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("removed", path);
    payload.put("kind", kind);
    return Result.ok(payload);
  }
}
