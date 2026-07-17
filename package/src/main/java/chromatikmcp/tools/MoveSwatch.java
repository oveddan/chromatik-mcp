package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.color.LXSwatch;

import chromatikmcp.domain.Palettes;
import chromatikmcp.domain.Resolve;

public final class MoveSwatch implements LxTool {

  @Override
  public String name() {
    return "move_swatch";
  }

  @Override
  public String description() {
    return "Move a saved swatch to a new 0-based index within get_palette's swatches list. "
        + "Returns invalid_argument if the index is out of range. Other swatches reindex "
        + "around it, so held paths can go stale. Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the saved swatch to move, e.g. /lx/palette/swatches/swatch/1"));
    properties.put("index", Map.of("type", "integer", "description",
        "0-based destination index within the saved swatch list"));
    return Schemas.object(properties, List.of("path", "index"));
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
    if (!(args.get("index") instanceof Number n)) {
      return Result.error(Result.INVALID_ARGUMENT, "Required integer argument: index");
    }
    LXSwatch swatch = Resolve.component(lx, path, LXSwatch.class);
    int index = n.intValue();
    if (index < 0 || index >= lx.engine.palette.swatches.size()) {
      return Result.error(Result.INVALID_ARGUMENT,
          "index out of range: " + index + " (swatch count "
              + lx.engine.palette.swatches.size() + ")");
    }
    Palettes.moveSwatch(lx, swatch, index);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", swatch.getCanonicalPath());
    payload.put("label", swatch.getLabel());
    payload.put("index", swatch.getIndex());
    return Result.ok(payload);
  }
}
