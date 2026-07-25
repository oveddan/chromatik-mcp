package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Palettes;

public final class GetPalette implements LxTool {

  @Override
  public String name() {
    return "get_palette";
  }

  @Override
  public String description() {
    return "The global color palette: the active swatch's colors (with current effective "
        + "color, mode, and the primary/secondary component paths — set hue/saturation/"
        + "brightness via e.g. <primaryPath>/hue), the saved swatches with their labels and "
        + "recall trigger paths, and transition/auto-cycle settings. Recall a saved swatch "
        + "with fire_trigger on its recallPath; with transitions enabled the change "
        + "interpolates over transitionTimeSecs (poll get_palette and watch transition.transitionProgress return to 0 to see it land). Recall is "
        + "NOT undoable. Palette-linked patterns and effects (color mode 'Palette') follow "
        + "these colors automatically.";
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
    Palettes.PaletteInfo info = Palettes.info(lx);

    Map<String, Object> activeSwatch = new LinkedHashMap<>();
    // path is null for an object that isn't path-registered (Resolve.canonicalPathOrNull) —
    // omit the key rather than emit a bogus "/null" or a literal JSON null.
    if (info.activeSwatch().path() != null) {
      activeSwatch.put("path", info.activeSwatch().path());
    }
    List<Map<String, Object>> colors = new ArrayList<>();
    for (Palettes.ColorInfo color : info.activeSwatch().colors()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      if (color.path() != null) {
        entry.put("path", color.path());
      }
      entry.put("mode", color.mode());
      entry.put("effectiveColor", color.effectiveColor());
      if (color.primaryPath() != null) {
        entry.put("primaryPath", color.primaryPath());
      }
      if (color.secondaryPath() != null) {
        entry.put("secondaryPath", color.secondaryPath());
      }
      colors.add(entry);
    }
    activeSwatch.put("colors", colors);

    List<Map<String, Object>> swatches = new ArrayList<>();
    for (Palettes.SwatchInfo swatch : info.swatches()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      if (swatch.path() != null) {
        entry.put("path", swatch.path());
      }
      entry.put("label", swatch.label());
      if (swatch.recallPath() != null) {
        entry.put("recallPath", swatch.recallPath());
      }
      entry.put("autoCycleEligible", swatch.autoCycleEligible());
      swatches.add(entry);
    }

    Map<String, Object> transition = new LinkedHashMap<>();
    transition.put("enabled", info.transition().enabled());
    if (info.transition().enabledPath() != null) {
      transition.put("enabledPath", info.transition().enabledPath());
    }
    transition.put("timeSecs", info.transition().timeSecs());
    if (info.transition().timeSecsPath() != null) {
      transition.put("timeSecsPath", info.transition().timeSecsPath());
    }
    transition.put("transitionProgress", info.transition().transitionProgress());

    Map<String, Object> autoCycle = new LinkedHashMap<>();
    autoCycle.put("enabled", info.autoCycle().enabled());
    autoCycle.put("timeSecs", info.autoCycle().timeSecs());
    autoCycle.put("cursor", info.autoCycle().cursor());

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("activeSwatch", activeSwatch);
    payload.put("swatches", swatches);
    payload.put("transition", transition);
    payload.put("autoCycle", autoCycle);
    return Result.ok(payload);
  }
}
