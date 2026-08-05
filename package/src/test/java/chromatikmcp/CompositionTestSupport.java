package chromatikmcp;

import static org.junit.jupiter.api.Assertions.assertTrue;

import heronarts.lx.LX;
import heronarts.lx.clip.Cursor;
import heronarts.lx.clip.LXClip;
import heronarts.lx.clip.LXComposition;
import heronarts.lx.clip.ParameterClipLane;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.pattern.color.SolidPattern;

/**
 * Shared fixture for composition/clip tests. A bare {@link #newHeadlessLx()} already
 * carries a fully initialized timeline: the composition exists with a master
 * {@code BusClipLane}, a {@code GlobalModulationClipLane}, and a
 * {@code ColorPaletteClipLane}; {@link #addChannelWithPattern} adds the channel-scoped
 * lane types (bus + MIDI + pattern) — together exactly the lane set whose upstream
 * {@code getPath()} overrides are broken, which the lane-aware canonical path must cover.
 *
 * <p>Two non-obvious recipes every composition test needs (docs/qa-strategy.md):
 * <ul>
 * <li>{@code playFrom} silently no-ops unless the clip {@code hasTimeline}, and a fresh
 * composition has none — {@link #enableTimeline} flips it via the one public path that
 * doesn't require recording ({@code setPlayEnd} past the current length grows the clip
 * and sets the flag).</li>
 * <li>Cursor assertions go through {@link #assertCursorEqual} (the clip's own
 * {@code CursorOp()}) — never {@code assertEquals} on millis, which TEMPO comparison
 * ignores. And snapshot any live marker cursor with {@code .immutable()} before a "do":
 * marker cursors are fields LX rewrites in place, so a live alias makes undo assertions
 * pass vacuously.</li>
 * </ul>
 */
public abstract class CompositionTestSupport extends HeadlessLxTest {

  /** The arrange timeline's composition — never null after LX construction. */
  protected static LXComposition composition(LX lx) {
    return lx.engine.timeline.getComposition();
  }

  /**
   * Adds a channel carrying one pattern: yields the channel {@code BusClipLane},
   * {@code MidiNoteClipLane}, and {@code PatternClipLane} on the composition, plus
   * registered channel parameters (fader, enabled, pattern parameters) that
   * {@link #addParameterLane} can target.
   */
  protected static LXChannel addChannelWithPattern(LX lx) {
    LXChannel channel = lx.engine.mixer.addChannel();
    channel.addPattern(new SolidPattern(lx));
    return channel;
  }

  /**
   * Flips {@code hasTimeline} without recording, giving the clip {@code lengthMillis} of
   * content: {@code setPlayEnd} past the current length grows the clip and sets the flag
   * (LXClip.java:828-832) — the only public path. Without this, {@code playFrom} is a
   * silent no-op on a fresh composition.
   */
  protected static void enableTimeline(LXClip clip, double lengthMillis) {
    clip.setPlayEnd(clip.constructAbsoluteCursor(lengthMillis));
  }

  /**
   * Creates (or returns the existing) parameter automation lane for {@code parameter},
   * which must be registered with the clip (e.g. a channel fader or pattern parameter on
   * a composition — LX warns and defaults if not).
   */
  protected static ParameterClipLane addParameterLane(LXClip clip, LXNormalizedParameter parameter) {
    return clip.createParameterLane(parameter);
  }

  /** Cursor equality in the clip's own time base — never assertEquals on raw millis. */
  protected static void assertCursorEqual(LXClip clip, Cursor expected, Cursor actual) {
    assertTrue(clip.CursorOp().isEqual(expected, actual),
        "expected cursor " + expected + " but was " + actual
            + " (timeBase=" + clip.getTimeBase() + ")");
  }
}
