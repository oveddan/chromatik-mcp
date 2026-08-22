package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CameraProjectionTest {

  private static final double EPSILON = 1e-9;

  private static Cameras.CameraAngle facingOrigin(
      double radius, Cameras.Projection projection, double fov) {
    return new Cameras.CameraAngle(0, 0, radius, new Cameras.Vec3(0, 0, 0), projection, fov);
  }

  private static double[] project(CameraProjection projection, double x, double y, double z) {
    double[] out = new double[3];
    assertTrue(projection.project(x, y, z, out), "expected (" + x + "," + y + "," + z + ") on screen");
    return out;
  }

  @Test
  void theTargetLandsDeadCenter() {
    CameraProjection projection = CameraProjection.of(
        facingOrigin(100, Cameras.Projection.PERSPECTIVE, 60), 4.0 / 3.0);
    double[] center = project(projection, 0, 0, 0);
    assertEquals(0.5, center[0], EPSILON, "u");
    assertEquals(0.5, center[1], EPSILON, "v");
    assertEquals(100, center[2], EPSILON, "depth is the orbit radius");
  }

  /** theta 0 looks from -Z toward +Z, so +X is to the right and +Y is up (v decreasing). */
  @Test
  void screenAxesFollowLxsZeroAzimuth() {
    CameraProjection projection = CameraProjection.of(
        facingOrigin(100, Cameras.Projection.PERSPECTIVE, 60), 1);
    assertTrue(project(projection, 10, 0, 0)[0] > 0.5, "+X is to the right");
    assertTrue(project(projection, 0, 10, 0)[1] < 0.5, "+Y is up the image");
  }

  @Test
  void perspectiveForeshortens() {
    CameraProjection projection = CameraProjection.of(
        facingOrigin(100, Cameras.Projection.PERSPECTIVE, 60), 1);
    // Same height off-axis, one at the target plane and one twice as far away: the far one
    // must sit closer to center. This is the whole reason an interior view reads correctly.
    double near = project(projection, 0, 10, 0)[1];
    double far = project(projection, 0, 10, 100)[1];
    assertTrue(far > near, "the further point is drawn nearer the center");
    assertTrue(near < 0.5 && far < 0.5, "both stay above center");
  }

  @Test
  void orthographicDoesNotForeshorten() {
    CameraProjection projection = CameraProjection.of(
        facingOrigin(100, Cameras.Projection.ORTHOGRAPHIC, 60), 1);
    assertEquals(
        project(projection, 0, 10, 0)[1],
        project(projection, 0, 10, 100)[1],
        EPSILON,
        "distance does not change where a point lands in a parallel projection");
  }

  /** LX frames an orthographic view to a width of exactly radius, so ±radius/2 is the edge. */
  @Test
  void orthographicFramingMatchesLxsHalfRadiusBox() {
    CameraProjection projection = CameraProjection.of(
        facingOrigin(100, Cameras.Projection.ORTHOGRAPHIC, 60), 1);
    assertEquals(1.0, project(projection, 50, 0, 0)[0], EPSILON, "right edge at +radius/2");
    assertEquals(0.0, project(projection, -50, 0, 0)[0], EPSILON, "left edge at -radius/2");
  }

  @Test
  void aWiderImageSeesMoreHorizontallyAndTheSameVertically() {
    Cameras.CameraAngle angle = facingOrigin(100, Cameras.Projection.PERSPECTIVE, 60);
    CameraProjection square = CameraProjection.of(angle, 1);
    CameraProjection wide = CameraProjection.of(angle, 2);

    double squareU = project(square, 20, 0, 0)[0];
    double wideU = project(wide, 20, 0, 0)[0];
    assertTrue(Math.abs(wideU - 0.5) < Math.abs(squareU - 0.5),
        "a wider frame pulls the same point toward center horizontally");
    assertEquals(project(square, 0, 20, 0)[1], project(wide, 0, 20, 0)[1], EPSILON,
        "the vertical field of view is what stays fixed");
  }

  @Test
  void pointsBehindTheCameraAreRejected() {
    CameraProjection projection = CameraProjection.of(
        facingOrigin(100, Cameras.Projection.PERSPECTIVE, 60), 1);
    double[] out = new double[3];
    // The eye is at z = -100 looking toward +z; anything further back is behind it.
    assertFalse(projection.project(0, 0, -200, out), "behind the eye");
    assertFalse(projection.project(0, 0, 1e7, out), "past the far plane");
  }

  @Test
  void offScreenPointsProjectButFallOutsideTheUnitFrame() {
    CameraProjection projection = CameraProjection.of(
        facingOrigin(100, Cameras.Projection.ORTHOGRAPHIC, 60), 1);
    assertTrue(project(projection, 500, 0, 0)[0] > 1,
        "off the side is still projected — the caller decides what to do with it");
  }

  @Test
  void cellBucketsOnScreenPointsAndRejectsTheRest() {
    CameraProjection projection = CameraProjection.of(
        facingOrigin(100, Cameras.Projection.ORTHOGRAPHIC, 60), 1);
    Frames.FrameSnapshot snapshot = new Frames.FrameSnapshot(
        new int[3], new float[3], new float[3], new float[3],
        new float[] {0f, 500f, 0f},
        new float[] {0f, 0f, 0f},
        new float[] {0f, 0f, -200f},
        3, 1f, 1f, 1f, "main");

    assertEquals(4, projection.cell(snapshot, 0, 3), "the target sits in the middle cell");
    assertEquals(-1, projection.cell(snapshot, 1, 3), "off the side is in no cell");
    assertEquals(-1, projection.cell(snapshot, 2, 3), "behind the camera is in no cell");
  }

  @Test
  void referenceDepthIsTheOrbitRadiusOnlyUnderPerspective() {
    assertEquals(100, CameraProjection.of(
        facingOrigin(100, Cameras.Projection.PERSPECTIVE, 60), 1).referenceDepth(), EPSILON);
    assertEquals(0, CameraProjection.of(
        facingOrigin(100, Cameras.Projection.ORTHOGRAPHIC, 60), 1).referenceDepth(), EPSILON,
        "a parallel projection has no size falloff to scale against");
  }
}
