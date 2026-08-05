package chromatikmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chromatikmcp.CompositionTestSupport;
import chromatikmcp.domain.ClipEvents;
import chromatikmcp.domain.ClipLanes;
import chromatikmcp.domain.Clips;
import chromatikmcp.domain.Compositions;
import chromatikmcp.domain.Cursors;

import heronarts.lx.LX;
import heronarts.lx.clip.LXComposition;
import heronarts.lx.clip.ParameterClipLane;
import heronarts.lx.clip.TextNoteClipLane;
import heronarts.lx.mixer.LXChannel;

/**
 * The MCP boundary for the composition surface: the domain returns typed results and these
 * serializers own every wire key, so this pins each shape's complete key list AND the
 * payload identity between sibling tools that emit the same named object.
 *
 * <p>Identity here is contractual, not incidental — get_clip_lane and the two automation
 * mutations are read/write views of the same event; list_clip_lanes, get_composition, and
 * every lane mutation echo the same lane. The generated tool-reference checks cover schemas
 * and descriptions, not response payloads, so nothing else would catch this drift.
 */
class PayloadsTest extends CompositionTestSupport {

  @TempDir
  Path tempDir;

  @SuppressWarnings("unchecked")
  private static Map<String, Object> ok(Result<Map<String, Object>> result) {
    return (Map<String, Object>) assertInstanceOf(Result.Ok.class, result).value();
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> list(Object value) {
    return (List<Map<String, Object>>) assertInstanceOf(List.class, value);
  }

  private static List<String> keys(Map<String, Object> map) {
    return List.copyOf(map.keySet());
  }

  /** ~100ms of silent 16-bit mono PCM — the smallest thing AudioSystem reads as WAV. */
  private File writeWav(String name) throws Exception {
    AudioFormat format = new AudioFormat(44100f, 16, 1, true, false);
    int frames = 4410;
    AudioInputStream stream = new AudioInputStream(
        new ByteArrayInputStream(new byte[frames * 2]), format, frames);
    File file = tempDir.resolve(name).toFile();
    AudioSystem.write(stream, AudioFileFormat.Type.WAVE, file);
    return file;
  }

  private static final List<String> CURSOR_KEYS =
      List.of("millis", "beatCount", "beatBasis", "formatted");

  private static final List<String> ENVELOPE_KEYS = List.of(
      "path", "label", "timeBase", "referenceBpm", "length", "loop", "loopStart", "loopEnd",
      "playStart", "playEnd", "insertMarker", "playhead", "running", "hasContent", "laneCount");

  @Test
  void cursorAndEnvelopeShapesArePinned() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);

    assertEquals(CURSOR_KEYS,
        keys(Payloads.cursor(Cursors.describe(composition, composition.getCursor()))));

    Map<String, Object> envelope = Payloads.clipEnvelope(Clips.envelope(composition));
    assertEquals(ENVELOPE_KEYS, keys(envelope));
    for (String marker : List.of(
        "length", "loopStart", "loopEnd", "playStart", "playEnd", "insertMarker", "playhead")) {
      assertEquals(CURSOR_KEYS, keys(castMap(envelope.get(marker))), marker + " is a cursor object");
    }

    // get_clip is the envelope plus exactly one key; get_composition adds exactly four.
    assertEquals(concat(ENVELOPE_KEYS, "pending"),
        keys(Payloads.clip(Clips.describe(composition))));
    assertEquals(concat(ENVELOPE_KEYS, "armed", "sync", "locatorCount", "lanes"),
        keys(Payloads.composition(Compositions.describe(lx))));
  }

  @Test
  void laneSummaryOmitsAbsentTargetsAndIsIdenticalAcrossItsFourEmitters() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXComposition composition = composition(lx);
    ParameterClipLane lane = addParameterLane(composition, channel.fader);

    Map<String, Object> summary = Payloads.laneSummary(ClipLanes.summary(lane));
    assertEquals(
        List.of("path", "index", "type", "label", "eventCount", "uiVisible", "removable",
            "parameterPath"),
        keys(summary));
    // The three target fields are mutually exclusive: a parameter lane emits neither of
    // the other two rather than emitting them null.
    assertFalse(summary.containsKey("busPath"));
    assertFalse(summary.containsKey("channelPath"));

    String lanePath = ClipLanes.lanePath(lane);
    Map<String, Object> fromList = laneWithPath(
        list(ok(new ListClipLanes().handle(lx, Map.of())).get("lanes")), lanePath);
    Map<String, Object> fromComposition = laneWithPath(
        list(ok(new GetComposition().handle(lx, Map.of())).get("lanes")), lanePath);
    Map<String, Object> fromMutation = castMap(
        ok(new SetClipLaneVisible().handle(lx, Map.of("path", lanePath, "visible", true)))
            .get("lane"));
    Map<String, Object> fromGetLane = ok(new GetClipLane().handle(lx, Map.of("path", lanePath)));

    assertEquals(summary, fromList);
    assertEquals(summary, fromComposition);
    assertEquals(summary, fromMutation);
    // get_clip_lane flattens the same summary and appends the paging envelope, so every
    // summary key must survive with the same value.
    for (Map.Entry<String, Object> entry : summary.entrySet()) {
      assertEquals(entry.getValue(), fromGetLane.get(entry.getKey()),
          "get_clip_lane drifted on " + entry.getKey());
    }
  }

  @Test
  void automationEventIsIdenticalAcrossTheReadAndBothMutations() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);
    ParameterClipLane lane = addParameterLane(composition, channel.fader);
    String lanePath = ClipLanes.lanePath(lane);

    Map<String, Object> added = ok(new AddAutomationPoint().handle(lx, Map.of(
        "lanePath", lanePath, "cursor", Map.of("millis", 2_000), "normalized", 0.5)));
    assertEquals(
        List.of("lanePath", "parameterPath", "timeBase", "index", "cursor", "normalized",
            "curve", "shape", "eventCount"),
        keys(added));

    Map<String, Object> edited = ok(new SetAutomationPoint().handle(lx, Map.of(
        "lanePath", lanePath, "index", 0, "normalized", 0.25)));
    assertEquals(keys(added), keys(edited), "the two mutations share one envelope");

    // The read tool's event entry is the same object shape the mutations flatten.
    Map<String, Object> read =
        list(ok(new GetClipLane().handle(lx, Map.of("path", lanePath))).get("events")).get(0);
    assertEquals(List.of("index", "cursor", "normalized", "curve", "shape"), keys(read));
    for (String key : read.keySet()) {
      assertEquals(read.get(key), edited.get(key), "set_automation_point drifted on " + key);
    }
    // ... and a removal echoes only the address half, never a bare divergent shape.
    Map<String, Object> removed = castMap(ok(new RemoveAutomationPoint().handle(lx, Map.of(
        "lanePath", lanePath, "index", 0))).get("removed"));
    assertEquals(List.of("index", "cursor"), keys(removed));
    assertEquals(read.get("cursor"), removed.get("cursor"));
  }

  @Test
  void textNoteEventIsIdenticalAcrossItsThreeEmitters() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);
    TextNoteClipLane lane = Compositions.addTextNoteLane(lx, "Cues");
    String lanePath = ClipLanes.lanePath(lane);

    Map<String, Object> added = ok(new AddClipNote().handle(lx, Map.of(
        "lanePath", lanePath, "note", "drop", "cursor", Map.of("millis", 1_000))));
    Map<String, Object> edited = ok(new SetClipNote().handle(lx, Map.of(
        "lanePath", lanePath, "index", 0, "note", "build")));
    Map<String, Object> read =
        list(ok(new GetClipLane().handle(lx, Map.of("path", lanePath))).get("events")).get(0);

    assertEquals(List.of("index", "cursor", "note", "length", "end"), keys(read));
    for (String key : read.keySet()) {
      assertEquals(read.get(key), edited.get(key), "set_clip_note drifted on " + key);
    }
    for (String key : read.keySet()) {
      assertTrue(added.containsKey(key), "add_clip_note is missing " + key);
    }
    assertEquals(CURSOR_KEYS, keys(castMap(read.get("length"))));
    assertEquals(CURSOR_KEYS, keys(castMap(read.get("end"))));
  }

  @Test
  void audioEventExtendsTheSharedEventShapeAdditively() throws Exception {
    LX lx = newHeadlessLx();
    Map<String, Object> payload = ok(new AddAudioLane().handle(
        lx, Map.of("file", writeWav("tone.wav").getAbsolutePath())));

    Map<String, Object> event = castMap(payload.get("event"));
    // Every get_clip_lane audio field, in the same order, plus filePath appended.
    assertEquals(
        List.of("index", "cursor", "fileName", "sourceLengthMs", "length", "end", "filePath"),
        keys(event));

    String lanePath = (String) castMap(payload.get("lane")).get("path");
    Map<String, Object> read =
        list(ok(new GetClipLane().handle(lx, Map.of("path", lanePath))).get("events")).get(0);
    for (String key : read.keySet()) {
      assertEquals(read.get(key), event.get(key), "add_audio_lane drifted on " + key);
    }
  }

  @Test
  void locatorSummaryIsIdenticalAcrossItsFiveEmitters() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);

    List<String> locatorKeys = List.of("path", "index", "label", "cursor");

    // add_locator extends the summary additively with the resulting locatorCount.
    Map<String, Object> added = ok(new AddLocator().handle(
        lx, Map.of("cursor", Map.of("millis", 2_000), "label", "Intro")));
    assertEquals(concat(locatorKeys, "locatorCount"), keys(added));
    assertEquals(CURSOR_KEYS, keys(castMap(added.get("cursor"))));

    Map<String, Object> listed =
        list(ok(new ListLocators().handle(lx, Map.of())).get("locators")).get(0);
    assertEquals(locatorKeys, keys(listed));
    for (String key : locatorKeys) {
      assertEquals(added.get(key), listed.get(key), "add_locator drifted on " + key);
    }

    Map<String, Object> moved = ok(new MoveLocator().handle(
        lx, Map.of("index", 1, "cursor", Map.of("millis", 3_000))));
    assertEquals(locatorKeys, keys(moved));

    Map<String, Object> went =
        castMap(ok(new GoLocator().handle(lx, Map.of("index", 1))).get("locator"));
    assertEquals(locatorKeys, keys(went));

    // remove_locator alone drops the summary's path — positional, so it would address
    // whichever locator slid into the freed slot — and keeps every other field identical.
    Map<String, Object> removed =
        castMap(ok(new RemoveLocator().handle(lx, Map.of("index", 1))).get("removed"));
    assertEquals(List.of("index", "label", "cursor"), keys(removed));

    // The list envelope wraps those entries with composition identity.
    assertEquals(List.of("path", "timeBase", "locatorCount", "locators"),
        keys(Payloads.locatorList(Compositions.listLocators(lx))));
  }

  @Test
  void eventPageEnvelopeShapeIsPinned() {
    LX lx = newHeadlessLx();
    LXChannel channel = addChannelWithPattern(lx);
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);
    ParameterClipLane lane = addParameterLane(composition, channel.fader);

    assertEquals(
        List.of("eventCount", "total", "offset", "limit", "returned", "truncated", "events"),
        keys(Payloads.eventPage(ClipEvents.page(lane, null, null, 0, 200))));
  }

  private static Map<String, Object> laneWithPath(List<Map<String, Object>> lanes, String path) {
    return lanes.stream()
        .filter(lane -> path.equals(lane.get("path")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no lane at " + path));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castMap(Object value) {
    return (Map<String, Object>) assertInstanceOf(Map.class, value);
  }

  private static List<String> concat(List<String> base, String... extra) {
    return java.util.stream.Stream.concat(base.stream(), java.util.Arrays.stream(extra)).toList();
  }
}
