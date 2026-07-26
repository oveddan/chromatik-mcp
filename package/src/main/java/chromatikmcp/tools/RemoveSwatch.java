package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.color.LXSwatch;

import chromatikmcp.domain.Palettes;
import chromatikmcp.domain.Resolve;

public final class RemoveSwatch implements LxTool {

  @Override
  public String name() {
    return "remove_swatch";
  }

  @Override
  public String description() {
    return "Remove a saved swatch by its canonical path (as returned by save_swatch, or "
        + "listed in get_palette's swatches). The active swatch's current colors are "
        + "unaffected. Remaining swatches reindex afterwards, so held paths can go stale. "
        + "Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the saved swatch to remove, e.g. /lx/palette/swatches/swatch/1"));
    return Schemas.object(properties, List.of("path"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String path = Args.requireString(args, "path");
    LXSwatch swatch = Resolve.component(lx, path, LXSwatch.class);
    Palettes.removeSwatch(lx, swatch);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("removed", path);
    payload.put("kind", "swatch");
    return Result.ok(payload);
  }
}
