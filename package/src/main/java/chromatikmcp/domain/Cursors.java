package chromatikmcp.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import heronarts.lx.clip.Cursor;
import heronarts.lx.clip.LXClip;
import heronarts.lx.clip.LXComposition;
import heronarts.lx.clip.Locator;

/**
 * The wire ↔ {@link Cursor} codec for every composition/clip tool.
 *
 * <p>LX has exactly two cursor constructors — {@code constructAbsoluteCursor(millis)} and
 * {@code constructTempoCursor(beatCount, beatBasis)} — each deriving the other half of the
 * triple from the clip's {@code referenceBpm}. A Cursor can be read as a full triple but
 * never written as one, so the wire format is asymmetric by necessity:
 *
 * <p><b>Read</b> ({@link #describe}): always the full object
 * {@code {millis, beatCount, beatBasis, formatted}}. {@code formatted} is informational
 * only, never parsed back. Which fields are authoritative depends on the clip's
 * {@code timeBase}, which lives once on the clip/lane envelope, never per-cursor.
 *
 * <p><b>Write</b> ({@link #parse}): exactly one of four forms —
 * {@code {millis}} | {@code {beatCount[, beatBasis]}} |
 * {@code {bars[, beats, sixteenths]}} (1-indexed tempo sugar, the inverse of the TEMPO
 * {@code formatLabel}) | {@code {at: <origin>[, offsetBeats | offsetMillis]}}. The one-of
 * rule is enforced here, not in JSON Schema.
 *
 * <p>Mutates nothing; cursors returned by {@link #parse} are always fresh instances, never
 * aliases of live engine state (marker cursor fields are rewritten in place by LX).
 */
public final class Cursors {

  private Cursors() {}

  // The 'at' origin vocabulary has ONE definition: the originCursor switch (plus the
  // originList() error text) — no parallel Set to drift out of sync with it.
  private static final Set<String> KEYS = Set.of(
      "millis", "beatCount", "beatBasis", "bars", "beats", "sixteenths",
      "at", "offsetBeats", "offsetMillis");

  /**
   * The read-side cursor triple plus its display label. A {@link Cursor} is a live engine
   * field that LX rewrites in place, so this is the snapshot form every payload carries.
   *
   * <p>The serializer lives on the record rather than under {@code tools/} — the
   * record-attached case the conventions carve out for a nested, broadly reused shape (the
   * {@code Parameters.ParameterInfo.toMap()} precedent). A cursor is nested inside every
   * clip, lane, event, and locator payload AND inside {@code ParameterInfo.value} for a
   * {@code Cursor.Parameter}, whose serializer is itself record-attached; one definition
   * here is what keeps those two families from drifting. {@code Payloads.cursor} is the
   * tools-side entry point and delegates here.
   */
  public record CursorInfo(double millis, int beatCount, double beatBasis, String formatted) {

    /** The wire object: {@code {millis, beatCount, beatBasis, formatted}}. */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("millis", this.millis);
      map.put("beatCount", this.beatCount);
      map.put("beatBasis", this.beatBasis);
      map.put("formatted", this.formatted);
      return map;
    }
  }

  /** Snapshots {@code cursor} in the clip's time base. */
  public static CursorInfo describe(LXClip clip, Cursor cursor) {
    return new CursorInfo(
        cursor.getMillis(), cursor.getBeatCount(), cursor.getBeatBasis(), format(clip, cursor));
  }

  /**
   * User-facing label in the clip's own time base: {@code m:ss:mmm} under ABSOLUTE,
   * {@code bars.beats[.sixteenths]} (1-indexed) under TEMPO. Display only — lossy by
   * design, never parsed back.
   */
  public static String format(LXClip clip, Cursor cursor) {
    return clip.CursorOp().formatLabel(clip, cursor, Cursor.MIN_LABEL_SPACING);
  }

  /**
   * Parses the write-side wire object into a fresh {@link Cursor}, scaled by
   * {@code clip.referenceBpm}. Throws {@code invalid_argument} unless exactly one form is
   * present (see class doc). The result is NOT clamped — marker/event setters clamp
   * downstream, which is why every mutation payload echoes the cursor read back from the
   * engine rather than this parse result.
   */
  public static Cursor parse(LXClip clip, Map<String, Object> spec) {
    if (spec == null || spec.isEmpty()) {
      throw Resolve.invalidArgument(
          "Cursor requires exactly one of: {millis} | {beatCount[, beatBasis]} | "
              + "{bars[, beats, sixteenths]} | {at[, offsetBeats | offsetMillis]}");
    }
    for (String key : spec.keySet()) {
      if (!KEYS.contains(key)) {
        throw Resolve.invalidArgument("Unknown cursor key: " + key);
      }
    }
    boolean hasMillis = spec.containsKey("millis");
    boolean hasTempo = spec.containsKey("beatCount");
    boolean hasBars = spec.containsKey("bars");
    boolean hasAt = spec.containsKey("at");
    int forms = (hasMillis ? 1 : 0) + (hasTempo ? 1 : 0) + (hasBars ? 1 : 0) + (hasAt ? 1 : 0);
    if (forms != 1) {
      throw Resolve.invalidArgument(
          "Cursor requires exactly one of: millis | beatCount | bars | at (got "
              + (forms == 0 ? "none of them" : "several") + ": " + spec.keySet() + ")");
    }
    requireCompanions(spec, hasTempo, "beatCount", "beatBasis");
    requireCompanions(spec, hasBars, "bars", "beats", "sixteenths");
    requireCompanions(spec, hasAt, "at", "offsetBeats", "offsetMillis");

    if (hasMillis) {
      return clip.constructAbsoluteCursor(nonNegative(number(spec, "millis"), "millis"));
    }
    if (hasTempo) {
      long beatCountLong = integral(spec, "beatCount");
      if (beatCountLong < 0 || beatCountLong > Integer.MAX_VALUE) {
        throw Resolve.invalidArgument("beatCount out of range: " + beatCountLong);
      }
      int beatCount = (int) beatCountLong;
      double beatBasis = nonNegative(number(spec, "beatBasis", 0.0), "beatBasis");
      if (beatBasis >= 1.0) {
        throw Resolve.invalidArgument("beatBasis must be in [0, 1): " + beatBasis);
      }
      return clip.constructTempoCursor(beatCount, beatBasis);
    }
    if (hasBars) {
      // 1-indexed sugar, the exact inverse of the TEMPO formatLabel: bar/beat positions
      // as a musician reads them off the arrange ruler, resolved against the live
      // beatsPerBar time signature.
      //
      // The conversion and its validation are a local duplicate of LX's own
      // Tempo.triggerBarAndBeat — the formula below is its `(bar-1) * beatsPerBar +
      // beat - 1`, and the >= 1 checks are its `bar < 1 || beat < 1` guard. Calling
      // triggerBarAndBeat directly would mutate the live transport, so it is copied
      // rather than invoked; replace this with the upstream API if LX ever exposes the
      // conversion as a pure helper. Deliberately NOT rejected here, because upstream
      // does not: a beat past beatsPerBar (e.g. {bars:1, beats:5} in 4/4) carries into
      // the next bar. Tightening that is an LX change first, so downstream integrations
      // can't drift into subtly different bar/beat rules.
      int beatsPerBar = clip.getLX().engine.tempo.beatsPerBar.getValuei();
      long bars = atLeastOne(integral(spec, "bars"), "bars");
      long beats = spec.containsKey("beats") ? atLeastOne(integral(spec, "beats"), "beats") : 1;
      long sixteenths = spec.containsKey("sixteenths")
          ? atLeastOne(integral(spec, "sixteenths"), "sixteenths") : 1;
      if (sixteenths > 4) {
        throw Resolve.invalidArgument("sixteenths must be 1-4 (position within a beat): " + sixteenths);
      }
      long beatCount = (bars - 1) * beatsPerBar + (beats - 1);
      if (beatCount > Integer.MAX_VALUE) {
        throw Resolve.invalidArgument("bars/beats position out of range: " + bars + "." + beats);
      }
      return clip.constructTempoCursor((int) beatCount, (sixteenths - 1) * 0.25);
    }

    // {at: origin[, offset]}
    if (!(spec.get("at") instanceof String at)) {
      throw Resolve.invalidArgument("Cursor 'at' must be a string origin, one of: "
          + originList());
    }
    Cursor origin = originCursor(clip, at);
    boolean hasOffsetBeats = spec.containsKey("offsetBeats");
    boolean hasOffsetMillis = spec.containsKey("offsetMillis");
    if (hasOffsetBeats && hasOffsetMillis) {
      throw Resolve.invalidArgument("Cursor accepts offsetBeats or offsetMillis, not both");
    }
    if (!hasOffsetBeats && !hasOffsetMillis) {
      // Clone: 'origin' is a live cursor field that LX rewrites in place.
      return origin.clone();
    }
    double offset = number(spec, hasOffsetBeats ? "offsetBeats" : "offsetMillis");
    double magnitude = Math.abs(offset);
    Cursor delta = hasOffsetBeats
        ? clip.constructTempoCursor((int) magnitude, magnitude % 1.0)
        : clip.constructAbsoluteCursor(magnitude);
    // applyDelta bounds a subtraction at ZERO rather than throwing on negative cursors.
    return clip.CursorOp().applyDelta(origin.clone(), delta, offset >= 0);
  }

  private static Cursor originCursor(LXClip clip, String at) {
    if (at.startsWith("locator:")) {
      if (!(clip instanceof LXComposition composition)) {
        throw Resolve.invalidArgument(
            "Cursor origin '" + at + "' requires a composition; this clip has no locators");
      }
      int index;
      try {
        index = Integer.parseInt(at.substring("locator:".length()));
      } catch (NumberFormatException nfx) {
        throw Resolve.invalidArgument("Cursor origin locator index must be an integer: " + at);
      }
      if (index < 1 || index > composition.locators.size()) {
        throw Resolve.invalidArgument("No locator " + index + " (composition has "
            + composition.locators.size() + " locators, 1-indexed)");
      }
      Locator locator = composition.locators.get(index - 1);
      return locator.position.cursor;
    }
    return switch (at) {
      case "playhead" -> clip.getCursor();
      case "insertMarker" -> clip.insertMarker.cursor;
      case "start" -> Cursor.ZERO;
      case "end" -> clip.length.cursor;
      case "loopStart" -> clip.loopStart.cursor;
      case "loopEnd" -> clip.loopEnd.cursor;
      case "playStart" -> clip.playStart.cursor;
      case "playEnd" -> clip.playEnd.cursor;
      default -> throw Resolve.invalidArgument(
          "Unknown cursor origin '" + at + "' — expected one of: " + originList());
    };
  }

  private static String originList() {
    return "playhead, insertMarker, start, end, loopStart, loopEnd, playStart, playEnd, locator:<n>";
  }

  /** Rejects companion keys present without their primary form key. */
  private static void requireCompanions(
      Map<String, Object> spec, boolean formPresent, String primary, String... companions) {
    if (!formPresent) {
      for (String companion : companions) {
        if (!companion.equals(primary) && spec.containsKey(companion)) {
          throw Resolve.invalidArgument(
              "Cursor key '" + companion + "' requires '" + primary + "'");
        }
      }
    }
  }

  private static double number(Map<String, Object> spec, String key) {
    if (!(spec.get(key) instanceof Number n) || !Double.isFinite(n.doubleValue())) {
      throw Resolve.invalidArgument("Cursor key '" + key + "' must be a finite number");
    }
    return n.doubleValue();
  }

  private static double number(Map<String, Object> spec, String key, double defaultValue) {
    return spec.containsKey(key) ? number(spec, key) : defaultValue;
  }

  private static long integral(Map<String, Object> spec, String key) {
    double d = number(spec, key);
    if (d != Math.rint(d)) {
      throw Resolve.invalidArgument("Cursor key '" + key + "' must be an integer: " + d);
    }
    return (long) d;
  }

  private static double nonNegative(double value, String key) {
    if (value < 0) {
      throw Resolve.invalidArgument("Cursor key '" + key + "' must be >= 0: " + value);
    }
    return value;
  }

  private static long atLeastOne(long value, String key) {
    if (value < 1) {
      throw Resolve.invalidArgument("Cursor key '" + key + "' is 1-indexed and must be >= 1: " + value);
    }
    return value;
  }
}
