package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.color.LXSwatch;

import chromatikmcp.domain.Palettes;

public final class SaveSwatch implements LxTool {

  @Override
  public String name() {
    return "save_swatch";
  }

  @Override
  public String description() {
    return "Capture the active swatch's current colors as a new saved swatch, appended to "
        + "the end of get_palette's swatches list. Returns the new swatch's canonical path — "
        + "pass it to set_swatch to recall it later, or move_swatch/remove_swatch to manage "
        + "it. Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Schemas.noArgs();
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    LXSwatch swatch = Palettes.saveSwatch(lx);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", swatch.getCanonicalPath());
    payload.put("label", swatch.getLabel());
    payload.put("recallPath", swatch.recall.getCanonicalPath());
    return Result.ok(payload);
  }
}
