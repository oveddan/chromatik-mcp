package chromatikmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import chromatikmcp.CompositionTestSupport;
import chromatikmcp.domain.Resolve;

import heronarts.lx.LX;
import heronarts.lx.clip.LXClip;
import heronarts.lx.clip.LXComposition;
import heronarts.lx.mixer.LXChannel;

/**
 * The first two composition tools, handler-level: these payload shapes are the pattern
 * every composition family copies (envelope + cursor objects + lane summaries).
 */
class CompositionToolsTest extends CompositionTestSupport {

  @SuppressWarnings("unchecked")
  private static Map<String, Object> ok(Result<Map<String, Object>> result) {
    return (Map<String, Object>) assertInstanceOf(Result.Ok.class, result).value();
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> lanes(Map<String, Object> payload, String key) {
    return (List<Map<String, Object>>) assertInstanceOf(List.class, payload.get(key));
  }

  @Test
  void getCompositionEmitsTheEnvelopeArmStateAndLaneSummaries() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);
    addParameterLane(composition, channel.fader);
    composition.addLocator(composition.constructAbsoluteCursor(5_000));

    Map<String, Object> payload = ok(new GetComposition().handle(lx, Map.of()));

    assertEquals("/lx/timeline/composition", payload.get("path"));
    assertEquals(composition.getTimeBase().name(), payload.get("timeBase"));
    assertEquals(false, payload.get("armed"));
    assertEquals(false, payload.get("sync"));
    assertEquals(1, payload.get("locatorCount"));
    assertEquals(false, payload.get("running"));
    assertEquals(true, payload.get("hasContent"));

    // Every marker is the full cursor object; length reflects the playEnd growth.
    for (String marker : new String[] {
        "length", "loopStart", "loopEnd", "playStart", "playEnd", "insertMarker", "playhead" }) {
      Map<?, ?> cursor = assertInstanceOf(Map.class, payload.get(marker), marker);
      assertTrue(cursor.containsKey("millis") && cursor.containsKey("beatCount")
          && cursor.containsKey("beatBasis") && cursor.containsKey("formatted"), marker);
    }
    assertEquals(10_000.0, ((Map<?, ?>) payload.get("length")).get("millis"));

    List<Map<String, Object>> lanes = lanes(payload, "lanes");
    assertEquals(composition.lanes.size(), lanes.size());
    assertEquals(payload.get("laneCount"), lanes.size());
  }

  @Test
  void listClipLanesDefaultsToTheCompositionAndRoundTrips() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXComposition composition = composition(lx);
    addParameterLane(composition, channel.fader);

    Map<String, Object> payload = ok(new ListClipLanes().handle(lx, Map.of()));
    assertEquals("/lx/timeline/composition", payload.get("clipPath"));
    assertEquals(composition.lanes.size(), payload.get("laneCount"));

    List<Map<String, Object>> lanes = lanes(payload, "lanes");
    boolean sawParameterLane = false;
    boolean sawBusLane = false;
    for (int i = 0; i < lanes.size(); ++i) {
      Map<String, Object> lane = lanes.get(i);
      assertEquals(i, lane.get("index"));
      assertNotNull(lane.get("type"));
      assertNotNull(lane.get("eventCount"));
      assertNotNull(lane.get("uiVisible"));
      // The advertised path is the address: it must resolve back to the same lane.
      String path = assertInstanceOf(String.class, lane.get("path"));
      assertEquals(composition.lanes.get(i), Resolve.component(lx, path));
      if ("parameter".equals(lane.get("type"))) {
        sawParameterLane = true;
        assertEquals(true, lane.get("removable"));
        assertEquals(Resolve.canonicalPath(channel.fader), lane.get("parameterPath"));
      }
      if ("bus".equals(lane.get("type"))) {
        sawBusLane = true;
        // Auto-managed: removing it corrupts the composition (risk 6).
        assertEquals(false, lane.get("removable"));
      }
    }
    assertTrue(sawParameterLane, "parameter lane present with its target path");
    assertTrue(sawBusLane, "bus lane present and marked non-removable");
  }

  @Test
  void listClipLanesAcceptsAGridClipPath() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXClip clip = channel.addClip(0);

    Map<String, Object> payload = ok(new ListClipLanes().handle(
        lx, Map.of("path", "/lx/mixer/channel/1/clip/1")));
    assertEquals("/lx/mixer/channel/1/clip/1", payload.get("clipPath"));

    // A grid channel clip's permanent MIDI/pattern lanes are non-removable, unlike the
    // same lane types on the composition.
    List<Map<String, Object>> lanes = lanes(payload, "lanes");
    assertEquals(clip.lanes.size(), lanes.size());
    for (Map<String, Object> lane : lanes) {
      if ("midiNote".equals(lane.get("type")) || "pattern".equals(lane.get("type"))) {
        assertEquals(false, lane.get("removable"));
      }
    }
  }

  @Test
  void listClipLanesOnAMissingClipIsTypedNotFound() {
    LX lx = newHeadlessLx();
    addChannelWithPattern(lx);
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> new ListClipLanes().handle(lx, Map.of("path", "/lx/mixer/channel/1/clip/9")));
    assertEquals(Resolve.Failure.NOT_FOUND, e.failure);
    assertFalse(e.getMessage().isEmpty());
  }
}
