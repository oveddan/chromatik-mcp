package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.color.LXSwatch;

import chromatikmcp.domain.Palettes;
import chromatikmcp.domain.Resolve;

public final class RemoveColor implements LxTool {

  @Override
  public String name() {
    return "remove_color";
  }

  @Override
  public String description() {
    return "Remove the last color slot from a swatch — targets the active swatch "
        + "(get_palette's activeSwatch) by default, or a saved swatch if swatch is given. "
        + "A swatch's first color can never be removed (LX requires at least one); this "
        + "returns invalid_argument on a single-color swatch. Remaining colors reindex, so "
        + "held paths can go stale. Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("swatch", Schemas.string(
        "Optional canonical path of a saved swatch (as returned by save_swatch); "
                + "defaults to the active swatch"));
    return Schemas.object(properties, List.of());
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    Object swatchArg = args.get("swatch");
    if (swatchArg != null && !(swatchArg instanceof String)) {
      return Result.error(Result.INVALID_ARGUMENT, "swatch must be a string path");
    }
    LXSwatch swatch = (swatchArg == null)
        ? lx.engine.palette.swatch
        : Resolve.component(lx, (String) swatchArg, LXSwatch.class);
    String removed = Palettes.removeColor(lx, swatch);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("removed", removed);
    payload.put("swatch", swatch.getCanonicalPath());
    payload.put("kind", "color");
    return Result.ok(payload);
  }
}
