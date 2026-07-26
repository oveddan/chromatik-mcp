package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.color.LXDynamicColor;
import heronarts.lx.color.LXSwatch;

import chromatikmcp.domain.Palettes;
import chromatikmcp.domain.Resolve;

public final class AddColor implements LxTool {

  @Override
  public String name() {
    return "add_color";
  }

  @Override
  public String description() {
    return "Add a color slot to a swatch, appended at the end — targets the active swatch "
        + "(get_palette's activeSwatch) by default, or a saved swatch if swatch is given. "
        + "Set the new color's hue/saturation/brightness via set_parameter on the returned "
        + "primaryPath. Undoable in Chromatik with Cmd-Z.";
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
    String swatchArg = Args.optionalString(args, "swatch", "swatch must be a string path");
    LXSwatch swatch = (swatchArg == null)
        ? lx.engine.palette.swatch
        : Resolve.component(lx, swatchArg, LXSwatch.class);
    LXDynamicColor color = Palettes.addColor(lx, swatch);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", color.getCanonicalPath());
    payload.put("swatch", swatch.getCanonicalPath());
    payload.put("primaryPath", color.primary.getCanonicalPath());
    payload.put("secondaryPath", color.secondary.getCanonicalPath());
    return Result.ok(payload);
  }
}
