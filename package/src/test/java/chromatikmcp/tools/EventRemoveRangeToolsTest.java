package chromatikmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import chromatikmcp.CompositionTestSupport;
import chromatikmcp.domain.ClipLanes;
import chromatikmcp.domain.Resolve;

import heronarts.lx.LX;
import heronarts.lx.clip.LXClipLane;
import heronarts.lx.clip.LXComposition;
import heronarts.lx.clip.MidiNoteClipLane;
import heronarts.lx.clip.ParameterClipLane;
import heronarts.lx.command.LXCommand;
import heronarts.lx.mixer.LXChannel;

/**
 * F6 — event removal and range ops, handler-level. Ranges are deliberately blind to
 * markers and clip length: the composed workflow is remove_clip_range first, then F1's
 * set_clip_marker truncate (pinned in {@link #rangeRemovalComposesWithTheTruncateMarker}).
 */
class EventRemoveRangeToolsTest extends CompositionTestSupport {

  @SuppressWarnings("unchecked")
  private static Map<String, Object> ok(Result<Map<String, Object>> result) {
    return (Map<String, Object>) assertInstanceOf(Result.Ok.class, result).value();
  }

  /** Fader automation lane holding one event per given position, 10s timeline. */
  private static ParameterClipLane laneWithEvents(LX lx, double... eventMillis) {
    LXChannel channel = addChannelWithPattern(lx);
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);
    ParameterClipLane lane = addParameterLane(composition, channel.fader);
    double normalized = 0.0;
    for (double millis : eventMillis) {
      // Distinct values so a stitch/merge bug can't masquerade as a correct count.
      lane.insertEvent(composition.constructAbsoluteCursor(millis), normalized += 0.1);
    }
    return lane;
  }

  private static void assertEventAtMillis(LXClipLane<?> lane, int index, double millis) {
    assertCursorEqual(lane.clip, lane.clip.constructAbsoluteCursor(millis),
        lane.events.get(index).getCursor());
  }

  @Test
  void removeAutomationPointRemovesEchoesTheOldAddressAndUndoRestores() {
    LX lx = newHeadlessLx();
    ParameterClipLane lane = laneWithEvents(lx, 1000, 2000, 3000);
    String lanePath = ClipLanes.lanePath(lane);

    Map<String, Object> payload = ok(new RemoveAutomationPoint().handle(
        lx, Map.of("lanePath", lanePath, "index", 1)));

    assertEquals(lanePath, payload.get("lanePath"));
    assertEquals(2, payload.get("eventCount"));
    Map<?, ?> removed = assertInstanceOf(Map.class, payload.get("removed"));
    assertEquals(1, removed.get("index"));
    Map<?, ?> cursor = assertInstanceOf(Map.class, removed.get("cursor"));
    assertEquals(2000.0, cursor.get("millis"));

    assertEquals(2, lane.events.size());
    assertEventAtMillis(lane, 0, 1000);
    assertEventAtMillis(lane, 1, 3000);

    lx.command.undo();
    assertEquals(3, lane.events.size());
    assertEventAtMillis(lane, 1, 2000);
  }

  @Test
  void removeAutomationPointAtCursorGuardsAgainstAShiftedLane() {
    LX lx = newHeadlessLx();
    ParameterClipLane lane = laneWithEvents(lx, 1000, 2000, 3000);
    String lanePath = ClipLanes.lanePath(lane);

    // Guard mismatch: the caller believed index 1 was still the 1000ms event.
    Resolve.ResolveException mismatch = assertThrows(Resolve.ResolveException.class,
        () -> new RemoveAutomationPoint().handle(lx, Map.of(
            "lanePath", lanePath, "index", 1, "atCursor", Map.of("millis", 1000))));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, mismatch.failure);
    assertEquals(3, lane.events.size());

    // Matching guard passes.
    ok(new RemoveAutomationPoint().handle(lx, Map.of(
        "lanePath", lanePath, "index", 1, "atCursor", Map.of("millis", 2000))));
    assertEquals(2, lane.events.size());
  }

  @Test
  void removeAutomationPointOutOfRangeIsNotFound() {
    LX lx = newHeadlessLx();
    ParameterClipLane lane = laneWithEvents(lx, 1000);
    Resolve.ResolveException missing = assertThrows(Resolve.ResolveException.class,
        () -> new RemoveAutomationPoint().handle(lx, Map.of(
            "lanePath", ClipLanes.lanePath(lane), "index", 5)));
    assertEquals(Resolve.Failure.NOT_FOUND, missing.failure);
  }

  @Test
  void removeAutomationPointRejectsMidiNoteLanes() {
    LX lx = newHeadlessLx();
    addChannelWithPattern(lx);
    LXComposition composition = composition(lx);
    LXClipLane<?> midiLane = composition.lanes.stream()
        .filter(lane -> lane instanceof MidiNoteClipLane).findFirst().orElseThrow();
    Resolve.ResolveException rejected = assertThrows(Resolve.ResolveException.class,
        () -> new RemoveAutomationPoint().handle(lx, Map.of(
            "lanePath", ClipLanes.lanePath(midiLane), "index", 0)));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, rejected.failure);
  }

  @Test
  void removeClipRangeIsInclusiveEchoesCursorsAndUndoRestores() {
    LX lx = newHeadlessLx();
    ParameterClipLane lane = laneWithEvents(lx, 1000, 2000, 3000, 4000, 5000);
    String lanePath = ClipLanes.lanePath(lane);

    Map<String, Object> payload = ok(new RemoveClipRange().handle(lx, Map.of(
        "lanePath", lanePath,
        "from", Map.of("millis", 2000),
        "to", Map.of("millis", 4000))));

    assertEquals(3, payload.get("removedCount"));
    assertEquals(2, payload.get("eventCount"));
    // The echoed range is the parsed request normalized to the full cursor object.
    assertEquals(2000.0, ((Map<?, ?>) payload.get("from")).get("millis"));
    assertEquals(4000.0, ((Map<?, ?>) payload.get("to")).get("millis"));

    assertEquals(2, lane.events.size());
    assertEventAtMillis(lane, 0, 1000);
    assertEventAtMillis(lane, 1, 5000);

    lx.command.undo();
    assertEquals(5, lane.events.size());
    assertEventAtMillis(lane, 2, 3000);
  }

  @Test
  void removeClipRangeOnAnEmptyRangeIsABenignNoOp() {
    // RISK 3: RemoveRange is an isIgnored command on an empty range — the common agent
    // case must be removedCount 0, not an internal error, and must push nothing to undo.
    LX lx = newHeadlessLx();
    ParameterClipLane lane = laneWithEvents(lx, 1000, 2000);

    Map<String, Object> payload = ok(new RemoveClipRange().handle(lx, Map.of(
        "lanePath", ClipLanes.lanePath(lane),
        "from", Map.of("millis", 6000),
        "to", Map.of("millis", 8000))));

    assertEquals(0, payload.get("removedCount"));
    assertEquals(2, payload.get("eventCount"));
    assertEquals(2, lane.events.size());
    assertNull(lx.command.getUndoCommand(), "ignored command must not land on the undo stack");
  }

  @Test
  void reversedRangeIsRejectedNotSilentlyEmpty() {
    LX lx = newHeadlessLx();
    ParameterClipLane lane = laneWithEvents(lx, 1000, 2000, 3000);
    Resolve.ResolveException reversed = assertThrows(Resolve.ResolveException.class,
        () -> new RemoveClipRange().handle(lx, Map.of(
            "lanePath", ClipLanes.lanePath(lane),
            "from", Map.of("millis", 4000),
            "to", Map.of("millis", 2000))));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, reversed.failure);
    assertEquals(3, lane.events.size());
  }

  @Test
  void collapseClipRangeKeepsTheBoundaryEventsAndUndoRestores() {
    LX lx = newHeadlessLx();
    ParameterClipLane lane = laneWithEvents(lx, 1000, 2000, 3000, 4000, 5000);

    Map<String, Object> payload = ok(new CollapseClipRange().handle(lx, Map.of(
        "lanePath", ClipLanes.lanePath(lane),
        "from", Map.of("millis", 1000),
        "to", Map.of("millis", 5000))));

    assertEquals(3, payload.get("removedCount"));
    assertEquals(2, payload.get("eventCount"));
    // Collapse keeps the range's first and last events as the surviving segment.
    assertEquals(2, lane.events.size());
    assertEventAtMillis(lane, 0, 1000);
    assertEventAtMillis(lane, 1, 5000);

    lx.command.undo();
    assertEquals(5, lane.events.size());
    assertEventAtMillis(lane, 2, 3000);
  }

  @Test
  void collapseClipRangeWithNoInteriorIsABenignNoOp() {
    LX lx = newHeadlessLx();
    ParameterClipLane lane = laneWithEvents(lx, 1000, 2000);

    Map<String, Object> payload = ok(new CollapseClipRange().handle(lx, Map.of(
        "lanePath", ClipLanes.lanePath(lane),
        "from", Map.of("millis", 0),
        "to", Map.of("millis", 10_000))));

    assertEquals(0, payload.get("removedCount"));
    assertEquals(2, lane.events.size());
    assertNull(lx.command.getUndoCommand(), "ignored command must not land on the undo stack");
  }

  @Test
  void rangeRemovalComposesWithTheTruncateMarker() {
    // Range ops leave markers and length alone by design; shortening is F1's
    // set_clip_marker truncate. Pin that remove-then-truncate composes: the truncate
    // cannot resurrect or disturb what the range removal already handled, and each step
    // stays independently undoable.
    LX lx = newHeadlessLx();
    ParameterClipLane lane = laneWithEvents(lx, 1000, 2000, 3000, 4000, 5000);
    LXComposition composition = composition(lx);

    ok(new RemoveClipRange().handle(lx, Map.of(
        "lanePath", ClipLanes.lanePath(lane),
        "from", Map.of("millis", 3000),
        "to", Map.of("millis", 5000))));
    assertEquals(2, lane.events.size());

    lx.command.perform(new LXCommand.Clip.SetMarker(composition,
        LXCommand.Clip.Marker.TRUNCATE, composition.constructAbsoluteCursor(2500)));
    assertCursorEqual(composition,
        composition.constructAbsoluteCursor(2500), composition.length.cursor);
    assertEquals(2, lane.events.size());
    assertEventAtMillis(lane, 0, 1000);
    assertEventAtMillis(lane, 1, 2000);

    lx.command.undo(); // truncate
    assertCursorEqual(composition,
        composition.constructAbsoluteCursor(10_000), composition.length.cursor);
    lx.command.undo(); // range removal
    assertEquals(5, lane.events.size());
    assertEventAtMillis(lane, 4, 5000);
  }
}
