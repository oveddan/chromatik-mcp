package lxmcp.tools;

import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.color.LXSwatch;

import lxmcp.domain.Palettes;
import lxmcp.domain.Resolve;

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
    return Schemas.object(
        Map.of("path", Schemas.string(
            "Canonical path of the saved swatch to apply, e.g. /lx/palette/swatches/swatch/1")),
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
    LXSwatch swatch = Resolve.component(lx, path, LXSwatch.class);
    Palettes.setSwatch(lx, swatch);
    return Result.ok(Map.of(
        "applied", path,
        "activeSwatch", lx.engine.palette.swatch.getCanonicalPath()));
  }
}
