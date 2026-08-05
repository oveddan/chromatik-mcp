package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import chromatikmcp.CompositionTestSupport;

import heronarts.lx.LX;
import heronarts.lx.clip.Cursor;
import heronarts.lx.clip.LXComposition;
import heronarts.lx.command.LXCommand.Clip.Marker;

/** F1 domain primitives: marker set/move clamp-echo + undo, and transport guards. */
class ClipTransportMarkersTest extends CompositionTestSupport {

  @Test
  void setMarkerClampsAndEchoesTheReadBackNotTheRequest() {
    // The clamp-echo contract: setLoopEnd bounds to [loopStart, length], so requesting
    // 50s on a 10s clip must echo 10s — echoing the request would lie.
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);

    Cursor requested = composition.constructAbsoluteCursor(50_000);
    Cursor echoed = Clips.setMarker(lx, composition, Marker.LOOP_END, requested);

    assertCursorEqual(composition, composition.length.cursor, echoed);
    assertFalse(composition.CursorOp().isEqual(requested, echoed),
        "requested cursor was clamped — the echo must be the engine read-back");
  }

  @Test
  void setMarkerIsUndoableAndRedoable() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);
    // Start from a realistic loop end: the initial 0 is below MIN_LOOP, so undoing back
    // to it re-bounds — that would test upstream's degenerate-state behavior, not ours.
    composition.setLoopEnd(composition.constructAbsoluteCursor(4_000));

    // Snapshot with .immutable(): the live marker cursor is rewritten in place, so a
    // live alias would make the undo assertion pass vacuously.
    Cursor before = composition.loopEnd.cursor.immutable();
    Cursor target = composition.constructAbsoluteCursor(5_000);
    Clips.setMarker(lx, composition, Marker.LOOP_END, target);
    assertCursorEqual(composition, target, composition.loopEnd.cursor);

    lx.command.undo();
    assertCursorEqual(composition, before, composition.loopEnd.cursor);

    lx.command.redo();
    assertCursorEqual(composition, target, composition.loopEnd.cursor);
  }

  @Test
  void playEndUndoRestoresTheGrownLength() {
    // PLAY_END is the one marker whose set also grows length; SetMarker.undo restores
    // both, which is why the payload carries the whole envelope.
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    Cursor lengthBefore = composition.length.cursor.immutable();

    Clips.setMarker(lx, composition, Marker.PLAY_END,
        composition.constructAbsoluteCursor(10_000));
    assertTrue(composition.hasContent());
    assertCursorEqual(composition,
        composition.constructAbsoluteCursor(10_000), composition.length.cursor);

    lx.command.undo();
    assertCursorEqual(composition, lengthBefore, composition.length.cursor);
  }

  @Test
  void moveMarkerNudgesForwardAndBoundsBackwardMovesAtZero() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);
    Clips.setMarker(lx, composition, Marker.INSERT_MARKER,
        composition.constructAbsoluteCursor(5_000));

    // Snapshot with .immutable() before each move: the undo assertions below are the
    // proof the primitive rides LXCommand.Clip.MoveMarker, not a direct setter.
    Cursor before = composition.insertMarker.cursor.immutable();
    Cursor echoed = Clips.moveMarker(lx, composition, Marker.INSERT_MARKER,
        composition.constructAbsoluteCursor(2_000), true);
    assertCursorEqual(composition, composition.constructAbsoluteCursor(7_000), echoed);

    lx.command.undo();
    assertCursorEqual(composition, before, composition.insertMarker.cursor);
    lx.command.redo();
    assertCursorEqual(composition,
        composition.constructAbsoluteCursor(7_000), composition.insertMarker.cursor);

    before = composition.insertMarker.cursor.immutable();
    echoed = Clips.moveMarker(lx, composition, Marker.INSERT_MARKER,
        composition.constructAbsoluteCursor(20_000), false);
    assertCursorEqual(composition, Cursor.ZERO, echoed);

    // Undo after the zero-bounded move must restore the pre-move 7000ms, not the clamp.
    lx.command.undo();
    assertCursorEqual(composition, before, composition.insertMarker.cursor);
  }

  @Test
  void playThrowsTypedErrorsWhereUpstreamSilentlyNoOps() {
    // playFrom upstream no-ops without a timeline and when already running — both are
    // invisible to an agent, so the primitive turns them into typed errors.
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);

    Resolve.ResolveException noContent = assertThrows(Resolve.ResolveException.class,
        () -> Clips.play(composition, null));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, noContent.failure);

    enableTimeline(composition, 10_000);
    Clips.play(composition, composition.constructAbsoluteCursor(2_500));
    assertTrue(composition.isRunning());

    Resolve.ResolveException alreadyRunning = assertThrows(Resolve.ResolveException.class,
        () -> Clips.play(composition, null));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, alreadyRunning.failure);

    Clips.stop(composition);
    assertFalse(composition.isRunning());
  }

  @Test
  void launchAutomationFiresImmediatelyUnderDefaultQuantization() {
    // The default launchQuantization is NONE, so the quantized trigger fires inline even
    // with no engine loop running.
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);

    Clips.launchAutomation(composition, composition.constructAbsoluteCursor(1_000));
    assertTrue(composition.isRunning());
    Clips.stop(composition);
  }

  @Test
  void stopCancelsAPendingQuantizedLaunch() {
    // With a launch quantization set (and no engine ticks to fire it), the launch parks
    // as pending; upstream stop() would leave it scheduled to fire later. The primitive
    // cancels it — an agent's stop means stop.
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);
    lx.engine.tempo.launchQuantization.setValue(1);

    Clips.launchAutomation(composition, null);
    assertTrue(composition.isPending());
    assertFalse(composition.isRunning());

    Clips.stop(composition);
    assertFalse(composition.isPending());
    assertFalse(composition.isRunning());
  }
}
