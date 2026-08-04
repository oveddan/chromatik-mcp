package chromatikmcp.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import heronarts.lx.LX;
import heronarts.lx.LXEngine;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;

/**
 * Read-only frame readback: captures the last completed engine frame as a detached
 * snapshot (colors + normalized point geometry), and reduces it to a compact summary.
 * The snapshot holds no live LX references, so it is safe to hand to another thread —
 * that is what lets PNG encoding happen off the engine thread.
 */
public final class Frames {

  private Frames() {}

  /** Which composited bus buffer to read. Per-channel buffers are not exposed in v1. */
  public enum Bus {
    MAIN, CUE, AUX;

    int[] buffer(LXEngine.Frame frame) {
      return switch (this) {
        case MAIN -> frame.getMain();
        case CUE -> frame.getCue();
        case AUX -> frame.getAux();
      };
    }
  }

  /**
   * Orthographic view plane. {@code u} runs left→right, {@code v} runs top→bottom in
   * image space (both 0–1), so a raster can consume them directly.
   */
  public enum View {
    FRONT {
      @Override
      public float u(FrameSnapshot s, int i) {
        return s.xn()[i];
      }

      @Override
      public float v(FrameSnapshot s, int i) {
        return 1f - s.yn()[i];
      }

      @Override
      public float uRange(FrameSnapshot s) {
        return s.xRange();
      }

      @Override
      public float vRange(FrameSnapshot s) {
        return s.yRange();
      }
    },
    TOP {
      @Override
      public float u(FrameSnapshot s, int i) {
        return s.xn()[i];
      }

      @Override
      public float v(FrameSnapshot s, int i) {
        return s.zn()[i];
      }

      @Override
      public float uRange(FrameSnapshot s) {
        return s.xRange();
      }

      @Override
      public float vRange(FrameSnapshot s) {
        return s.zRange();
      }
    },
    SIDE {
      @Override
      public float u(FrameSnapshot s, int i) {
        return s.zn()[i];
      }

      @Override
      public float v(FrameSnapshot s, int i) {
        return 1f - s.yn()[i];
      }

      @Override
      public float uRange(FrameSnapshot s) {
        return s.zRange();
      }

      @Override
      public float vRange(FrameSnapshot s) {
        return s.yRange();
      }
    };

    public abstract float u(FrameSnapshot s, int i);

    public abstract float v(FrameSnapshot s, int i);

    public abstract float uRange(FrameSnapshot s);

    public abstract float vRange(FrameSnapshot s);
  }

  /** Fully detached from LX: arrays are copies, indexed by {@link LXPoint#index}. */
  public record FrameSnapshot(
      int[] colors,
      float[] xn,
      float[] yn,
      float[] zn,
      int size,
      float xRange,
      float yRange,
      float zRange,
      String bus) {}

  /** One hue-bin aggregate in a {@link FrameSummary}. */
  public record DominantColor(String hex, double fraction) {}

  /** Stable, wire-agnostic result of reducing a captured frame. */
  public record FrameSummary(
      int points,
      double nonBlackFraction,
      double litFraction,
      double meanBrightness,
      List<DominantColor> dominantColors,
      List<List<String>> grid) {

    public FrameSummary {
      dominantColors = List.copyOf(dominantColors);
      List<List<String>> gridCopy = new ArrayList<>(grid.size());
      for (List<String> row : grid) {
        // Empty grid cells are null, so List.copyOf(row) is intentionally not usable.
        gridCopy.add(Collections.unmodifiableList(new ArrayList<>(row)));
      }
      grid = List.copyOf(gridCopy);
    }
  }

  public static FrameSnapshot capture(LX lx, Bus bus) {
    LXEngine.Frame frame = new LXEngine.Frame(lx);
    lx.engine.copyFrameThreadSafe(frame);
    // Geometry from the frame's own model, not lx.getModel(): copyFrom carries the model
    // with the buffers, so colors index-match geometry even across a model swap.
    LXModel model = frame.getModel();
    int size = model.size;
    int[] colors = Arrays.copyOf(bus.buffer(frame), size);
    float[] xn = new float[size];
    float[] yn = new float[size];
    float[] zn = new float[size];
    for (LXPoint p : model.points) {
      // Buffers are indexed by point.index; a model that skipped reindexPoints() (LX only
      // reindexes via LXStructure.setStaticModel, not the immutable-model constructor)
      // would mis-attribute colors — fail loudly instead.
      if (p.index >= size) {
        throw new IllegalStateException(
            "Model points are not reindexed (point index " + p.index + " >= size " + size + ")");
      }
      xn[p.index] = p.xn;
      yn[p.index] = p.yn;
      zn[p.index] = p.zn;
    }
    return new FrameSnapshot(colors, xn, yn, zn, size,
        model.xRange, model.yRange, model.zRange, bus.name().toLowerCase(Locale.ROOT));
  }

  private static final int HUE_BINS = 12;

  // "Lit" is a heuristic cutoff, not perceptual luminance: the default 26/255 ~= 10% of
  // full scale, comfortably above near-black blur/residual tails (e.g. #101010, max=16)
  // that inflate nonBlackFraction without reading as lit to a human judging the frame.
  public static final int LIT_THRESHOLD = 26;

  /**
   * Compact stats over the snapshot: non-black fraction, lit fraction (see {@link
   * #LIT_THRESHOLD}), mean brightness, top dominant colors from a hue histogram, and a
   * {@code gridSize}×{@code gridSize} matrix of mean cell colors over the view plane
   * (rows top→bottom; cells with no points are null). {@code litThreshold} is the max-
   * channel value (0-255) a pixel must exceed to count toward litFraction. At 0 litFraction
   * equals nonBlackFraction (max > 0 is the nonBlack condition); at 255 litFraction is
   * always 0.0, since no channel can exceed the maximum.
   */
  public static FrameSummary summarize(
      FrameSnapshot s, View view, int gridSize, int litThreshold) {
    int size = s.size();
    int nonBlack = 0;
    int lit = 0;
    double brightnessSum = 0;

    long[] binCount = new long[HUE_BINS];
    long[] binR = new long[HUE_BINS];
    long[] binG = new long[HUE_BINS];
    long[] binB = new long[HUE_BINS];

    int cells = gridSize * gridSize;
    long[] cellCount = new long[cells];
    long[] cellR = new long[cells];
    long[] cellG = new long[cells];
    long[] cellB = new long[cells];

    for (int i = 0; i < size; ++i) {
      int c = s.colors()[i];
      int r = (c >> 16) & 0xFF;
      int g = (c >> 8) & 0xFF;
      int b = c & 0xFF;

      int col = clamp((int) (view.u(s, i) * gridSize), gridSize);
      int row = clamp((int) (view.v(s, i) * gridSize), gridSize);
      int cell = row * gridSize + col;
      cellCount[cell]++;
      cellR[cell] += r;
      cellG[cell] += g;
      cellB[cell] += b;

      int max = Math.max(r, Math.max(g, b));
      brightnessSum += max / 255.0;
      if (max > litThreshold) {
        lit++;
      }
      if (max == 0) {
        continue;
      }
      nonBlack++;
      int bin = hueBin(r, g, b, max);
      binCount[bin]++;
      binR[bin] += r;
      binG[bin] += g;
      binB[bin] += b;
    }

    List<DominantColor> dominant = new ArrayList<>();
    for (int n = 0; n < 3; ++n) {
      int best = -1;
      for (int bin = 0; bin < HUE_BINS; ++bin) {
        if (binCount[bin] > 0 && (best < 0 || binCount[bin] > binCount[best])) {
          best = bin;
        }
      }
      if (best < 0) {
        break;
      }
      dominant.add(new DominantColor(
          hex(binR[best] / binCount[best], binG[best] / binCount[best],
              binB[best] / binCount[best]),
          round4((double) binCount[best] / nonBlack)));
      binCount[best] = 0;
    }

    List<List<String>> grid = new ArrayList<>(gridSize);
    for (int row = 0; row < gridSize; ++row) {
      List<String> cols = new ArrayList<>(gridSize);
      for (int col = 0; col < gridSize; ++col) {
        int cell = row * gridSize + col;
        cols.add((cellCount[cell] == 0) ? null
            : hex(cellR[cell] / cellCount[cell], cellG[cell] / cellCount[cell], cellB[cell] / cellCount[cell]));
      }
      grid.add(cols);
    }

    return new FrameSummary(
        size,
        round4((size == 0) ? 0 : (double) nonBlack / size),
        round4((size == 0) ? 0 : (double) lit / size),
        round4((size == 0) ? 0 : brightnessSum / size),
        dominant,
        grid);
  }

  private static int clamp(int v, int gridSize) {
    return Math.max(0, Math.min(gridSize - 1, v));
  }

  private static int hueBin(int r, int g, int b, int max) {
    int min = Math.min(r, Math.min(g, b));
    if (max == min) {
      return 0; // achromatic lands in the red bin; dominant hex still reflects true color
    }
    float range = max - min;
    float hue;
    if (max == r) {
      hue = ((g - b) / range + 6f) % 6f;
    } else if (max == g) {
      hue = (b - r) / range + 2f;
    } else {
      hue = (r - g) / range + 4f;
    }
    return Math.min(HUE_BINS - 1, (int) (hue / 6f * HUE_BINS));
  }

  private static String hex(long r, long g, long b) {
    return String.format("#%02x%02x%02x", r, g, b);
  }

  private static double round4(double v) {
    return Math.round(v * 10_000.0) / 10_000.0;
  }
}
