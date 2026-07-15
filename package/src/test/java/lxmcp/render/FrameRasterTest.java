package lxmcp.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import lxmcp.domain.Frames;

class FrameRasterTest {

  /** Four corner points: red bottom-left, green bottom-right, blue top-left, white top-right. */
  private static Frames.FrameSnapshot corners(float xRange, float yRange) {
    return new Frames.FrameSnapshot(
        new int[] {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF},
        new float[] {0f, 1f, 0f, 1f},
        new float[] {0f, 0f, 1f, 1f},
        new float[] {0.5f, 0.5f, 0.5f, 0.5f},
        4, xRange, yRange, 0f, "main");
  }

  private static BufferedImage decode(byte[] png) throws IOException {
    return ImageIO.read(new ByteArrayInputStream(png));
  }

  @Test
  void frontViewPlacesCornerColors() throws IOException {
    BufferedImage image = decode(FrameRaster.png(corners(1f, 1f), Frames.View.FRONT, 100));

    assertEquals(100, image.getWidth());
    assertEquals(100, image.getHeight(), "unit aspect ratio stays square");

    // radius = max(2, 0.4 * 100 / sqrt(4)) = 20; disc center = u * (width - 40) + 20
    assertEquals(0x0000FF, image.getRGB(20, 20) & 0xFFFFFF, "blue (xn=0, yn=1) at top-left");
    assertEquals(0xFFFFFF, image.getRGB(80, 20) & 0xFFFFFF, "white (xn=1, yn=1) at top-right");
    assertEquals(0xFF0000, image.getRGB(20, 80) & 0xFFFFFF, "red (xn=0, yn=0) at bottom-left");
    assertEquals(0x00FF00, image.getRGB(80, 80) & 0xFFFFFF, "green (xn=1, yn=0) at bottom-right");
    assertEquals(0x101010, image.getRGB(50, 50) & 0xFFFFFF, "dark background between discs");
  }

  @Test
  void heightFollowsModelAspectRatio() throws IOException {
    BufferedImage image = decode(FrameRaster.png(corners(2f, 1f), Frames.View.FRONT, 100));
    assertEquals(50, image.getHeight(), "vRange/uRange = 0.5");
  }

  @Test
  void degenerateRangeFallsBackToSquare() throws IOException {
    // TOP view of a planar model: vRange (zRange) is 0.
    BufferedImage image = decode(FrameRaster.png(corners(1f, 1f), Frames.View.TOP, 64));
    assertEquals(64, image.getHeight());
  }

  @Test
  void extremeAspectClampsToMinHeight() throws IOException {
    BufferedImage image = decode(FrameRaster.png(corners(100f, 1f), Frames.View.FRONT, 100));
    assertEquals(16, image.getHeight());
  }

  @Test
  void stripModelDiscsStayOnCanvas() throws IOException {
    // Extreme aspect clamps height to 16; the disc radius must shrink with it, or the
    // vertical mapping inverts ((height - diameter) < 0) and points render off-canvas.
    BufferedImage image = decode(FrameRaster.png(corners(100f, 1f), Frames.View.FRONT, 100));
    assertEquals(16, image.getHeight());

    // radius = min(8, max(2, round(0.4 * 16 / 2))) = 3; center = u/v * (dim - 6) + 3
    assertEquals(0x0000FF, image.getRGB(3, 3) & 0xFFFFFF, "blue top-left on canvas");
    assertEquals(0xFFFFFF, image.getRGB(97, 3) & 0xFFFFFF, "white top-right on canvas");
    assertEquals(0xFF0000, image.getRGB(3, 13) & 0xFFFFFF, "red bottom-left on canvas");
    assertEquals(0x00FF00, image.getRGB(97, 13) & 0xFFFFFF, "green bottom-right on canvas");
  }

  @Test
  void emptyModelStillEncodes() throws IOException {
    Frames.FrameSnapshot empty = new Frames.FrameSnapshot(
        new int[0], new float[0], new float[0], new float[0], 0, 0f, 0f, 0f, "main");
    BufferedImage image = decode(FrameRaster.png(empty, Frames.View.FRONT, 64));
    assertEquals(64, image.getWidth());
    assertEquals(0x101010, image.getRGB(32, 32) & 0xFFFFFF);
  }
}
