package lxmcp.domain;

import java.util.ArrayList;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.color.LXDynamicColor;
import heronarts.lx.color.LXPalette;
import heronarts.lx.color.LXSwatch;

/**
 * Read-only snapshot of the global color palette: the active swatch's effective colors,
 * the saved swatches available for recall, and the transition / auto-cycle settings.
 */
public final class Palettes {

  public record ColorInfo(String path, String mode, String effectiveColor,
      String primaryPath, String secondaryPath) {}

  public record ActiveSwatchInfo(String path, List<ColorInfo> colors) {}

  public record SwatchInfo(String path, String label, String recallPath, boolean autoCycleEligible) {}

  public record TransitionInfo(boolean enabled, String enabledPath, double timeSecs, String timeSecsPath, double transitionProgress) {}

  public record AutoCycleInfo(boolean enabled, double timeSecs, int cursor) {}

  public record PaletteInfo(ActiveSwatchInfo activeSwatch, List<SwatchInfo> swatches,
      TransitionInfo transition, AutoCycleInfo autoCycle) {}

  private Palettes() {}

  /** Call on the engine thread; the returned record is safe to read anywhere. */
  public static PaletteInfo info(LX lx) {
    LXPalette palette = lx.engine.palette;

    List<SwatchInfo> swatches = new ArrayList<>();
    for (LXSwatch swatch : palette.swatches) {
      swatches.add(new SwatchInfo(
          swatch.getCanonicalPath(),
          swatch.getLabel(),
          swatch.recall.getCanonicalPath(),
          swatch.autoCycleEligible.isOn()));
    }

    return new PaletteInfo(
        new ActiveSwatchInfo(palette.swatch.getCanonicalPath(), colors(palette.swatch)),
        swatches,
        new TransitionInfo(
            palette.transitionEnabled.isOn(),
            palette.transitionEnabled.getCanonicalPath(),
            palette.transitionTimeSecs.getValue(),
            palette.transitionTimeSecs.getCanonicalPath(),
            palette.getTransitionProgress()),
        new AutoCycleInfo(
            palette.autoCycleEnabled.isOn(),
            palette.autoCycleTimeSecs.getValue(),
            palette.autoCycleCursor.getValuei()));
  }

  private static List<ColorInfo> colors(LXSwatch swatch) {
    List<ColorInfo> colors = new ArrayList<>();
    for (LXDynamicColor color : swatch.colors) {
      colors.add(new ColorInfo(
          color.getCanonicalPath(),
          color.mode.getEnum().label,
          String.format("0x%08x", color.getColor()),
          color.primary.getCanonicalPath(),
          color.secondary.getCanonicalPath()));
    }
    return colors;
  }
}
