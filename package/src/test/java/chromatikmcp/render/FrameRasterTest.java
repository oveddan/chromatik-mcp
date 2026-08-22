package chromatikmcp.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import chromatikmcp.domain.CameraProjection;
import chromatikmcp.domain.Cameras;
import chromatikmcp.domain.Frames;

class FrameRasterTest {

  /** Four corner points: red bottom-left, green bottom-right, blue top-left, white top-right. */
  private static Frames.FrameSnapshot corners(float xRange, float yRange) {
    float[] xn = {0f, 1f, 0f, 1f};
    float[] yn = {0f, 0f, 1f, 1f};
    float[] zn = {0.5f, 0.5f, 0.5f, 0.5f};
    return new Frames.FrameSnapshot(
        new int[] {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF},
        xn, yn, zn,
        // World coordinates span the same unit square the normalized ones do, so the
        // camera path can be aimed at this fixture with plain numbers.
        xn, yn, new float[] {0f, 0f, 0f, 0f},
        4, xRange, yRange, 0f, "main");
  }

  private static BufferedImage decode(byte[] png) throws IOException {
    return ImageIO.read(new ByteArrayInputStream(png));
  }

  /**
   * Guards the surefire {@code -Djava.awt.headless=true} argLine. Without it,
   * {@link java.awt.image.BufferedImage#createGraphics()} below initializes the macOS
   * CGraphicsEnvironment, which registers the test fork with LaunchServices as a foreground
   * application and bounces a Java icon in the Dock during every build.
   *
   * <p>The property, not {@link GraphicsEnvironment#isHeadless()}, is what this asserts:
   * on a display-less CI worker {@code isHeadless()} is true whether or not the argLine is
   * set, so it would stay green there while macOS builds resumed opening a GUI app. The
   * property is the only signal that fails everywhere the argLine goes missing.
   */
  @Test
  void testJvmIsHeadless() {
    assertEquals("true", System.getProperty("java.awt.headless"),
        "test JVM must run with -Djava.awt.headless=true"
            + " — see the surefire argLine in package/pom.xml");
    assertTrue(GraphicsEnvironment.isHeadless(), "the JVM must actually honor the property");
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
        new int[0], new float[0], new float[0], new float[0],
        new float[0], new float[0], new float[0], 0, 0f, 0f, 0f, "main");
    BufferedImage image = decode(FrameRaster.png(empty, Frames.View.FRONT, 64));
    assertEquals(64, image.getWidth());
    assertEquals(0x101010, image.getRGB(32, 32) & 0xFFFFFF);
  }

  /**
   * Two points on the view axis, the near one red and the far one green. A painter's
   * algorithm must leave red on top — without it the far wall of a nested structure draws
   * over the near one and an interior view is meaningless.
   */
  @Test
  void nearPointsOccludeFarOnes() throws IOException {
    Frames.FrameSnapshot inLine = new Frames.FrameSnapshot(
        new int[] {0xFFFF0000, 0xFF00FF00},
        new float[2], new float[2], new float[2],
        new float[] {0f, 0f},
        new float[] {0f, 0f},
        new float[] {0f, 50f},
        2, 1f, 1f, 1f, "main");
    Cameras.CameraAngle angle = new Cameras.CameraAngle(
        0, 0, 100, new Cameras.Vec3(0, 0, 0), Cameras.Projection.PERSPECTIVE, 60);

    int width = 120;
    int height = FrameRaster.cameraHeight(width);
    BufferedImage image = decode(FrameRaster.png(
        inLine, CameraProjection.of(angle, (double) width / height), width, height));

    assertEquals(0xFF0000, image.getRGB(width / 2, height / 2) & 0xFFFFFF,
        "the nearer point wins the center pixel");
  }

  @Test
  void cameraRendersAtTheDeclaredSize() throws IOException {
    Cameras.CameraAngle angle = new Cameras.CameraAngle(
        0, 0, 4, new Cameras.Vec3(0.5, 0.5, 0), Cameras.Projection.ORTHOGRAPHIC, 60);
    int width = 160;
    int height = FrameRaster.cameraHeight(width);
    assertEquals(120, height, "camera renders use a 4:3 frame");

    BufferedImage image = decode(FrameRaster.png(
        corners(1f, 1f), CameraProjection.of(angle, (double) width / height), width, height));
    assertEquals(width, image.getWidth());
    assertEquals(height, image.getHeight());
    assertEquals(0x101010, image.getRGB(2, 2) & 0xFFFFFF, "background outside the model");
  }

  @Test
  void aCameraLookingAwayRendersNothingButBackground() throws IOException {
    // theta 180 puts the eye on the +Z side of its target looking back toward -Z; with the
    // target beyond the model, the unit square ends up behind the camera.
    Cameras.CameraAngle away = new Cameras.CameraAngle(
        180, 0, 4, new Cameras.Vec3(0.5, 0.5, -8), Cameras.Projection.PERSPECTIVE, 60);
    int width = 64;
    int height = FrameRaster.cameraHeight(width);
    BufferedImage image = decode(FrameRaster.png(
        corners(1f, 1f), CameraProjection.of(away, (double) width / height), width, height));

    for (int x = 0; x < width; x += 8) {
      for (int y = 0; y < height; y += 8) {
        assertEquals(0x101010, image.getRGB(x, y) & 0xFFFFFF, "background at " + x + "," + y);
      }
    }
  }

  @Test
  void emptyModelStillEncodesFromACamera() throws IOException {
    Frames.FrameSnapshot empty = new Frames.FrameSnapshot(
        new int[0], new float[0], new float[0], new float[0],
        new float[0], new float[0], new float[0], 0, 0f, 0f, 0f, "main");
    Cameras.CameraAngle angle = new Cameras.CameraAngle(
        0, 0, 10, new Cameras.Vec3(0, 0, 0), Cameras.Projection.PERSPECTIVE, 60);
    BufferedImage image = decode(
        FrameRaster.png(empty, CameraProjection.of(angle, 4.0 / 3.0), 64, 48));
    assertEquals(64, image.getWidth());
    assertEquals(0x101010, image.getRGB(32, 24) & 0xFFFFFF);
  }
}
