package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.modulator.LXModulator;

import chromatikmcp.domain.Modulators;
import chromatikmcp.domain.Resolve;

public final class RemoveModulator implements LxTool {

  @Override
  public String name() {
    return "remove_modulator";
  }

  @Override
  public String description() {
    return "Remove a modulator added by add_modulator, by the canonical path returned when it "
        + "was added (or by list_modulations). Any wirings (modulations or triggers) sourced "
        + "from the modulator are removed with it. Remaining modulators in the same engine "
        + "reindex afterwards, so held paths can go stale. Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Schemas.object(
        Map.of("path", Schemas.string(
            "Canonical path of the modulator, as returned by add_modulator")),
        List.of("path"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    if (!(args.get("path") instanceof String path)) {
      return Result.error(Result.INVALID_ARGUMENT, "Required string argument: path");
    }
    // Resolution failures are typed ResolveExceptions; the seam maps them to wire codes.
    LXModulator modulator = Resolve.component(lx, path, LXModulator.class);
    Modulators.removeModulator(lx, modulator);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("removed", path);
    payload.put("kind", "modulator");
    return Result.ok(payload);
  }
}
