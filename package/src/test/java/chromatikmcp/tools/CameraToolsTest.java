package chromatikmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import chromatikmcp.HeadlessLxTest;
import chromatikmcp.domain.Cameras;

import heronarts.lx.LX;
import heronarts.lx.model.GridModel;
import heronarts.lx.model.LXModel;

/**
 * Handler-level coverage of the six camera tools: the payload shape every one of them
 * shares, the two spellings of a viewpoint, and the failure modes an agent will actually
 * hit. Registry-level coverage (Tools.allTools) arrives at integration.
 */
class CameraToolsTest extends HeadlessLxTest {

  private static final double EPSILON = 1e-4;

  // get_frame reads back per-point colors, so this class needs the reindexed model
  // FramesTest uses: LXPoint indices come from a JVM-global counter and the immutable-model
  // LX constructor does not reindex, so a later LX in the same JVM gets points indexed 64..
  @Override
  protected LXModel newModel() {
    return new GridModel(8, 8).reindexPoints();
  }

  /** Accepts both success shapes — include_image=true yields OkImage, not Ok. */
  private static Map<String, Object> ok(Result<Map<String, Object>> result) {
    return switch (result) {
      case Result.Ok<Map<String, Object>> value -> value.value();
      case Result.OkImage<Map<String, Object>> image -> image.value();
      case Result.OkAwait<Map<String, Object>> awaited ->
          throw new AssertionError("test must advance the engine before awaiting: " + awaited);
      case Result.Error<Map<String, Object>> error ->
          throw new AssertionError(error.code() + ": " + error.message());
    };
  }

  private static Result.Error<Map<String, Object>> error(Result<Map<String, Object>> result) {
    return assertInstanceOf(Result.Error.class, result);
  }

  @SuppressWarnings("unchecked")
  private static double axis(Map<String, Object> payload, String vector, String axis) {
    return ((Number) ((Map<String, Object>) payload.get(vector)).get(axis)).doubleValue();
  }

  private static double number(Map<String, Object> payload, String key) {
    return ((Number) payload.get(key)).doubleValue();
  }

  @Test
  void getCameraReportsOrbitEyeAndWhetherAnybodyIsWatching() {
    LX lx = newHeadlessLx();
    Map<String, Object> payload = ok(new GetCamera(new Cameras()).handle(lx, Map.of()));

    assertEquals(
        List.of("projection", "fovDegrees", "theta", "phi", "radius", "target", "eye",
            "livePreview"),
        List.copyOf(payload.keySet()));
    assertEquals("perspective", payload.get("projection"));
    assertEquals(false, payload.get("livePreview"),
        "headless: no Chromatik preview is bound to this camera");
  }

  @Test
  void setCameraNudgesOneAxisAndLeavesTheRest() {
    LX lx = newHeadlessLx();
    Cameras cameras = new Cameras();
    Map<String, Object> before = ok(new GetCamera(cameras).handle(lx, Map.of()));

    Map<String, Object> after =
        ok(new SetCamera(cameras).handle(lx, Map.of("theta", 90)));

    assertEquals(90.0, number(after, "theta"), EPSILON);
    assertEquals(number(before, "radius"), number(after, "radius"), EPSILON);
    assertEquals(axis(before, "target", "y"), axis(after, "target", "y"), EPSILON);
  }

  @Test
  void setCameraPlacesTheEyeByPositionAndReportsTheOrbitItBecame() {
    LX lx = newHeadlessLx();
    Cameras cameras = new Cameras();

    // The motivating case: stand on the floor inside the piece and look up at it.
    Map<String, Object> payload = ok(new SetCamera(cameras).handle(lx, Map.of(
        "eye", Map.of("x", 0, "y", 0, "z", 0),
        "target", Map.of("x", 0, "y", 100, "z", 0),
        "projection", "perspective",
        "fovDegrees", 100)));

    assertEquals(-89.0, number(payload, "phi"), EPSILON, "clamped a degree off the pole");
    assertEquals(100.0, number(payload, "radius"), EPSILON);
    assertEquals(100.0, number(payload, "fovDegrees"), EPSILON);
    assertTrue(axis(payload, "eye", "y") < axis(payload, "target", "y"),
        "the eye stays below what it is aimed at");
  }

  @Test
  void setCameraRejectsMixingEyeWithOrbitFields() {
    LX lx = newHeadlessLx();
    Result.Error<Map<String, Object>> failure = error(Tools.invoke(
        new SetCamera(new Cameras()), lx,
        Map.of("eye", Map.of("x", 0, "y", 0, "z", 0), "theta", 90)));
    assertEquals(Result.INVALID_ARGUMENT, failure.code());
    assertTrue(failure.message().contains("theta"), failure.message());
  }

  @Test
  void setCameraRejectsAnEmptyRequest() {
    LX lx = newHeadlessLx();
    assertEquals(Result.INVALID_ARGUMENT,
        error(new SetCamera(new Cameras()).handle(lx, Map.of())).code());
  }

  @Test
  void setCameraRejectsAnIncompleteVector() {
    LX lx = newHeadlessLx();
    Result.Error<Map<String, Object>> failure = error(Tools.invoke(
        new SetCamera(new Cameras()), lx, Map.of("target", Map.of("x", 1, "y", 2))));
    assertEquals(Result.INVALID_ARGUMENT, failure.code());
    assertTrue(failure.message().contains("target.z"), failure.message());
  }

  @Test
  void saveListRecallRemoveRoundTrip() {
    LX lx = newHeadlessLx();
    Cameras cameras = new Cameras();

    Map<String, Object> saved = ok(new SaveCamera(cameras).handle(lx, Map.of(
        "name", "stage-looking-up",
        "eye", Map.of("x", 0, "y", 0, "z", 0),
        "target", Map.of("x", 0, "y", 100, "z", 0))));
    assertEquals("stage-looking-up", saved.get("name"));
    assertEquals(false, saved.get("replaced"));

    Map<String, Object> listed = ok(new ListCameras(cameras).handle(lx, Map.of()));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> entries = (List<Map<String, Object>>) listed.get("cameras");
    assertEquals(1, entries.size());
    assertEquals("stage-looking-up", entries.get(0).get("name"));

    // Move away, then come back to exactly the saved angle.
    Map<String, Object> moved = ok(new SetCamera(cameras).handle(lx, Map.of("phi", 45)));
    assertNotEquals(number(saved, "phi"), number(moved, "phi"), "the camera really moved");

    Map<String, Object> recalled =
        ok(new RecallCamera(cameras).handle(lx, Map.of("name", "stage-looking-up")));
    assertEquals("stage-looking-up", recalled.get("name"));
    assertEquals(number(saved, "phi"), number(recalled, "phi"), EPSILON);
    assertEquals(number(saved, "theta"), number(recalled, "theta"), EPSILON);
    assertEquals(number(saved, "radius"), number(recalled, "radius"), EPSILON);

    Map<String, Object> removed =
        ok(new RemoveCamera(cameras).handle(lx, Map.of("name", "stage-looking-up")));
    assertEquals("stage-looking-up", removed.get("removed"));
    assertEquals("camera", removed.get("kind"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> empty =
        (List<Map<String, Object>>) ok(new ListCameras(cameras).handle(lx, Map.of()))
            .get("cameras");
    assertTrue(empty.isEmpty());
  }

  @Test
  void saveCameraDefaultsToTheLiveCameraAndFlagsAnOverwrite() {
    LX lx = newHeadlessLx();
    Cameras cameras = new Cameras();
    ok(new SetCamera(cameras).handle(lx, Map.of("theta", 33)));

    Map<String, Object> saved =
        ok(new SaveCamera(cameras).handle(lx, Map.of("name", "here")));
    assertEquals(33.0, number(saved, "theta"), EPSILON);
    assertFalse((Boolean) saved.get("replaced"));

    ok(new SetCamera(cameras).handle(lx, Map.of("theta", 66)));
    Map<String, Object> again =
        ok(new SaveCamera(cameras).handle(lx, Map.of("name", "here")));
    assertEquals(66.0, number(again, "theta"), EPSILON);
    assertTrue((Boolean) again.get("replaced"));
  }

  @Test
  void unknownNamesReportNotFound() {
    LX lx = newHeadlessLx();
    Cameras cameras = new Cameras();
    assertEquals(Result.NOT_FOUND,
        error(Tools.invoke(new RecallCamera(cameras), lx, Map.of("name", "nope"))).code());
    assertEquals(Result.NOT_FOUND,
        error(Tools.invoke(new RemoveCamera(cameras), lx, Map.of("name", "nope"))).code());
  }

  /** The saved-angle payload must stay field-identical to the live-camera one. */
  @Test
  void everyCameraToolEmitsTheSameAngleFields() {
    LX lx = newHeadlessLx();
    Cameras cameras = new Cameras();
    Map<String, Object> live = ok(new GetCamera(cameras).handle(lx, Map.of()));
    Map<String, Object> saved =
        ok(new SaveCamera(cameras).handle(lx, Map.of("name", "here")));
    Map<String, Object> recalled =
        ok(new RecallCamera(cameras).handle(lx, Map.of("name", "here")));

    for (String field : List.of("projection", "fovDegrees", "theta", "phi", "radius")) {
      assertEquals(live.get(field), saved.get(field), field);
      assertEquals(live.get(field), recalled.get(field), field);
    }
    assertEquals(live.get("target"), saved.get("target"));
    assertEquals(live.get("eye"), recalled.get("eye"));
  }

  /** CameraArgs.present() drives the empty-request guard off a hand-kept field list. */
  @Test
  void theCameraFieldListCoversEveryCameraSchemaProperty() {
    for (String property : CameraArgs.schemaProperties().keySet()) {
      assertTrue(CameraArgs.present(Map.of(property, "any")),
          "CameraArgs.FIELDS is missing schema property '" + property + "'");
    }
  }

  @Test
  void getFrameRendersFromTheLiveCameraAndEchoesIt() {
    LX lx = newHeadlessLx();
    Cameras cameras = new Cameras();
    ok(new SetCamera(cameras).handle(lx, Map.of("theta", 45, "projection", "orthographic")));

    Map<String, Object> payload = ok(new GetFrame(cameras).handle(lx, Map.of(
        "camera", Cameras.CURRENT, "include_image", true)));

    assertFalse(payload.containsKey("view"), "a camera render is not an orthographic plane");
    @SuppressWarnings("unchecked")
    Map<String, Object> camera = (Map<String, Object>) payload.get("camera");
    assertEquals(Cameras.CURRENT, camera.get("name"));
    assertEquals(45.0, ((Number) camera.get("theta")).doubleValue(), EPSILON);
    assertEquals("orthographic", camera.get("projection"));
    assertEquals(false, camera.get("midMove"));
    assertEquals(
        ((Number) payload.get("imageWidth")).intValue() * 3 / 4,
        ((Number) payload.get("imageHeight")).intValue(),
        "camera renders use a 4:3 frame");
  }

  @Test
  void getFrameFlagsTheCurrentInterpolatedCameraMidMove() {
    LX lx = newHeadlessLx();
    lx.engine.setFixedDeltaMs(10);
    Cameras cameras = new Cameras();
    cameras.apply(lx, new Cameras.CameraAngle(
        350, 0, 10, new Cameras.Vec3(0, 0, 0),
        Cameras.Projection.PERSPECTIVE, 60));
    Cameras.CameraAnimation move = cameras.animate(
        lx,
        new Cameras.CameraAngle(10, 0, 10, new Cameras.Vec3(0, 0, 0),
            Cameras.Projection.PERSPECTIVE, 60),
        20, Cameras.AnimationEase.SINUSOIDAL);
    lx.engine.run();

    @SuppressWarnings("unchecked")
    Map<String, Object> camera = (Map<String, Object>)
        ok(new GetFrame(cameras).handle(lx, Map.of("camera", Cameras.CURRENT))).get("camera");
    assertEquals(true, camera.get("midMove"));
    assertEquals(0.0, ((Number) camera.get("theta")).doubleValue(), EPSILON,
        "the frame uses the current interpolated position rather than either endpoint");

    lx.engine.run();
    move.await();
  }

  @Test
  void animateCameraRejectsDurationOverTheExecutorDerivedCap() {
    LX lx = newHeadlessLx();
    Result.Error<Map<String, Object>> failure = error(new AnimateCamera(new Cameras()).handle(
        lx, Map.of("theta", 90, "durationMs", AnimateCamera.MAX_DURATION_MS + 1)));
    assertEquals(Result.INVALID_ARGUMENT, failure.code());
    assertTrue(failure.message().contains(String.valueOf(AnimateCamera.MAX_DURATION_MS)));
  }

  @Test
  void getFrameRendersFromASavedCamera() {
    LX lx = newHeadlessLx();
    Cameras cameras = new Cameras();
    ok(new SaveCamera(cameras).handle(lx, Map.of("name", "plan", "phi", 89)));
    // Move away: the render must use the saved angle, not wherever the camera is now.
    ok(new SetCamera(cameras).handle(lx, Map.of("phi", 0)));

    @SuppressWarnings("unchecked")
    Map<String, Object> camera = (Map<String, Object>)
        ok(new GetFrame(cameras).handle(lx, Map.of("camera", "plan"))).get("camera");
    assertEquals("plan", camera.get("name"));
    assertEquals(89.0, ((Number) camera.get("phi")).doubleValue(), EPSILON);
  }

  @Test
  void getFrameStillDefaultsToTheOrthographicFrontPlane() {
    LX lx = newHeadlessLx();
    Map<String, Object> payload = ok(new GetFrame(new Cameras()).handle(lx, Map.of()));
    assertEquals("front", payload.get("view"));
    assertFalse(payload.containsKey("camera"));
  }

  @Test
  void getFrameRejectsViewAndCameraTogether() {
    LX lx = newHeadlessLx();
    Result.Error<Map<String, Object>> failure = error(new GetFrame(new Cameras())
        .handle(lx, Map.of("view", "top", "camera", Cameras.CURRENT)));
    assertEquals(Result.INVALID_ARGUMENT, failure.code());
  }

  @Test
  void getFrameReportsAnUnknownCameraAsNotFound() {
    LX lx = newHeadlessLx();
    assertEquals(Result.NOT_FOUND, error(Tools.invoke(
        new GetFrame(new Cameras()), lx, Map.of("camera", "nope"))).code());
  }

  /** "current" already means the live viewpoint, so it cannot also name a saved one. */
  @Test
  void currentIsReservedAsACameraName() {
    LX lx = newHeadlessLx();
    Result.Error<Map<String, Object>> failure = error(Tools.invoke(
        new SaveCamera(new Cameras()), lx, Map.of("name", Cameras.CURRENT)));
    assertEquals(Result.INVALID_ARGUMENT, failure.code());
    assertTrue(failure.message().contains("reserved"), failure.message());
  }

  /**
   * A camera at its own target names no view direction: the perspective near/far planes
   * both collapse to zero and the orthographic box has zero width. Rejected rather than
   * accepted-then-rendered-blank.
   */
  @Test
  void setCameraRejectsANonPositiveRadius() {
    LX lx = newHeadlessLx();
    for (Object radius : List.of(0, -5)) {
      Result.Error<Map<String, Object>> failure = error(Tools.invoke(
          new SetCamera(new Cameras()), lx, Map.of("radius", radius)));
      assertEquals(Result.INVALID_ARGUMENT, failure.code(), "radius " + radius);
      assertTrue(failure.message().contains("radius must be greater than 0"),
          failure.message());
    }
  }

  @Test
  void saveCameraRejectsANonPositiveRadiusToo() {
    LX lx = newHeadlessLx();
    assertEquals(Result.INVALID_ARGUMENT, error(Tools.invoke(
        new SaveCamera(new Cameras()), lx, Map.of("name", "flat", "radius", 0))).code());
  }
}
