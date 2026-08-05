package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import chromatikmcp.CompositionTestSupport;

import heronarts.lx.LX;
import heronarts.lx.clip.Cursor;
import heronarts.lx.clip.LXComposition;

/** The cursor wire codec — decision 3's contract, shared by every composition family. */
class CursorsTest extends CompositionTestSupport {

  private static Resolve.ResolveException parseFailure(LXComposition clip, Map<String, Object> spec) {
    return assertThrows(Resolve.ResolveException.class, () -> Cursors.parse(clip, spec));
  }

  @Test
  void describeEmitsTheFullTriplePlusFormatted() {
    LX lx = newHeadlessLx();
    LXComposition clip = composition(lx);
    // New clips default to TEMPO timeBase (LXClipEngine.timeBaseDefault); formatted
    // follows the clip's own base — pin both renderings.
    Cursors.CursorInfo tempo = Cursors.describe(clip, clip.constructTempoCursor(6, 0.25));
    assertEquals(6, tempo.beatCount());
    assertEquals(0.25, tempo.beatBasis());
    assertEquals("2.3.2", tempo.formatted());

    clip.timeBase.setValue(Cursor.TimeBase.ABSOLUTE);
    Cursors.CursorInfo absolute = Cursors.describe(clip, clip.constructAbsoluteCursor(12500));
    assertEquals(12500.0, absolute.millis());
    assertEquals("0:12:500", absolute.formatted());
  }

  /**
   * The record-attached serializer is the single definition of the cursor wire object —
   * every composition payload and {@code ParameterInfo}'s {@code Cursor.Parameter} value
   * route through it, so its keys and their order are the contract.
   */
  @Test
  void toMapPinsTheCursorWireShape() {
    LX lx = newHeadlessLx();
    LXComposition clip = composition(lx);
    clip.timeBase.setValue(Cursor.TimeBase.ABSOLUTE);
    Map<String, Object> map = Cursors.describe(clip, clip.constructAbsoluteCursor(12500)).toMap();
    assertEquals(List.of("millis", "beatCount", "beatBasis", "formatted"),
        List.copyOf(map.keySet()));
    assertEquals(12500.0, map.get("millis"));
    assertTrue(map.get("beatCount") instanceof Integer);
    assertTrue(map.get("beatBasis") instanceof Double);
    assertEquals("0:12:500", map.get("formatted"));
  }

  @Test
  void millisFormConstructsAnAbsoluteCursor() {
    LX lx = newHeadlessLx();
    LXComposition clip = composition(lx);
    Cursor cursor = Cursors.parse(clip, Map.of("millis", 12500));
    assertCursorEqual(clip, clip.constructAbsoluteCursor(12500), cursor);
    // Beat fields are derived from referenceBpm — the triple is always fully populated.
    assertEquals(clip.constructAbsoluteCursor(12500).getBeatCount(), cursor.getBeatCount());
  }

  @Test
  void tempoFormConstructsATempoCursorWithDefaultBasis() {
    LX lx = newHeadlessLx();
    LXComposition clip = composition(lx);
    assertCursorEqual(clip, clip.constructTempoCursor(25, 0.5),
        Cursors.parse(clip, Map.of("beatCount", 25, "beatBasis", 0.5)));
    assertCursorEqual(clip, clip.constructTempoCursor(25, 0),
        Cursors.parse(clip, Map.of("beatCount", 25)));
  }

  @Test
  void barsSugarIsTheInverseOfTheTempoFormatLabel() {
    LX lx = newHeadlessLx();
    LXComposition clip = composition(lx);
    int beatsPerBar = lx.engine.tempo.beatsPerBar.getValuei();
    Cursor cursor = Cursors.parse(clip, Map.of("bars", 2, "beats", 3, "sixteenths", 2));
    assertEquals((2 - 1) * beatsPerBar + (3 - 1), cursor.getBeatCount());
    assertEquals(0.25, cursor.getBeatBasis());
    // Round-trip: the TEMPO formatLabel renders this exact position back as "2.3.2".
    assertEquals("2.3.2",
        Cursor.TimeBase.TEMPO.operator.formatLabel(clip, cursor, Cursor.MIN_LABEL_SPACING));
    // bars alone defaults beats/sixteenths to 1 — the top of the bar.
    assertCursorEqual(clip, clip.constructTempoCursor(beatsPerBar, 0),
        Cursors.parse(clip, Map.of("bars", 2)));
  }

  /**
   * The bars/beats conversion is a local duplicate of LX's {@code
   * Tempo.triggerBarAndBeat} — copied rather than called, since calling it would mutate
   * the live transport. Both halves of that alignment are pinned here so a well-meaning
   * "fix" can't quietly fork the bar/beat rules from upstream's.
   */
  @Test
  void barsSugarMatchesUpstreamTriggerBarAndBeatSemantics() {
    LX lx = newHeadlessLx();
    LXComposition clip = composition(lx);
    int beatsPerBar = lx.engine.tempo.beatsPerBar.getValuei();

    // A beat past beatsPerBar carries into the next bar rather than erroring: upstream
    // applies the same formula with no upper bound, so neither do we.
    assertEquals((1 - 1) * beatsPerBar + (beatsPerBar + 1 - 1),
        Cursors.parse(clip, Map.of("bars", 1, "beats", beatsPerBar + 1)).getBeatCount());

    // The lower bound IS upstream's ("Bar and beat must be 1 or greater").
    parseFailure(clip, Map.of("bars", 0));
    parseFailure(clip, Map.of("bars", 1, "beats", 0));
  }

  @Test
  void atOriginsReadTheLiveMarkers() {
    LX lx = newHeadlessLx();
    LXComposition clip = composition(lx);
    enableTimeline(clip, 10_000);
    clip.setInsertMarker(clip.constructAbsoluteCursor(4_000));

    assertCursorEqual(clip, Cursor.ZERO, Cursors.parse(clip, Map.of("at", "start")));
    assertCursorEqual(clip, clip.length.cursor, Cursors.parse(clip, Map.of("at", "end")));
    assertCursorEqual(clip, clip.insertMarker.cursor,
        Cursors.parse(clip, Map.of("at", "insertMarker")));
    assertCursorEqual(clip, clip.playEnd.cursor, Cursors.parse(clip, Map.of("at", "playEnd")));
    assertCursorEqual(clip, clip.getCursor(), Cursors.parse(clip, Map.of("at", "playhead")));
  }

  @Test
  void atReturnsACopyNeverALiveAlias() {
    // Marker cursors are fields LX rewrites in place — parse must clone, or a caller
    // holding the result would silently track (and worse, mutate) engine state.
    LX lx = newHeadlessLx();
    LXComposition clip = composition(lx);
    enableTimeline(clip, 10_000);
    clip.setInsertMarker(clip.constructAbsoluteCursor(4_000));

    Cursor parsed = Cursors.parse(clip, Map.of("at", "insertMarker"));
    parsed.advance(clip.constructAbsoluteCursor(1_000));
    assertCursorEqual(clip, clip.constructAbsoluteCursor(4_000), clip.insertMarker.cursor);
  }

  @Test
  void atWithOffsetsAppliesDeltasBoundedAtZero() {
    LX lx = newHeadlessLx();
    LXComposition clip = composition(lx);
    enableTimeline(clip, 60_000);
    clip.setInsertMarker(clip.constructAbsoluteCursor(10_000));

    Cursor plus = Cursors.parse(clip, Map.of("at", "insertMarker", "offsetMillis", 2_500));
    assertCursorEqual(clip, clip.constructAbsoluteCursor(12_500), plus);

    Cursor plusBeats = Cursors.parse(clip, Map.of("at", "insertMarker", "offsetBeats", 4));
    Cursor expected = clip.insertMarker.cursor.add(clip.constructTempoCursor(4, 0));
    assertCursorEqual(clip, expected, plusBeats);

    // A negative offset larger than the origin bottoms out at ZERO instead of throwing.
    Cursor bounded = Cursors.parse(clip, Map.of("at", "insertMarker", "offsetMillis", -99_999));
    assertCursorEqual(clip, Cursor.ZERO, bounded);
  }

  @Test
  void locatorOriginIsOneIndexed() {
    LX lx = newHeadlessLx();
    LXComposition clip = composition(lx);
    enableTimeline(clip, 60_000);
    clip.addLocator(clip.constructAbsoluteCursor(30_000));

    assertCursorEqual(clip, clip.constructAbsoluteCursor(30_000),
        Cursors.parse(clip, Map.of("at", "locator:1")));
    assertEquals(Resolve.Failure.TYPE_MISMATCH,
        parseFailure(clip, Map.of("at", "locator:2")).failure);
    assertEquals(Resolve.Failure.TYPE_MISMATCH,
        parseFailure(clip, Map.of("at", "locator:zero")).failure);
  }

  @Test
  void exactlyOneFormIsEnforced() {
    LX lx = newHeadlessLx();
    LXComposition clip = composition(lx);

    assertTrue(parseFailure(clip, Map.of()).getMessage().contains("exactly one"));
    parseFailure(clip, Map.of("millis", 100, "beatCount", 2));
    parseFailure(clip, Map.of("millis", 100, "at", "start"));
    parseFailure(clip, Map.of("bars", 1, "beatCount", 0));
    // Companion keys without their primary form key.
    parseFailure(clip, Map.of("beatBasis", 0.5));
    parseFailure(clip, Map.of("beats", 2));
    parseFailure(clip, Map.of("offsetBeats", 4));
    // Both offsets at once.
    parseFailure(clip, Map.of("at", "start", "offsetBeats", 1, "offsetMillis", 100));
    // Unknown keys are typos, not silence.
    parseFailure(clip, Map.of("milis", 100));
    // Null map (absent argument) is the caller's bug surfaced as invalid_argument.
    HashMap<String, Object> nullSpec = null;
    assertEquals(Resolve.Failure.TYPE_MISMATCH, parseFailure(clip, nullSpec).failure);
  }

  @Test
  void valueValidationRejectsNegativesAndNonIntegers() {
    LX lx = newHeadlessLx();
    LXComposition clip = composition(lx);
    parseFailure(clip, Map.of("millis", -1));
    parseFailure(clip, Map.of("beatCount", 1.5));
    parseFailure(clip, Map.of("beatCount", -2));
    parseFailure(clip, Map.of("beatCount", 0, "beatBasis", 1.0));
    parseFailure(clip, Map.of("bars", 0));
    parseFailure(clip, Map.of("bars", 1, "sixteenths", 5));
    parseFailure(clip, Map.of("at", "nowhere"));
  }
}
