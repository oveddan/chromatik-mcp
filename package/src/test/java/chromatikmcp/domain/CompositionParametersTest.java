package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import chromatikmcp.CompositionTestSupport;

import heronarts.lx.LX;
import heronarts.lx.clip.LXComposition;

/**
 * The two Parameters safety fixes for clip cursors (plan risks 4-5): set_parameter must
 * not bypass marker clamping via the resolvable cursor subparameters, and
 * list_parameters/get_parameter must not report every clip length as the aggregate's
 * never-updated 0.0.
 */
class CompositionParametersTest extends CompositionTestSupport {

  @Test
  void requireWritableRejectsCursorSubparameters() {
    // length/millis resolves and is technically writable — but writing it re-derives the
    // cursor with none of setLength's insert-marker rebound or setLoopStart's
    // loop-length coupling. The error must route the agent to set_clip_marker.
    LX lx = newHeadlessLx();
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Parameters.set(lx, "/lx/timeline/composition/length/millis", 5000));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    assertTrue(e.getMessage().contains("set_clip_marker"), e.getMessage());

    LXComposition composition = composition(lx);
    assertCursorEqual(composition, composition.constructAbsoluteCursor(0),
        composition.length.cursor);
  }

  @Test
  void requireWritableRejectsTheCursorAggregateItself() {
    // A direct setValue on the aggregate throws an IllegalStateException upstream — the
    // guard turns that internal crash into a typed invalid_argument with a pointer.
    LX lx = newHeadlessLx();
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Parameters.set(lx, "/lx/timeline/composition/insertMarker", 5000));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    assertTrue(e.getMessage().contains("set_clip_marker"), e.getMessage());
  }

  @Test
  void describeEmitsTheCursorObjectNotTheNeverUpdatedZero() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);

    Parameters.ParameterInfo info = Parameters.describe(composition.length);
    Map<?, ?> value = assertInstanceOf(Map.class, info.value(),
        "Cursor.Parameter value is the cursor object, not the aggregate's 0.0 double");
    assertEquals(10_000.0, value.get("millis"));
    assertTrue(value.containsKey("beatCount"));
    assertTrue(value.containsKey("beatBasis"));
    assertEquals(value.get("formatted"), info.formatted());
  }

  @Test
  void markerSettersClampAndTheMarkerIsTheEchoSource() {
    // The clamp-echo contract every marker mutation payload follows: setters silently
    // bound their input, so payloads must echo the cursor READ BACK from the marker,
    // never the requested one.
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);

    composition.setLoopEnd(composition.constructAbsoluteCursor(50_000));
    assertCursorEqual(composition, composition.length.cursor, composition.loopEnd.cursor);
    assertFalse(composition.CursorOp().isEqual(
        composition.constructAbsoluteCursor(50_000), composition.loopEnd.cursor),
        "requested cursor was clamped — echoing the request would lie");
  }

  @Test
  void playFromIsANoOpUntilTheTimelineExists() {
    // playFrom silently no-ops unless hasTimeline (LXClip.java:354) — the recipe that
    // otherwise costs a day: a fresh composition has no timeline until a recording, a
    // non-zero-length load, or the setPlayEnd growth path used by enableTimeline.
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    composition.playFrom(composition.constructAbsoluteCursor(0));
    assertFalse(composition.isRunning());
    assertFalse(composition.hasContent());

    enableTimeline(composition, 10_000);
    assertTrue(composition.hasContent());
    composition.playFrom(composition.constructAbsoluteCursor(0));
    assertTrue(composition.isRunning());
    composition.stop();
  }
}
