package chromatikmcp.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.clip.AudioClipEvent;
import heronarts.lx.clip.Cursor;
import heronarts.lx.clip.LXClipEvent;
import heronarts.lx.clip.LXClipLane;
import heronarts.lx.clip.LXCompositionEvent;
import heronarts.lx.clip.MidiNoteClipEvent;
import heronarts.lx.clip.MidiNoteClipLane;
import heronarts.lx.clip.ParameterClipEvent;
import heronarts.lx.clip.ParameterClipLane;
import heronarts.lx.clip.PatternClipEvent;
import heronarts.lx.clip.TextNoteClipEvent;
import heronarts.lx.command.LXCommand;

import chromatikmcp.domain.Cursors.CursorInfo;

/**
 * Clip-lane event primitives. Events are NOT {@code LXComponent}s and have no canonical
 * path — the address of an event is the pair {@code {lanePath, index}}, where {@code index}
 * is the event's absolute position in {@code lane.events}. That address is POSITIONAL and
 * unstable: any insert/remove shifts every later index, so every event mutation both
 * returns the resulting {@code index} + {@code cursor} and accepts the optional
 * {@code atCursor} optimistic-concurrency guard ({@link #eventAt}).
 */
public final class ClipEvents {

  private ClipEvents() {}

  // Wide enough to absorb JSON double round-tripping, far below any real event spacing
  // (MIN_GRID_SPACING is ~16ms / a 32nd note).
  private static final double MILLIS_EPSILON = 1e-3;
  private static final double BEATS_EPSILON = 1e-6;

  /**
   * The event at {@code index}, with the shared optimistic-concurrency guard: when
   * {@code atCursor} is non-null (parsed from the tool's optional {@code atCursor}
   * argument) and the event found at {@code index} is not at that cursor (within epsilon,
   * in the clip's time base), throws {@code invalid_argument} — the caller's index was
   * computed against a lane that has since changed. Every event mutation resolves its
   * target through here.
   */
  public static <T extends LXClipEvent<?>> T eventAt(LXClipLane<T> lane, int index, Cursor atCursor) {
    if (index < 0 || index >= lane.events.size()) {
      throw new Resolve.ResolveException(Resolve.Failure.NOT_FOUND,
          "No event at index " + index + " on lane " + ClipLanes.lanePath(lane)
              + " (eventCount=" + lane.events.size() + ", 0-indexed)");
    }
    T event = lane.events.get(index);
    if (atCursor != null && !cursorMatches(lane, event.getCursor(), atCursor)) {
      throw Resolve.invalidArgument(
          "Event at index " + index + " on lane " + ClipLanes.lanePath(lane)
              + " is at " + Cursors.format(lane.clip, event.getCursor())
              + ", not the expected " + Cursors.format(lane.clip, atCursor)
              + " — indices shift on insert/remove; re-read the lane and retry");
    }
    return event;
  }

  /**
   * One event: its positional address ({@code index}), its position ({@code cursor}), and
   * the type-appropriate value carried by its lane kind — null on a bare address echo (see
   * {@link #describe}) or a lane type with no value fields. {@code Payloads.event} flattens
   * the value onto the same map level, so one wire shape covers every lane type.
   */
  public record EventDetail(int index, CursorInfo cursor, EventValue value) {}

  /** The per-lane-kind value half of {@link EventDetail}. */
  public sealed interface EventValue {}

  /** Automation point: normalized value plus its interpolation curve/shape. */
  public record ParameterValue(double normalized, String curve, double shape)
      implements EventValue {}

  /** Pattern change; {@code patternPath} is null when the pattern isn't path-registered. */
  public record PatternValue(String patternLabel, String patternPath) implements EventValue {}

  /** One MIDI note-on or note-off. */
  public record MidiNoteValue(boolean noteOn, int pitch, int velocity, int midiChannel)
      implements EventValue {}

  /** Composition-span events (audio, text) occupy a window, not a point. */
  public record Span(CursorInfo length, CursorInfo end) {}

  /** Imported audio, with the window it occupies on the timeline. */
  public record AudioValue(String fileName, double sourceLengthMs, Span span)
      implements EventValue {}

  /** A timeline text note, with the window it occupies. */
  public record TextNoteValue(String note, Span span) implements EventValue {}

  /**
   * The bare event address {@code {index, cursor}}, with no value half — what a removal
   * echoes, since a removed event has no value worth reading back.
   */
  public static EventDetail describe(LXClipLane<?> lane, LXClipEvent<?> event, int index) {
    return new EventDetail(index, Cursors.describe(lane.clip, event.getCursor()), null);
  }

  private static boolean cursorMatches(LXClipLane<?> lane, Cursor actual, Cursor expected) {
    // Compare in the clip's own time base, mirroring Cursor.Operator.isEqual (which under
    // TEMPO ignores millis entirely), but with an epsilon so a JSON echo can't false-miss.
    if (lane.clip.getTimeBase() == Cursor.TimeBase.TEMPO) {
      return Math.abs((actual.getBeatCount() + actual.getBeatBasis())
          - (expected.getBeatCount() + expected.getBeatBasis())) < BEATS_EPSILON;
    }
    return Math.abs(actual.getMillis() - expected.getMillis()) < MILLIS_EPSILON;
  }

  // ============================================================
  // F4 Event read + insert primitives (branch comp-f4-events-read-insert) — implemented
  // by that family agent: paged list (from/to cursor range + offset/limit, reporting
  // eventCount/total/returned/truncated with ABSOLUTE indices), and insert
  // (LXCommand.Clip.Event.Parameter.InsertEvent), echoing resulting index + cursor.
  // Keep additions inside this band; bands are merge-isolation, do not reorder.
  // ============================================================

  /** Paging bounds for get_clip_lane, shared so the schema and validation can't drift. */
  public static final int DEFAULT_PAGE_LIMIT = 200;
  public static final int MAX_PAGE_LIMIT = 1000;

  /**
   * Paged, range-filtered read of a lane's events: {@code from}/{@code to} are an
   * inclusive cursor window (either may be null for unbounded), {@code offset} indexes
   * into the MATCHED set, and each returned event carries its ABSOLUTE index in
   * {@code lane.events} — the address every event mutation takes. The paging envelope
   * reports {@code eventCount} (lane total) / {@code total} (matched) / {@code returned} /
   * {@code truncated} so an agent can always tell a short page from a short lane.
   */
  public record EventPage(
      int eventCount,
      int total,
      int offset,
      int limit,
      int returned,
      boolean truncated,
      List<EventDetail> events) {}

  public static EventPage page(
      LXClipLane<?> lane, Cursor from, Cursor to, int offset, int limit) {
    if (offset < 0) {
      throw Resolve.invalidArgument("offset must be >= 0: " + offset);
    }
    if (limit < 1 || limit > MAX_PAGE_LIMIT) {
      throw Resolve.invalidArgument("limit must be 1-" + MAX_PAGE_LIMIT + ": " + limit);
    }
    final Cursor.Operator op = lane.clip.CursorOp();
    final List<EventDetail> events = new ArrayList<>();
    int total = 0;
    // Linear scan rather than the lane's binary-search iterators: the payload needs
    // absolute indices anyway (which the scan yields for free), and range comparison in
    // the clip's own CursorOp keeps TEMPO/ABSOLUTE semantics identical to the engine's.
    for (int index = 0; index < lane.events.size(); ++index) {
      LXClipEvent<?> event = lane.events.get(index);
      Cursor cursor = event.getCursor();
      if ((from != null && op.isBefore(cursor, from))
          || (to != null && op.isAfter(cursor, to))) {
        continue;
      }
      if (total >= offset && events.size() < limit) {
        events.add(detail(lane, event, index));
      }
      ++total;
    }
    return new EventPage(lane.events.size(), total, offset, limit, events.size(),
        offset + events.size() < total, events);
  }

  /**
   * Full event payload: the {@code {index, cursor}} base plus type-appropriate value
   * fields, so one read shape covers every lane type — automation points get
   * normalized/curve/shape, pattern changes their pattern, MIDI notes their note bytes,
   * and composition-span events (audio, text) their length/end window.
   */
  public static EventDetail detail(LXClipLane<?> lane, LXClipEvent<?> event, int index) {
    EventValue value = switch (event) {
      case ParameterClipEvent e ->
          new ParameterValue(e.getNormalized(), e.getCurve().name(), e.getShape());
      case PatternClipEvent e ->
          new PatternValue(e.getPattern().getLabel(), Resolve.canonicalPathOrNull(e.getPattern()));
      case MidiNoteClipEvent e -> new MidiNoteValue(
          e.isNoteOn(), e.midiNote.getPitch(), e.midiNote.getVelocity(), e.midiNote.getChannel());
      case AudioClipEvent e ->
          new AudioValue(e.fileName.getString(), e.getSourceLengthMs(), span(lane, e));
      case TextNoteClipEvent e -> new TextNoteValue(e.note.getString(), span(lane, e));
      default -> null;
    };
    return new EventDetail(index, Cursors.describe(lane.clip, event.getCursor()), value);
  }

  /**
   * Inserts an automation point via {@code LXCommand.Clip.Event.Parameter.InsertEvent}
   * (undoable). Clamp-echo: {@code ParameterClipEvent} constrains normalized to [0,1] and
   * Boolean lanes snap it to 0/1, so the returned payload is the shared
   * {@link #parameterEventEnvelope} of the event read back from the lane — value, cursor,
   * and resulting absolute index — never the request.
   */
  public static ParameterEventEnvelope insertParameterEvent(
      LX lx, ParameterClipLane lane, Cursor cursor, double normalized) {
    LXCommand.Clip.Event.Parameter.InsertEvent command =
        new LXCommand.Clip.Event.Parameter.InsertEvent(lane, cursor, normalized);
    Commands.perform(lx, command);
    return parameterEventEnvelope(lane, command.getEvent());
  }

  /**
   * The one echo envelope for both parameter-point mutations (add_automation_point,
   * set_automation_point): lane identity + the exact {@link #detail} event shape that
   * get_clip_lane emits, so the read tool and the two sibling mutations can never drift
   * on field names ({@code normalized}, never a divergent {@code value} key).
   */
  public record ParameterEventEnvelope(
      String lanePath,
      String parameterPath,
      String timeBase,
      EventDetail event,
      int eventCount) {}

  /** Snapshots that envelope; {@code Payloads.parameterEvent} owns its MCP field names. */
  public static ParameterEventEnvelope parameterEventEnvelope(
      ParameterClipLane lane, ParameterClipEvent event) {
    return new ParameterEventEnvelope(
        ClipLanes.lanePath(lane),
        Resolve.canonicalPathOrNull(lane.parameter),
        lane.clip.getTimeBase().name(),
        detail(lane, event, lane.events.indexOf(event)),
        lane.events.size());
  }

  private static Span span(LXClipLane<?> lane, LXCompositionEvent<?> event) {
    return new Span(
        Cursors.describe(lane.clip, event.length), Cursors.describe(lane.clip, event.end));
  }

  // ============================================================
  // F5 Event edit primitives (branch comp-f5-events-edit) — implemented by that family
  // agent: move/set values (LXCommand.Clip.Event.Parameter.MoveEvent/SetValues), curves
  // and shapes (SetCurve/SetShape/ResetShape). Targets resolve via eventAt above (with
  // the atCursor guard); echo resulting index + cursor.
  // Keep additions inside this band; bands are merge-isolation, do not reorder.
  // Types are fully qualified rather than imported so this band never touches the
  // shared import block (the F4/F5/F6 merge would conflict there).
  // ============================================================

  /**
   * The full edit surface for one existing automation point on a parameter lane, addressed
   * by {@code index} (guarded by the optional {@code atCursor}, see {@link #eventAt}).
   * Every non-null aspect applies as its own {@code LXCommand} (its own Cmd-Z step), in
   * order: position/value, then curve, then shape. {@code toCursor} and {@code toValue}
   * together ride a single {@code MoveEvent} so a point drag is one undo step;
   * {@code toValue} alone uses {@code SetValues}.
   *
   * <p>Clamp-echo: {@code moveEvent} silently constrains the cursor between the
   * neighboring events' cursors and the clip bounds (a point can never cross its
   * neighbors), and a Boolean lane snaps values to 0/1 — so the returned payload is the
   * event read back from the engine ({@code index, cursor, normalized, curve, shape}),
   * never the request.
   */
  public static ParameterEventEnvelope editParameterEvent(
      heronarts.lx.LX lx,
      heronarts.lx.clip.ParameterClipLane lane,
      int index,
      Cursor atCursor,
      Cursor toCursor,
      Double toValue,
      heronarts.lx.clip.ParameterClipEvent.Curve curve,
      Double shape,
      boolean resetShape) {

    if (toCursor == null && toValue == null && curve == null && shape == null && !resetShape) {
      throw Resolve.invalidArgument(
          "At least one edit is required: cursor, value, curve, shape, or resetShape");
    }
    if (shape != null && resetShape) {
      throw Resolve.invalidArgument("shape and resetShape are mutually exclusive");
    }

    heronarts.lx.clip.ParameterClipEvent event = eventAt(lane, index, atCursor);

    if (toCursor != null) {
      // MoveEvent carries the value alongside the cursor, so a cursor+value edit is one
      // undo step (the same gesture the UI's point drag produces).
      Commands.perform(lx, new heronarts.lx.command.LXCommand.Clip.Event.Parameter.MoveEvent(lane, event)
          .update(toCursor, (toValue != null) ? toValue : event.getNormalized()));
    } else if (toValue != null) {
      Commands.perform(lx, new heronarts.lx.command.LXCommand.Clip.Event.Parameter.SetValues(
          lane, Map.of(event, toValue)));
    }
    // Curve/shape commands capture the event's index at construction, so they must be
    // built only after any move has settled the event's position.
    if (curve != null) {
      Commands.perform(lx, new heronarts.lx.command.LXCommand.Clip.Event.Parameter.SetCurve(
          lane, event, curve));
    }
    if (shape != null) {
      Commands.perform(lx, new heronarts.lx.command.LXCommand.Clip.Event.Parameter.SetShape(
          lane, event).update(shape));
    } else if (resetShape) {
      Commands.perform(lx, new heronarts.lx.command.LXCommand.Clip.Event.Parameter.ResetShape(
          lane, event));
    }

    return parameterEventEnvelope(lane, event);
  }

  // ============================================================
  // F6 Event remove + range primitives (branch comp-f6-ranges).
  // Truncation itself is a marker op (Marker.TRUNCATE via F1's setMarker path) — these
  // range ops leave markers and clip length untouched, which is what lets remove-then-
  // truncate compose.
  // ============================================================

  /**
   * Removes the event at {@code index} via {@code LXCommand.Clip.Event.Remove} (undoable).
   * MIDI-note lanes are rejected: their events come in note-on/off pairs that a
   * single-event remove would orphan — upstream's own UI never routes them here (it uses
   * the {@code Event.Midi} vocabulary, deliberately deferred from the MCP surface).
   * Returns {@link #describe} of the removed event, captured BEFORE the removal — an
   * event that is no longer in the lane has no index to read back, so the pre-removal
   * address is the only honest echo.
   */
  public static EventDetail remove(LX lx, LXClipLane<?> lane, int index, Cursor atCursor) {
    if (lane instanceof MidiNoteClipLane) {
      throw Resolve.invalidArgument(
          "Lane " + ClipLanes.lanePath(lane) + " is a MIDI note lane; its events pair "
              + "note-on/off and removing one would orphan its partner. MIDI note editing "
              + "is not exposed yet.");
    }
    LXClipEvent<?> event = eventAt(lane, index, atCursor);
    EventDetail removed = describe(lane, event, index);
    int countBefore = lane.events.size();
    Commands.perform(lx, removeCommand(lane, event));
    if (lane.events.size() != countBefore - 1) {
      throw new IllegalStateException(
          "Event removal did not shrink lane " + ClipLanes.lanePath(lane));
    }
    return removed;
  }

  // Event.Remove insists the lane and event share T; the wildcard-typed lane from Resolve
  // can't prove that statically, but eventAt just took this event out of this very lane.
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static LXCommand removeCommand(LXClipLane<?> lane, LXClipEvent<?> event) {
    return new LXCommand.Clip.Event.Remove((LXClipLane) lane, (LXClipEvent) event);
  }

  /**
   * Deletes every event in {@code [from, to]} (inclusive at both ends) on one lane via
   * {@code LXCommand.Clip.Event.RemoveRange}. On MIDI-note lanes the upstream override
   * removes note-on/off pairs together, so no orphaning hazard here. Returns the number
   * of events removed — 0 for a range containing none, the common agent case, which must
   * be a benign success (see {@link #performRangeEdit}).
   */
  public static int removeRange(LX lx, LXClipLane<?> lane, Cursor from, Cursor to) {
    requireOrdered(lane, from, to);
    return performRangeEdit(lx, lane, new LXCommand.Clip.Event.RemoveRange(lane, from, to));
  }

  /**
   * Collapses the envelope inside {@code [from, to]} via
   * {@code LXCommand.Clip.Event.CollapseRange}: removes the interior events strictly
   * between the first and last events in the range, keeping those two as the surviving
   * boundary points (Chromatik's "Collapse Envelope"). A range holding fewer than three
   * events has no interior — returns 0, a benign success.
   */
  public static int collapseRange(LX lx, LXClipLane<?> lane, Cursor from, Cursor to) {
    requireOrdered(lane, from, to);
    return performRangeEdit(lx, lane, new LXCommand.Clip.Event.CollapseRange(lane, from, to));
  }

  private static void requireOrdered(LXClipLane<?> lane, Cursor from, Cursor to) {
    // A reversed range would silently match nothing; an agent that swapped its arguments
    // deserves a loud pointer, not a removedCount of 0.
    if (lane.clip.CursorOp().isAfter(from, to)) {
      throw Resolve.invalidArgument(
          "Range cursors are reversed: from " + Cursors.format(lane.clip, from)
              + " is after to " + Cursors.format(lane.clip, to));
    }
  }

  /**
   * RemoveRange/CollapseRange are {@code isIgnored} no-ops on an empty range, which
   * {@code Commands.perform} would misreport as an internal failure — the event-count
   * delta read back from the lane is the truth for both the success detection and the
   * returned count. Both commands remove at least one event whenever they commit, so
   * applied and (removed > 0) must agree; disagreement means the engine changed under us.
   */
  private static int performRangeEdit(LX lx, LXClipLane<?> lane, LXCommand command) {
    int countBefore = lane.events.size();
    boolean applied = Commands.applied(lx, command);
    int removed = countBefore - lane.events.size();
    if (applied != (removed > 0)) {
      throw new IllegalStateException(command.getClass().getSimpleName()
          + (applied ? " applied but removed no events" : " was ignored but events changed")
          + " on lane " + ClipLanes.lanePath(lane));
    }
    return removed;
  }
}
