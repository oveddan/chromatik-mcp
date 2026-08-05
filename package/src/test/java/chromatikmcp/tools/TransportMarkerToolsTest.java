package chromatikmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import chromatikmcp.CompositionTestSupport;
import chromatikmcp.domain.Resolve;

import heronarts.lx.LX;
import heronarts.lx.clip.LXClip;
import heronarts.lx.clip.LXComposition;
import heronarts.lx.mixer.LXChannel;

/** F1 handlers: get_clip, launch_clip, stop_clip, set_clip_marker. */
class TransportMarkerToolsTest extends CompositionTestSupport {

  @SuppressWarnings("unchecked")
  private static Map<String, Object> ok(Result<Map<String, Object>> result) {
    return (Map<String, Object>) assertInstanceOf(Result.Ok.class, result).value();
  }

  private static Map<?, ?> cursorOf(Map<String, Object> payload, String key) {
    return assertInstanceOf(Map.class, payload.get(key), key);
  }

  @Test
  void getClipDefaultsToTheCompositionAndAcceptsAGridClip() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);
    LXClip gridClip = channel.addClip(0);

    Map<String, Object> payload = ok(new GetClip().handle(lx, Map.of()));
    assertEquals("/lx/timeline/composition", payload.get("path"));
    assertEquals(composition.getTimeBase().name(), payload.get("timeBase"));
    assertEquals(false, payload.get("running"));
    assertEquals(false, payload.get("pending"));
    assertEquals(true, payload.get("hasContent"));
    assertEquals(composition.lanes.size(), payload.get("laneCount"));
    for (String marker : new String[] {
        "length", "loopStart", "loopEnd", "playStart", "playEnd", "insertMarker", "playhead" }) {
      Map<?, ?> cursor = cursorOf(payload, marker);
      assertTrue(cursor.containsKey("millis") && cursor.containsKey("beatCount")
          && cursor.containsKey("beatBasis") && cursor.containsKey("formatted"), marker);
    }

    Map<String, Object> grid = ok(new GetClip().handle(
        lx, Map.of("path", "/lx/mixer/channel/1/clip/1")));
    assertEquals("/lx/mixer/channel/1/clip/1", grid.get("path"));
    assertEquals(gridClip.lanes.size(), grid.get("laneCount"));
  }

  @Test
  void setClipMarkerEchoesTheClampedCursorAndFlagsTheClamp() {
    // The clamp-echo contract test at the tool boundary: the payload cursor is the
    // engine read-back, and clamped:true tells the agent its request was bounded.
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);

    Map<String, Object> payload = ok(new SetClipMarker().handle(lx, Map.of(
        "marker", "loopEnd", "cursor", Map.of("millis", 50_000))));
    assertEquals("loopEnd", payload.get("marker"));
    assertEquals(true, payload.get("clamped"));
    assertEquals(10_000.0, cursorOf(payload, "cursor").get("millis"));

    // The coupled envelope rides along: loopEnd there matches the echo.
    assertInstanceOf(Map.class, payload.get("clip"));
    assertEquals(10_000.0, ((Map<?, ?>) castClip(payload).get("loopEnd")).get("millis"));

    Map<String, Object> unclamped = ok(new SetClipMarker().handle(lx, Map.of(
        "marker", "loopEnd", "cursor", Map.of("millis", 5_000))));
    assertEquals(false, unclamped.get("clamped"));
    assertEquals(5_000.0, cursorOf(unclamped, "cursor").get("millis"));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castClip(Map<String, Object> payload) {
    return (Map<String, Object>) payload.get("clip");
  }

  @Test
  void setClipMarkerRelativeMoveIsSignedAndBoundedAtZero() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);
    ok(new SetClipMarker().handle(lx, Map.of(
        "marker", "insertMarker", "cursor", Map.of("beatCount", 8))));

    Map<String, Object> forward = ok(new SetClipMarker().handle(lx, Map.of(
        "marker", "insertMarker", "moveBeats", 2.5)));
    assertEquals(10, cursorOf(forward, "cursor").get("beatCount"));
    assertEquals(0.5, cursorOf(forward, "cursor").get("beatBasis"));
    assertFalse(forward.containsKey("clamped"), "no requested absolute target to compare");

    Map<String, Object> bounded = ok(new SetClipMarker().handle(lx, Map.of(
        "marker", "insertMarker", "moveMillis", -60_000)));
    assertEquals(0.0, cursorOf(bounded, "cursor").get("millis"));
  }

  @Test
  void setClipMarkerTruncateSetsLengthAndReboundsTheInsertMarker() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);
    ok(new SetClipMarker().handle(lx, Map.of(
        "marker", "insertMarker", "cursor", Map.of("millis", 8_000))));

    Map<String, Object> payload = ok(new SetClipMarker().handle(lx, Map.of(
        "marker", "truncate", "cursor", Map.of("millis", 5_000))));
    assertEquals(5_000.0, cursorOf(payload, "cursor").get("millis"));
    Map<String, Object> clip = castClip(payload);
    assertEquals(5_000.0, ((Map<?, ?>) clip.get("length")).get("millis"));
    assertEquals(5_000.0, ((Map<?, ?>) clip.get("insertMarker")).get("millis"));
  }

  @Test
  void setClipMarkerRequiresExactlyOneForm() {
    LX lx = newHeadlessLx();
    enableTimeline(composition(lx), 10_000);

    Resolve.ResolveException none = assertThrows(Resolve.ResolveException.class,
        () -> new SetClipMarker().handle(lx, Map.of("marker", "loopEnd")));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, none.failure);

    Resolve.ResolveException both = assertThrows(Resolve.ResolveException.class,
        () -> new SetClipMarker().handle(lx, Map.of(
            "marker", "loopEnd", "cursor", Map.of("millis", 1_000), "moveBeats", 1)));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, both.failure);
  }

  @Test
  void launchClipPlaysFromACursorAndStopClipHalts() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);

    Map<String, Object> played = ok(new LaunchClip().handle(lx, Map.of(
        "mode", "play", "from", Map.of("millis", 2_500))));
    assertEquals("play", played.get("mode"));
    assertEquals(true, played.get("running"));
    assertCursorEqual(composition,
        composition.constructAbsoluteCursor(2_500), composition.getCursor());

    Map<String, Object> stopped = ok(new StopClip().handle(lx, Map.of()));
    assertEquals(false, stopped.get("running"));
    assertEquals(false, stopped.get("pending"));
  }

  @Test
  void launchClipPlayModeIsATypedErrorOnAFreshComposition() {
    // The hasTimeline no-op trap, pinned at the tool boundary: upstream playFrom would
    // silently do nothing here.
    LX lx = newHeadlessLx();
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> new LaunchClip().handle(lx, Map.of()));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    assertTrue(e.getMessage().contains("set_clip_marker"),
        "error should point at the public way to give the clip a timeline");
  }

  @Test
  void launchClipAutomationModeRunsAndLaunchModeRejectsFrom() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);

    Map<String, Object> launched = ok(new LaunchClip().handle(lx, Map.of(
        "mode", "automation", "from", Map.of("at", "start"))));
    assertEquals(true, launched.get("running"));
    ok(new StopClip().handle(lx, Map.of()));

    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> new LaunchClip().handle(lx, Map.of(
            "mode", "launch", "from", Map.of("millis", 0))));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
  }
}
