package chromatikmcp.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;

import javax.imageio.ImageIO;

import chromatikmcp.domain.CameraProjection;
import chromatikmcp.domain.Frames;

/**
 * Rasterizes a frame snapshot to a PNG: the point cloud projected onto the image, each
 * point splatted as a filled disc on a dark background. Two projections are offered —
 * a fixed orthographic plane ({@link Frames.View}) for a quick structural read, and an
 * arbitrary {@link CameraProjection} for a viewpoint a person could actually stand at.
 *
 * <p>Pure JDK 2D — no window is ever opened. It is not, however, free of the AWT
 * environment: {@link BufferedImage#createGraphics()} initializes the local
 * {@link java.awt.GraphicsEnvironment}, and on macOS that links AppKit and registers the
 * JVM with LaunchServices as a foreground app (a Dock icon). Inside Chromatik that is
 * already true and harmless; batch JVMs that call this must set
 * {@code -Djava.awt.headless=true}, as the surefire config does. Runs on the HTTP worker
 * thread over a detached {@link Frames.FrameSnapshot}; no LX types, no MCP types.
 */
public final class FrameRaster {

  private FrameRaster() {}

  /** Off-state points read as "LED that is off" against this, rather than empty canvas. */
  private static final Color BACKGROUND = new Color(0x10, 0x10, 0x10);

  private static final int MIN_HEIGHT = 16;
  private static final int MAX_HEIGHT = 1024;

  /**
   * A camera has no model aspect ratio to inherit — its frame is whatever shape the
   * window is — so camera renders use a conventional 4:3, matching a typical preview.
   */
  private static final double CAMERA_ASPECT = 4.0 / 3.0;

  /** A disc may grow this many times its base size as the camera closes on a point. */
  private static final double MAX_NEAR_MAGNIFICATION = 8;

  public static byte[] png(Frames.FrameSnapshot s, Frames.View view, int width) {
    int height = height(s, view, width);
    BufferedImage image = newImage(width, height);
    Graphics2D g = image.createGraphics();
    try {
      prepare(g, width, height);
      int radius = baseRadius(s, width, height);
      int diameter = 2 * radius;
      for (int i = 0; i < s.size(); ++i) {
        g.setColor(new Color(s.colors()[i] & 0xFFFFFF));
        int x = Math.round(view.u(s, i) * (width - diameter));
        int y = Math.round(view.v(s, i) * (height - diameter));
        g.fillOval(x, y, diameter, diameter);
      }
    } finally {
      g.dispose();
    }
    return encode(image);
  }

  /**
   * Renders from an arbitrary viewpoint. Two things distinguish this from the orthographic
   * path, and both are what make an interior view read as interior: points are drawn far to
   * near so the near wall occludes the far one, and a point's disc grows as the camera
   * closes on it.
   *
   * @param projection built for this exact width/height (see {@link #cameraHeight}), so
   *     the image and the numeric grid summary describe the same frame
   */
  public static byte[] png(
      Frames.FrameSnapshot s, CameraProjection projection, int width, int height) {
    BufferedImage image = newImage(width, height);
    Graphics2D g = image.createGraphics();
    try {
      prepare(g, width, height);
      int baseRadius = baseRadius(s, width, height);
      double[] projected = new double[3];
      float[] u = new float[s.size()];
      float[] v = new float[s.size()];

      // Painter's algorithm: pack depth and index into one long and sort ascending, then
      // walk it backwards to draw far points first. The bit pattern of a non-negative
      // float orders the same way the float does, so this sorts by depth without a
      // comparator — and without boxing every index.
      long[] byDepth = new long[s.size()];
      int visible = 0;
      for (int i = 0; i < s.size(); ++i) {
        if (!projection.project(s.x()[i], s.y()[i], s.z()[i], projected)) {
          continue;
        }
        u[i] = (float) projected[0];
        v[i] = (float) projected[1];
        long depthBits = Float.floatToIntBits((float) Math.max(0, projected[2]));
        byDepth[visible++] = (depthBits << 32) | (i & 0xFFFFFFFFL);
      }
      Arrays.sort(byDepth, 0, visible);

      for (int n = visible - 1; n >= 0; --n) {
        int i = (int) (byDepth[n] & 0xFFFFFFFFL);
        double depth = Float.intBitsToFloat((int) (byDepth[n] >>> 32));
        int radius = nearRadius(baseRadius, projection.referenceDepth(), depth);
        int diameter = 2 * radius;
        long x = Math.round((double) u[i] * width) - radius;
        long y = Math.round((double) v[i] * height) - radius;
        if (x + diameter < 0 || x > width || y + diameter < 0 || y > height) {
          continue;
        }
        g.setColor(new Color(s.colors()[i] & 0xFFFFFF));
        g.fillOval((int) x, (int) y, diameter, diameter);
      }
    } finally {
      g.dispose();
    }
    return encode(image);
  }

  /** Preserve the model's aspect ratio on the view plane; square on degenerate ranges. */
  public static int height(Frames.FrameSnapshot s, Frames.View view, int width) {
    float uRange = view.uRange(s);
    float vRange = view.vRange(s);
    if (uRange <= 0 || vRange <= 0) {
      return Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, width));
    }
    return Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, Math.round(width * vRange / uRange)));
  }

  /** The height a camera render takes at {@code width}, and the aspect its projection needs. */
  public static int cameraHeight(int width) {
    return Math.max(MIN_HEIGHT,
        Math.min(MAX_HEIGHT, (int) Math.round(width / CAMERA_ASPECT)));
  }

  private static BufferedImage newImage(int width, int height) {
    return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
  }

  private static void prepare(Graphics2D g, int width, int height) {
    g.setColor(BACKGROUND);
    g.fillRect(0, 0, width, height);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
  }

  /**
   * Radius derives from the smaller dimension: a strip model clamps height to
   * {@link #MIN_HEIGHT}, and a width-derived disc bigger than the canvas would invert the
   * vertical mapping ({@code (height - diameter) < 0}) and splat mostly off-canvas.
   */
  private static int baseRadius(Frames.FrameSnapshot s, int width, int height) {
    int minDimension = Math.min(width, height);
    if (s.size() == 0) {
      return 2;
    }
    return Math.max(1, Math.min(minDimension / 2,
        Math.max(2, Math.round(0.4f * minDimension / (float) Math.sqrt(s.size())))));
  }

  /**
   * Perspective size falloff: a point at the camera's own orbit distance draws at the base
   * size, nearer ones grow. Capped, or a point a hand's breadth from the eye fills the
   * frame with one disc.
   */
  private static int nearRadius(int baseRadius, double referenceDepth, double depth) {
    if (referenceDepth <= 0 || depth <= 0) {
      return baseRadius;
    }
    double scale = Math.min(MAX_NEAR_MAGNIFICATION, referenceDepth / depth);
    return Math.max(1, (int) Math.round(baseRadius * scale));
  }

  private static byte[] encode(BufferedImage image) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      ImageIO.write(image, "png", out);
    } catch (IOException e) {
      throw new UncheckedIOException("PNG encoding failed", e);
    }
    return out.toByteArray();
  }
}
