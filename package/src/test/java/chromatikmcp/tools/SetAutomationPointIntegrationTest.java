package chromatikmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import chromatikmcp.CompositionTestSupport;
import chromatikmcp.domain.ClipLanes;
import chromatikmcp.domain.Resolve;

import heronarts.lx.LX;
import heronarts.lx.clip.Cursor;
import heronarts.lx.clip.LXComposition;
import heronarts.lx.clip.MidiNoteClipLane;
import heronarts.lx.clip.LXClipLane;
import heronarts.lx.clip.ParameterClipEvent;
import heronarts.lx.clip.ParameterClipLane;
import heronarts.lx.mixer.LXChannel;

/**
 * set_automation_point, handler-level: one tool over five LXCommands (MoveEvent,
 * SetValues, SetCurve, SetShape, ResetShape) with do-undo-assert per command path, the
 * clamp-echo pins (neighbor-clamped moves, boolean value snap), and the atCursor
 * optimistic-concurrency guard against a concurrent insert shifting indices (risk 2).
 */
class SetAutomationPointIntegrationTest extends CompositionTestSupport {

  private static final SetAutomationPoint TOOL = new SetAutomationPoint();

  @SuppressWarnings("unchecked")
  private static Map<String, Object> ok(Result<Map<String, Object>> result) {
    return (Map<String, Object>) assertInstanceOf(Result.Ok.class, result).value();
  }

  /** Fader lane with points 0.0@0ms, 0.5@2000ms, 1.0@4000ms on a 10s composition. */
  private static ParameterClipLane laneWithThreePoints(LX lx) {
    LXChannel channel = addChannelWithPattern(lx);
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);
    ParameterClipLane lane = addParameterLane(composition, channel.fader);
    lane.insertEvent(composition.constructAbsoluteCursor(0), 0.0);
    lane.insertEvent(composition.constructAbsoluteCursor(2_000), 0.5);
    lane.insertEvent(composition.constructAbsoluteCursor(4_000), 1.0);
    return lane;
  }

  @Test
  void cursorPlusValueIsASingleUndoableMove() {
    LX lx = newHeadlessLx();
    ParameterClipLane lane = laneWithThreePoints(lx);
    // Snapshot before the "do": the cursor is a field LX rewrites in place.
    Cursor before = lane.events.get(1).getCursor().immutable();

    Map<String, Object> payload = ok(TOOL.handle(lx, Map.of(
        "lanePath", ClipLanes.lanePath(lane),
        "index", 1,
        "cursor", Map.of("millis", 3_000),
        "normalized", 0.25)));

    assertEquals(1, payload.get("index"));
    assertEquals(0.25, payload.get("normalized"));
    assertEquals("POWER_EASE", payload.get("curve"));
    assertEquals(0.0, payload.get("shape"));
    assertEquals(3, payload.get("eventCount"));
    Map<?, ?> cursor = assertInstanceOf(Map.class, payload.get("cursor"));
    assertEquals(3_000.0, cursor.get("millis"));
    assertCursorEqual(lane.clip, lane.clip.constructAbsoluteCursor(3_000),
        lane.events.get(1).getCursor());
    assertEquals(0.25, lane.events.get(1).getNormalized());

    // One MoveEvent carried both aspects: a single undo restores cursor AND value.
    lx.command.undo();
    assertCursorEqual(lane.clip, before, lane.events.get(1).getCursor());
    assertEquals(0.5, lane.events.get(1).getNormalized());
  }

  @Test
  void aMoveIsClampedAtItsNeighborAndTheEchoIsTheReadBack() {
    LX lx = newHeadlessLx();
    ParameterClipLane lane = laneWithThreePoints(lx);

    // Requested 9000ms, but the next point sits at 4000ms: moveEvent clamps there.
    Map<String, Object> payload = ok(TOOL.handle(lx, Map.of(
        "lanePath", ClipLanes.lanePath(lane),
        "index", 1,
        "cursor", Map.of("millis", 9_000))));

    assertEquals(1, payload.get("index"));
    Map<?, ?> cursor = assertInstanceOf(Map.class, payload.get("cursor"));
    assertEquals(4_000.0, cursor.get("millis"));
    assertCursorEqual(lane.clip, lane.events.get(2).getCursor(), lane.events.get(1).getCursor());
    // Value untouched by a cursor-only move.
    assertEquals(0.5, payload.get("normalized"));
  }

  @Test
  void valueOnlyEditIsUndoable() {
    LX lx = newHeadlessLx();
    ParameterClipLane lane = laneWithThreePoints(lx);

    Map<String, Object> payload = ok(TOOL.handle(lx, Map.of(
        "lanePath", ClipLanes.lanePath(lane),
        "index", 1,
        "normalized", 0.9)));

    assertEquals(0.9, payload.get("normalized"));
    assertEquals(0.9, lane.events.get(1).getNormalized());

    // SetValues undo reloads the lane (fresh event objects) — re-read by index.
    lx.command.undo();
    assertEquals(0.5, lane.events.get(1).getNormalized());
  }

  @Test
  void aBooleanLaneSnapsTheValueAndTheEchoIsTheSnap() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);
    ParameterClipLane lane = addParameterLane(composition, channel.enabled);
    lane.insertEvent(composition.constructAbsoluteCursor(1_000), 1.0);

    Map<String, Object> payload = ok(TOOL.handle(lx, Map.of(
        "lanePath", ClipLanes.lanePath(lane),
        "index", 0,
        "normalized", 0.4)));

    assertEquals(0.0, payload.get("normalized"));
    assertEquals(0.0, lane.events.get(0).getNormalized());
  }

  @Test
  void curveAndShapeApplyTogetherAsTwoUndoSteps() {
    LX lx = newHeadlessLx();
    ParameterClipLane lane = laneWithThreePoints(lx);
    ParameterClipEvent event = lane.events.get(2);

    Map<String, Object> payload = ok(TOOL.handle(lx, Map.of(
        "lanePath", ClipLanes.lanePath(lane),
        "index", 2,
        "curve", "SMOOTHSTEP",
        "shape", 0.5)));

    assertEquals("SMOOTHSTEP", payload.get("curve"));
    assertEquals(0.5, payload.get("shape"));
    assertEquals(ParameterClipEvent.Curve.SMOOTHSTEP, event.getCurve());
    assertEquals(0.5, event.getShape());

    // Each aspect is its own command: shape undoes first, then curve.
    lx.command.undo();
    assertEquals(0.0, event.getShape());
    assertEquals(ParameterClipEvent.Curve.SMOOTHSTEP, event.getCurve());
    lx.command.undo();
    assertEquals(ParameterClipEvent.Curve.POWER_EASE, event.getCurve());
  }

  @Test
  void resetShapeIsItsOwnUndoableCommand() {
    LX lx = newHeadlessLx();
    ParameterClipLane lane = laneWithThreePoints(lx);
    lane.events.get(1).setShape(0.7);

    Map<String, Object> payload = ok(TOOL.handle(lx, Map.of(
        "lanePath", ClipLanes.lanePath(lane),
        "index", 1,
        "resetShape", true)));

    assertEquals(0.0, payload.get("shape"));
    assertEquals(0.0, lane.events.get(1).getShape());

    lx.command.undo();
    assertEquals(0.7, lane.events.get(1).getShape());
  }

  @Test
  void atCursorGuardCatchesAConcurrentInsertShiftingIndices() {
    LX lx = newHeadlessLx();
    ParameterClipLane lane = laneWithThreePoints(lx);
    // The agent read the 0.5 point at index 1 @2000ms; a concurrent insert at 1000ms
    // shifts it to index 2 — index 1 now addresses a different point.
    lane.insertEvent(lane.clip.constructAbsoluteCursor(1_000), 0.1);

    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> TOOL.handle(lx, Map.of(
            "lanePath", ClipLanes.lanePath(lane),
            "index", 1,
            "atCursor", Map.of("millis", 2_000),
            "normalized", 0.9)));

    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    assertTrue(e.getMessage().contains("re-read the lane"), e.getMessage());
    // The guard fired before any command: nothing changed.
    assertEquals(0.1, lane.events.get(1).getNormalized());
    assertEquals(0.5, lane.events.get(2).getNormalized());
  }

  @Test
  void atCursorGuardPassesWhenTheEventIsWhereExpected() {
    LX lx = newHeadlessLx();
    ParameterClipLane lane = laneWithThreePoints(lx);

    Map<String, Object> payload = ok(TOOL.handle(lx, Map.of(
        "lanePath", ClipLanes.lanePath(lane),
        "index", 1,
        "atCursor", Map.of("millis", 2_000),
        "normalized", 0.6)));

    assertEquals(0.6, payload.get("normalized"));
  }

  @Test
  void indexOutOfRangeIsTypedNotFound() {
    LX lx = newHeadlessLx();
    ParameterClipLane lane = laneWithThreePoints(lx);
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> TOOL.handle(lx, Map.of(
            "lanePath", ClipLanes.lanePath(lane), "index", 3, "normalized", 0.5)));
    assertEquals(Resolve.Failure.NOT_FOUND, e.failure);
  }

  @Test
  void aNonParameterLaneIsATypedMismatchNamingTheResolvedType() {
    LX lx = newHeadlessLx();
    ParameterClipLane lane = laneWithThreePoints(lx);
    LXClipLane<?> midiLane = lane.clip.lanes.stream()
        .filter(l -> l instanceof MidiNoteClipLane).findFirst().orElseThrow();
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> TOOL.handle(lx, Map.of(
            "lanePath", ClipLanes.lanePath(midiLane), "index", 0, "normalized", 0.5)));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    assertTrue(e.getMessage().contains("ParameterClipLane"), e.getMessage());
  }

  @Test
  void argumentValidationIsTypedInvalidArgument() {
    LX lx = newHeadlessLx();
    ParameterClipLane lane = laneWithThreePoints(lx);
    String lanePath = ClipLanes.lanePath(lane);

    // No edit aspect at all.
    Resolve.ResolveException none = assertThrows(Resolve.ResolveException.class,
        () -> TOOL.handle(lx, Map.of("lanePath", lanePath, "index", 1)));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, none.failure);
    assertTrue(none.getMessage().contains("At least one edit"), none.getMessage());

    // shape and resetShape together.
    Resolve.ResolveException both = assertThrows(Resolve.ResolveException.class,
        () -> TOOL.handle(lx, Map.of(
            "lanePath", lanePath, "index", 1, "shape", 0.5, "resetShape", true)));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, both.failure);

    // Out-of-range normalized value: rejected, not silently clamped — an out-of-unit
    // number almost always means the caller sent a raw parameter value.
    Resolve.ResolveException range = assertThrows(Resolve.ResolveException.class,
        () -> TOOL.handle(lx, Map.of("lanePath", lanePath, "index", 1, "normalized", 1.5)));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, range.failure);
    assertTrue(range.getMessage().contains("normalized must be within"), range.getMessage());

    // Unknown curve name.
    Resolve.ResolveException curve = assertThrows(Resolve.ResolveException.class,
        () -> TOOL.handle(lx, Map.of("lanePath", lanePath, "index", 1, "curve", "WIGGLY")));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, curve.failure);
    assertTrue(curve.getMessage().contains("POWER_S_CURVE"), curve.getMessage());
  }
}
