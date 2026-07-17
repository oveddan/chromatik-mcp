package chromatikmcp.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

import chromatikmcp.domain.Frames;

/**
 * Rasterizes a frame snapshot to a PNG: orthographic projection of the point cloud onto
 * the selected view plane, each point splatted as a filled disc on a dark background.
 *
 * <p>Pure JDK 2D — never touches the AWT Toolkit (no window, no headless-mode concern),
 * so it is safe both in CI and inside the live Chromatik process. Runs on the HTTP worker
 * thread over a detached {@link Frames.FrameSnapshot}; no LX types, no MCP types.
 */
public final class FrameRaster {

  private FrameRaster() {}

  /** Off-state points read as "LED that is off" against this, rather than empty canvas. */
  private static final Color BACKGROUND = new Color(0x10, 0x10, 0x10);

  private static final int MIN_HEIGHT = 16;
  private static final int MAX_HEIGHT = 1024;

  public static byte[] png(Frames.FrameSnapshot s, Frames.View view, int width) {
    int height = height(s, view, width);
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setColor(BACKGROUND);
      g.fillRect(0, 0, width, height);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      // Radius derives from the smaller dimension: a strip model clamps height to
      // MIN_HEIGHT, and a width-derived disc bigger than the canvas would invert the
      // vertical mapping ((height - diameter) < 0) and splat mostly off-canvas.
      int minDimension = Math.min(width, height);
      int radius = (s.size() == 0) ? 2
          : Math.max(1, Math.min(minDimension / 2,
              Math.max(2, Math.round(0.4f * minDimension / (float) Math.sqrt(s.size())))));
      int diameter = 2 * radius;
      for (int i = 0; i < s.size(); ++i) {
        int c = s.colors()[i] & 0xFFFFFF;
        g.setColor(new Color(c));
        int x = Math.round(view.u(s, i) * (width - diameter));
        int y = Math.round(view.v(s, i) * (height - diameter));
        g.fillOval(x, y, diameter, diameter);
      }
    } finally {
      g.dispose();
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      ImageIO.write(image, "png", out);
    } catch (IOException e) {
      throw new UncheckedIOException("PNG encoding failed", e);
    }
    return out.toByteArray();
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
}
