package lxmcp.domain;

import java.util.ArrayList;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.color.LXDynamicColor;
import heronarts.lx.color.LXPalette;
import heronarts.lx.color.LXSwatch;
import heronarts.lx.command.LXCommand;

/**
 * The global color palette ({@code lx.engine.palette}): read-only snapshot plus swatch /
 * color mutations, all routed through {@code LXCommand} for undo. Call on the engine thread.
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

  /**
   * Save the active swatch's current colors as a new saved swatch, appended to the end of
   * {@code palette.swatches}.
   */
  public static LXSwatch saveSwatch(LX lx) {
    List<LXSwatch> swatches = lx.engine.palette.swatches;
    int before = swatches.size();
    Commands.perform(lx, new LXCommand.Palette.SaveSwatch());
    if (swatches.size() != before + 1) {
      throw new IllegalStateException("SaveSwatch did not add a swatch");
    }
    return swatches.get(before);
  }

  /**
   * Apply a saved swatch's colors onto the active swatch — undoably, unlike firing the
   * swatch's {@code recall} trigger directly (same underlying {@code LXPalette.setSwatch},
   * but only the command wraps it in undo history). Both honor the palette's transition
   * settings identically: if {@code transitionEnabled} is on, colors interpolate over
   * {@code transitionTimeSecs} rather than snapping immediately.
   */
  public static void setSwatch(LX lx, LXSwatch swatch) {
    Commands.perform(lx, new LXCommand.Palette.SetSwatch(swatch));
  }

  /** Remove a saved swatch. Remaining swatches reindex, so held paths can go stale. */
  public static void removeSwatch(LX lx, LXSwatch swatch) {
    Commands.perform(lx, new LXCommand.Palette.RemoveSwatch(swatch));
    if (lx.engine.palette.swatches.contains(swatch)) {
      throw new IllegalStateException("RemoveSwatch did not remove " + swatch.getCanonicalPath());
    }
  }

  /** Move a saved swatch to a new 0-based index in {@code palette.swatches}. */
  public static void moveSwatch(LX lx, LXSwatch swatch, int index) {
    Commands.perform(lx, new LXCommand.Palette.MoveSwatch(swatch, index));
    if (swatch.getIndex() != index) {
      throw new IllegalStateException(
          "MoveSwatch did not move " + swatch.getCanonicalPath() + " to index " + index);
    }
  }

  /** Add a color slot to {@code swatch}, appended at the end. */
  public static LXDynamicColor addColor(LX lx, LXSwatch swatch) {
    List<LXDynamicColor> colors = swatch.colors;
    int before = colors.size();
    Commands.perform(lx, new LXCommand.Palette.AddColor(swatch));
    if (colors.size() != before + 1) {
      throw new IllegalStateException("AddColor did not add a color to " + swatch.getCanonicalPath());
    }
    return colors.get(before);
  }

  /**
   * Remove the last color slot from {@code swatch} (mirrors {@code LXSwatch.removeColor()}).
   * The first color of a swatch can never be removed — LX requires at least one — so a
   * single-color swatch is rejected up front rather than performing a command doomed to fail.
   *
   * @return the canonical path the removed color used to have
   */
  public static String removeColor(LX lx, LXSwatch swatch) {
    List<LXDynamicColor> colors = swatch.colors;
    if (colors.size() <= 1) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Cannot remove the last remaining color from swatch " + swatch.getCanonicalPath());
    }
    LXDynamicColor color = colors.get(colors.size() - 1);
    String path = color.getCanonicalPath();
    int before = colors.size();
    Commands.perform(lx, new LXCommand.Palette.RemoveColor(color));
    if (colors.size() != before - 1) {
      throw new IllegalStateException("RemoveColor did not remove " + path);
    }
    return path;
  }
}
