package chromatikmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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
import heronarts.lx.clip.PatternClipEvent;
import heronarts.lx.clip.PatternClipLane;
import heronarts.lx.mixer.LXChannel;

/**
 * F4: get_clip_lane (paged event read) + add_automation_point (parameter-event insert),
 * handler-level. Pins the paging envelope (eventCount/total/returned/truncated with
 * ABSOLUTE indices), the clamp-echo rule on inserted values, and — because every event
 * mutation's description warns about it — that an insert really does shift later indices.
 */
class EventsReadInsertToolsTest extends CompositionTestSupport {

  @SuppressWarnings("unchecked")
  private static Map<String, Object> ok(Result<Map<String, Object>> result) {
    return (Map<String, Object>) assertInstanceOf(Result.Ok.class, result).value();
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> events(Map<String, Object> payload) {
    return (List<Map<String, Object>>) assertInstanceOf(List.class, payload.get("events"));
  }

  private static int beatCount(Map<String, Object> event) {
    Map<?, ?> cursor = assertInstanceOf(Map.class, event.get("cursor"));
    return ((Number) cursor.get("beatCount")).intValue();
  }

  private static Map<String, Object> insert(
      LX lx, String lanePath, int beatCount, double normalized) {
    return ok(new AddAutomationPoint().handle(lx, Map.of(
        "lanePath", lanePath,
        "cursor", Map.of("beatCount", beatCount),
        "normalized", normalized)));
  }

  private static Map<String, Object> read(LX lx, Map<String, Object> args) {
    return ok(new GetClipLane().handle(lx, args));
  }

  /** Composition + channel with a fader automation lane, the shared starting state. */
  private static ParameterClipLane faderLane(LX lx) {
    LXChannel channel = addChannelWithPattern(lx);
    return addParameterLane(composition(lx), channel.fader);
  }

  @Test
  void emptyLaneReadsAnEmptyPageWithTheLaneSummary() {
    LX lx = newHeadlessLx();
    ParameterClipLane lane = faderLane(lx);
    String lanePath = ClipLanes.lanePath(lane);

    Map<String, Object> payload = read(lx, Map.of("path", lanePath));
    assertEquals(lanePath, payload.get("path"));
    assertEquals("/lx/timeline/composition", payload.get("clipPath"));
    assertEquals("parameter", payload.get("type"));
    assertEquals(lane.clip.getTimeBase().name(), payload.get("timeBase"));
    assertEquals(0, payload.get("eventCount"));
    assertEquals(0, payload.get("total"));
    assertEquals(0, payload.get("returned"));
    assertEquals(false, payload.get("truncated"));
    assertTrue(events(payload).isEmpty());
  }

  @Test
  void insertEchoesEngineStateAndIsUndoable() {
    LX lx = newHeadlessLx();
    ParameterClipLane lane = faderLane(lx);
    String lanePath = ClipLanes.lanePath(lane);

    Map<String, Object> payload = insert(lx, lanePath, 4, 0.5);
    assertEquals(lanePath, payload.get("lanePath"));
    // The insert echo is the same envelope set_automation_point returns — the shared
    // formatter is what keeps the sibling mutations' payload shapes identical.
    assertEquals(lane.parameter.getCanonicalPath(), payload.get("parameterPath"));
    assertEquals(lane.clip.getTimeBase().name(), payload.get("timeBase"));
    assertEquals(0, payload.get("index"));
    assertEquals(4, beatCount(payload));
    assertEquals(0.5, payload.get("normalized"));
    assertEquals("POWER_EASE", payload.get("curve"));
    assertEquals(0.0, payload.get("shape"));
    assertEquals(1, payload.get("eventCount"));
    assertEquals(1, lane.events.size());
    assertCursorEqual(lane.clip, lane.clip.constructTempoCursor(4, 0),
        lane.events.get(0).getCursor());

    lx.command.undo();
    assertEquals(0, lane.events.size(), "undo removes the inserted point");
    lx.command.redo();
    assertEquals(1, lane.events.size(), "redo restores it");
    assertCursorEqual(lane.clip, lane.clip.constructTempoCursor(4, 0),
        lane.events.get(0).getCursor());
  }

  @Test
  void normalizedIsClampedByTheEngineAndTheEchoShowsIt() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXComposition composition = composition(lx);

    ParameterClipLane fader = addParameterLane(composition, channel.fader);
    Map<String, Object> clamped = insert(lx, ClipLanes.lanePath(fader), 0, 1.5);
    assertEquals(1.0, clamped.get("normalized"), "constrained to [0,1], echo is the truth");

    // Boolean lanes snap rather than clamp — same echo rule, different engine behavior.
    ParameterClipLane enabled = addParameterLane(composition, channel.enabled);
    Map<String, Object> snapped = insert(lx, ClipLanes.lanePath(enabled), 0, 0.4);
    assertEquals(0.0, snapped.get("normalized"), "boolean lane snaps 0.4 to 0");
  }

  @Test
  void barsSugarCursorFormLandsOnTheEchoedBeat() {
    LX lx = newHeadlessLx();
    ParameterClipLane lane = faderLane(lx);

    Map<String, Object> payload = ok(new AddAutomationPoint().handle(lx, Map.of(
        "lanePath", ClipLanes.lanePath(lane),
        "cursor", Map.of("bars", 2, "beats", 1),
        "normalized", 0.25)));
    // Default 4/4: bar 2 beat 1 is beatCount 4 — echoed from the event, not the request.
    assertEquals(4, beatCount(payload));
  }

  @Test
  void fromToWindowIsInclusiveAndIndicesStayAbsolute() {
    LX lx = newHeadlessLx();
    String lanePath = ClipLanes.lanePath(faderLane(lx));
    for (int beat = 0; beat < 10; ++beat) {
      insert(lx, lanePath, beat, beat / 10.0);
    }

    Map<String, Object> payload = read(lx, Map.of("path", lanePath,
        "from", Map.of("beatCount", 2), "to", Map.of("beatCount", 5)));
    assertEquals(10, payload.get("eventCount"), "eventCount is the lane total");
    assertEquals(4, payload.get("total"), "total is the matched count, inclusive both ends");
    assertEquals(4, payload.get("returned"));
    assertEquals(false, payload.get("truncated"));
    List<Map<String, Object>> events = events(payload);
    for (int i = 0; i < events.size(); ++i) {
      assertEquals(i + 2, events.get(i).get("index"), "absolute index in lane.events");
      assertEquals(i + 2, beatCount(events.get(i)));
    }
  }

  @Test
  void paginationTruncatesAndOffsetsWithinTheMatchedSet() {
    LX lx = newHeadlessLx();
    String lanePath = ClipLanes.lanePath(faderLane(lx));
    for (int beat = 0; beat < 10; ++beat) {
      insert(lx, lanePath, beat, beat / 10.0);
    }

    Map<String, Object> firstPage = read(lx, Map.of("path", lanePath, "limit", 3));
    assertEquals(10, firstPage.get("total"));
    assertEquals(3, firstPage.get("returned"));
    assertEquals(true, firstPage.get("truncated"));
    assertEquals(0, events(firstPage).get(0).get("index"));

    Map<String, Object> lastPage = read(lx, Map.of("path", lanePath, "offset", 8));
    assertEquals(2, lastPage.get("returned"));
    assertEquals(false, lastPage.get("truncated"), "final page is not truncated");
    assertEquals(8, events(lastPage).get(0).get("index"));

    Map<String, Object> pastEnd = read(lx, Map.of("path", lanePath, "offset", 20));
    assertEquals(0, pastEnd.get("returned"));
    assertEquals(false, pastEnd.get("truncated"));

    // Offset applies within the from/to-matched set, not the whole lane.
    Map<String, Object> windowed = read(lx, Map.of("path", lanePath,
        "from", Map.of("beatCount", 2), "to", Map.of("beatCount", 5),
        "offset", 1, "limit", 2));
    assertEquals(4, windowed.get("total"));
    assertEquals(2, windowed.get("returned"));
    assertEquals(true, windowed.get("truncated"));
    assertEquals(3, events(windowed).get(0).get("index"));
    assertEquals(4, events(windowed).get(1).get("index"));
  }

  @Test
  void pagingArgumentsAreValidated() {
    LX lx = newHeadlessLx();
    String lanePath = ClipLanes.lanePath(faderLane(lx));
    for (Map<String, Object> args : List.of(
        Map.<String, Object>of("path", lanePath, "limit", 0),
        Map.<String, Object>of("path", lanePath, "limit", 1001),
        Map.<String, Object>of("path", lanePath, "offset", -1))) {
      Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
          () -> new GetClipLane().handle(lx, args), args.toString());
      assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    }
  }

  @Test
  void insertShiftsLaterIndicesSoTheDescriptionWarningIsReal() {
    LX lx = newHeadlessLx();
    String lanePath = ClipLanes.lanePath(faderLane(lx));
    insert(lx, lanePath, 4, 0.1);
    Map<String, Object> late = insert(lx, lanePath, 8, 0.9);
    assertEquals(1, late.get("index"));

    Map<String, Object> middle = insert(lx, lanePath, 6, 0.5);
    assertEquals(1, middle.get("index"), "new point lands between the two");

    List<Map<String, Object>> events = events(read(lx, Map.of("path", lanePath)));
    assertEquals(3, events.size());
    assertEquals(8, beatCount(events.get(2)), "the beat-8 point moved from index 1 to 2");
  }

  @Test
  void nonParameterLanesAreRejectedWithTheLaneType() {
    LX lx = newHeadlessLx();
    addChannelWithPattern(lx);
    LXComposition composition = composition(lx);
    LXClipLane<?> midiLane = composition.lanes.stream()
        .filter(MidiNoteClipLane.class::isInstance).findFirst().orElseThrow();

    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> new AddAutomationPoint().handle(lx, Map.of(
            "lanePath", ClipLanes.lanePath(midiLane),
            "cursor", Map.of("beatCount", 0),
            "normalized", 0.5)));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
    assertTrue(e.getMessage().contains("midiNote"), e.getMessage());
  }

  @Test
  void patternLaneEventsCarryTheirPatternPayload() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXComposition composition = composition(lx);
    PatternClipLane lane = composition.lanes.stream()
        .filter(PatternClipLane.class::isInstance).map(PatternClipLane.class::cast)
        .findFirst().orElseThrow();
    lane.insertEvent(new PatternClipEvent(lane, composition.constructTempoCursor(2, 0), 0));

    Map<String, Object> payload = read(lx, Map.of("path", ClipLanes.lanePath(lane)));
    assertEquals("pattern", payload.get("type"));
    List<Map<String, Object>> events = events(payload);
    assertEquals(1, events.size());
    assertEquals(channel.patterns.get(0).getLabel(), events.get(0).get("patternLabel"));
    assertEquals(Resolve.canonicalPath(channel.patterns.get(0)),
        events.get(0).get("patternPath"));
    assertNull(events.get(0).get("normalized"), "no parameter fields on a pattern event");
  }

  @Test
  void missingLaneIsTypedNotFound() {
    LX lx = newHeadlessLx();
    addChannelWithPattern(lx);
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> new GetClipLane().handle(lx,
            Map.of("path", "/lx/timeline/composition/lane/99")));
    assertEquals(Resolve.Failure.NOT_FOUND, e.failure);
  }
}
