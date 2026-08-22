package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import chromatikmcp.HeadlessLxTest;

import heronarts.lx.LX;
import heronarts.lx.model.LXModel;

class CamerasTest extends HeadlessLxTest {

  private static final double EPSILON = 1e-6;

  private static Cameras.CameraAngle angle(double theta, double phi, double radius) {
    return new Cameras.CameraAngle(theta, phi, radius, new Cameras.Vec3(0, 0, 0),
        Cameras.Projection.PERSPECTIVE, 60);
  }

  private static void assertVec3(
      double x, double y, double z, Cameras.Vec3 actual, String message) {
    assertEquals(x, actual.x(), EPSILON, message + " x");
    assertEquals(y, actual.y(), EPSILON, message + " y");
    assertEquals(z, actual.z(), EPSILON, message + " z");
  }

  /**
   * The whole point of matching LX's formula rather than a textbook spherical one: theta 0
   * / phi 0 must look from -Z toward +Z, the same viewpoint as get_frame's 'front' plane.
   * If this drifts, the human preview and the agent's render stop agreeing.
   */
  @Test
  void eyeFollowsLxOrbitConvention() {
    assertVec3(0, 0, -10, Cameras.eye(angle(0, 0, 10)), "theta 0 sits on -Z");
    assertVec3(10, 0, 0, Cameras.eye(angle(90, 0, 10)), "theta 90 swings to +X");
    assertVec3(0, 0, 10, Cameras.eye(angle(180, 0, 10)), "theta 180 sits on +Z");
    assertVec3(-10, 0, 0, Cameras.eye(angle(270, 0, 10)), "theta 270 swings to -X");
    assertVec3(0, 10, 0, Cameras.eye(angle(0, 90, 10)), "phi 90 looks straight down");
    assertVec3(0, -10, 0, Cameras.eye(angle(0, -90, 10)), "phi -90 looks straight up");
  }

  @Test
  void eyeIsRelativeToTheTarget() {
    Cameras.CameraAngle offset = new Cameras.CameraAngle(0, 0, 10,
        new Cameras.Vec3(5, 6, 7), Cameras.Projection.PERSPECTIVE, 60);
    assertVec3(5, 6, -3, Cameras.eye(offset), "eye orbits the target, not the origin");
  }

  @Test
  void fromEyeInvertsEye() {
    for (double theta : List.of(0.0, 37.0, 90.0, 183.5, 359.0)) {
      for (double phi : List.of(-89.0, -42.0, 0.0, 12.5, 89.0)) {
        Cameras.CameraAngle original = new Cameras.CameraAngle(theta, phi, 17.5,
            new Cameras.Vec3(3, -4, 5), Cameras.Projection.ORTHOGRAPHIC, 90);
        Cameras.CameraAngle roundTrip = Cameras.fromEye(
            Cameras.eye(original), original.target(), original.projection(),
            original.fovDegrees());
        String at = "theta " + theta + " phi " + phi;
        assertEquals(original.theta(), roundTrip.theta(), 1e-9, at + " theta");
        assertEquals(original.phi(), roundTrip.phi(), 1e-9, at + " phi");
        assertEquals(original.radius(), roundTrip.radius(), 1e-9, at + " radius");
        assertEquals(original.projection(), roundTrip.projection(), at + " projection");
        assertEquals(original.fovDegrees(), roundTrip.fovDegrees(), 1e-9, at + " fov");
      }
    }
  }

  /** The interior viewpoint the whole feature exists for: eye on the floor, aimed up. */
  @Test
  void fromEyeExpressesAnUpwardInteriorView() {
    Cameras.CameraAngle looking = Cameras.fromEye(
        new Cameras.Vec3(0, 0, 0), new Cameras.Vec3(0, 100, 0),
        Cameras.Projection.PERSPECTIVE, 100);
    // Clamped off the pole, so it is 89 rather than 90 — but still aimed upward, which is
    // what distinguishes it from a camera hanging above the target.
    assertEquals(-89, looking.phi(), EPSILON);
    assertEquals(100, looking.radius(), EPSILON);
    assertTrue(Cameras.eye(looking).y() < looking.target().y(),
        "the eye stays below what it is aimed at");
  }

  @Test
  void fromEyeRejectsAnEyeOnItsTarget() {
    Resolve.ResolveException failure = assertThrows(Resolve.ResolveException.class,
        () -> Cameras.fromEye(new Cameras.Vec3(1, 2, 3), new Cameras.Vec3(1, 2, 3),
            Cameras.Projection.PERSPECTIVE, 60));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, failure.failure);
  }

  @Test
  void normalizeWrapsThetaAndClampsEverythingElse() {
    Cameras.CameraAngle normalized = Cameras.normalize(new Cameras.CameraAngle(
        -90, -120, -5, new Cameras.Vec3(0, 0, 0), Cameras.Projection.PERSPECTIVE, 1));
    assertEquals(270, normalized.theta(), EPSILON, "theta wraps rather than clamping");
    assertEquals(Cameras.PHI_MIN, normalized.phi(), EPSILON);
    assertEquals(Cameras.RADIUS_MIN, normalized.radius(), EPSILON);
    assertEquals(Cameras.FOV_MIN, normalized.fovDegrees(), EPSILON);

    Cameras.CameraAngle high = Cameras.normalize(new Cameras.CameraAngle(
        720.5, 120, 1e12, new Cameras.Vec3(0, 0, 0), Cameras.Projection.PERSPECTIVE, 400));
    assertEquals(0.5, high.theta(), EPSILON);
    assertEquals(Cameras.PHI_MAX, high.phi(), EPSILON);
    assertEquals(Cameras.RADIUS_MAX, high.radius(), EPSILON);
    assertEquals(Cameras.FOV_MAX, high.fovDegrees(), EPSILON);
  }

  @Test
  void currentFramesTheModelWhenNoPreviewIsBound() {
    LX lx = newHeadlessLx();
    Cameras cameras = new Cameras();

    Cameras.CameraView view = cameras.current(lx);
    assertFalse(view.livePreview(), "headless: nobody is watching this camera");
    LXModel model = lx.getModel();
    assertVec3(model.xMin + model.xRange / 2, model.yMin + model.yRange / 2,
        model.zMin + model.zRange / 2, view.angle().target(), "framed on the model center");
    assertTrue(view.angle().radius() > 0, "framed far enough out to see the model");
    assertEquals(Cameras.Projection.PERSPECTIVE, view.angle().projection());
  }

  @Test
  void applyClampsAndReportsTheAngleItLandedOn() {
    LX lx = newHeadlessLx();
    Cameras cameras = new Cameras();

    Cameras.CameraView applied = cameras.apply(lx, angle(-45, -120, 42));
    assertEquals(315, applied.angle().theta(), EPSILON);
    assertEquals(Cameras.PHI_MIN, applied.angle().phi(), EPSILON);
    assertEquals(42, applied.angle().radius(), EPSILON);
    assertEquals(applied.angle(), cameras.current(lx).angle(), "the move stuck");
  }

  @Test
  void savedAnglesRecallRemoveAndReportReplacement() {
    LX lx = newHeadlessLx();
    Cameras cameras = new Cameras();

    Cameras.SaveResult first = cameras.save("stage-looking-up", angle(10, -89, 200));
    assertFalse(first.replaced());
    assertEquals("stage-looking-up", first.camera().name());

    Cameras.SaveResult again = cameras.save("stage-looking-up", angle(20, -80, 300));
    assertTrue(again.replaced());
    assertEquals(1, cameras.list().size(), "a replaced name is not a second entry");

    cameras.apply(lx, angle(0, 0, 5));
    Cameras.CameraView recalled = cameras.recall(lx, "stage-looking-up");
    assertEquals(20, recalled.angle().theta(), EPSILON);
    assertEquals(recalled.angle(), cameras.current(lx).angle(), "recall moved the camera");

    assertEquals("stage-looking-up", cameras.remove("stage-looking-up").name());
    assertTrue(cameras.list().isEmpty());
  }

  @Test
  void namesAreTrimmedButOtherwiseExact() {
    LX lx = newHeadlessLx();
    Cameras cameras = new Cameras();
    cameras.save("  floor  ", angle(0, 0, 10));

    assertEquals("floor", cameras.list().get(0).name());
    assertNotNull(cameras.recall(lx, "floor"));
    assertEquals(Resolve.Failure.NOT_FOUND,
        assertThrows(Resolve.ResolveException.class, () -> cameras.lookup("Floor")).failure);
  }

  @Test
  void unknownNamesAreNotFound() {
    LX lx = newHeadlessLx();
    Cameras cameras = new Cameras();
    assertEquals(Resolve.Failure.NOT_FOUND,
        assertThrows(Resolve.ResolveException.class,
            () -> cameras.recall(lx, "nope")).failure);
    assertEquals(Resolve.Failure.NOT_FOUND,
        assertThrows(Resolve.ResolveException.class,
            () -> cameras.remove("nope")).failure);
  }

  @Test
  void blankNamesAreRejected() {
    Cameras cameras = new Cameras();
    assertEquals(Resolve.Failure.TYPE_MISMATCH,
        assertThrows(Resolve.ResolveException.class,
            () -> cameras.save("   ", angle(0, 0, 10))).failure);
  }

  @Test
  void savedAnglesRoundTripThroughProjectJson() {
    LX lx = newHeadlessLx();
    Cameras source = new Cameras();
    source.save("front", new Cameras.CameraAngle(0, 0, 10, new Cameras.Vec3(1, 2, 3),
        Cameras.Projection.PERSPECTIVE, 60));
    source.save("plan", new Cameras.CameraAngle(45, 89, 400, new Cameras.Vec3(-1, 0, 9),
        Cameras.Projection.ORTHOGRAPHIC, 15));

    JsonObject obj = new JsonObject();
    source.save(lx, obj);

    Cameras restored = new Cameras();
    restored.load(lx, obj);

    List<String> names = new ArrayList<>();
    for (Cameras.SavedCamera camera : restored.list()) {
      names.add(camera.name());
    }
    assertEquals(List.of("front", "plan"), names, "insertion order survives the round trip");
    assertEquals(source.lookup("plan"), restored.lookup("plan"));
  }

  /** Opening a project must not leave the previous project's angles in place. */
  @Test
  void loadReplacesTheWholeStoreEvenWhenTheProjectHasNone() {
    LX lx = newHeadlessLx();
    Cameras cameras = new Cameras();
    cameras.save("stale", angle(0, 0, 10));

    cameras.load(lx, new JsonObject());

    assertTrue(cameras.list().isEmpty());
  }

  @Test
  void loadSkipsMalformedEntriesRatherThanFailingTheProject() {
    LX lx = newHeadlessLx();
    Cameras source = new Cameras();
    source.save("good", angle(0, 0, 10));
    JsonObject obj = new JsonObject();
    source.save(lx, obj);
    obj.getAsJsonArray("cameras").add(new JsonObject());

    Cameras restored = new Cameras();
    restored.load(lx, obj);

    assertEquals(1, restored.list().size());
    assertEquals("good", restored.list().get(0).name());
  }

  @Test
  void aBoundPreviewOwnsTheViewpoint() {
    LX lx = newHeadlessLx();
    Cameras cameras = new Cameras();
    RecordingPreview preview = new RecordingPreview();
    cameras.bindPreview(preview);

    assertTrue(cameras.current(lx).livePreview());

    // The preview clamps on its own (LX parameters do), so the applied angle is reported
    // from the read-back, not echoed from the request.
    preview.clampRadiusTo = 50;
    Cameras.CameraView applied = cameras.apply(lx, angle(90, 10, 999));
    assertEquals(90, preview.applied.theta(), EPSILON, "the request reached the preview");
    assertEquals(50, applied.angle().radius(), EPSILON, "the response is the read-back");

    cameras.unbindPreview();
    Cameras.CameraView detached = cameras.current(lx);
    assertFalse(detached.livePreview());
    assertEquals(50, detached.angle().radius(), EPSILON,
        "unbinding keeps the last angle the preview reported, not a reframe of the model");
  }

  private static final class RecordingPreview implements Cameras.PreviewCamera {

    private Cameras.CameraAngle applied =
        new Cameras.CameraAngle(0, 0, 1, new Cameras.Vec3(0, 0, 0),
            Cameras.Projection.PERSPECTIVE, 60);
    private double clampRadiusTo = -1;

    @Override
    public Cameras.CameraAngle read() {
      if (this.clampRadiusTo < 0) {
        return this.applied;
      }
      return new Cameras.CameraAngle(this.applied.theta(), this.applied.phi(),
          this.clampRadiusTo, this.applied.target(), this.applied.projection(),
          this.applied.fovDegrees());
    }

    @Override
    public void apply(Cameras.CameraAngle angle) {
      this.applied = angle;
    }
  }
}
