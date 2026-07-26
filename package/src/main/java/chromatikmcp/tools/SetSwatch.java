package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.color.LXSwatch;

import chromatikmcp.domain.Palettes;
import chromatikmcp.domain.Resolve;

public final class SetSwatch implements LxTool {

  @Override
  public String name() {
    return "set_swatch";
  }

  @Override
  public String description() {
    return "Apply a saved swatch's colors onto the active swatch, by its canonical path (as "
        + "returned by save_swatch or listed in get_palette's swatches). Same effective "
        + "change as firing the swatch's recallPath with fire_trigger — including "
        + "transitionEnabled/transitionTimeSecs interpolation — but undoable in Chromatik "
        + "with Cmd-Z, unlike the trigger.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the saved swatch to apply, e.g. /lx/palette/swatches/swatch/1"));
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
    Palettes.setSwatch(lx, swatch);
    return Result.ok(Map.of(
        "applied", path,
        "activeSwatch", lx.engine.palette.swatch.getCanonicalPath()));
  }
}
