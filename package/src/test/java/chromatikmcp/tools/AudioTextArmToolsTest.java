package chromatikmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Map;
import java.nio.file.Path;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.Gson;

import chromatikmcp.CompositionTestSupport;
import chromatikmcp.domain.ClipLanes;
import chromatikmcp.domain.Resolve;

import heronarts.lx.LX;
import heronarts.lx.clip.LXComposition;
import heronarts.lx.clip.TextNoteClipLane;

/**
 * F7 tools, handler-level (registry coverage arrives with integration): audio lane,
 * notes lane, note events, and the timeline arm. Every mutation payload is the state
 * read back from the engine — the clamp tests pin that.
 */
class AudioTextArmToolsTest extends CompositionTestSupport {

  @TempDir
  Path tempDir;

  @SuppressWarnings("unchecked")
  private static Map<String, Object> ok(Result<Map<String, Object>> result) {
    Map<String, Object> payload =
        (Map<String, Object>) assertInstanceOf(Result.Ok.class, result).value();
    // Real example payloads for the tool manifest (surefire captures stdout per-test).
    System.out.println("PAYLOAD " + new Gson().toJson(payload));
    return payload;
  }

  private File writeWav(String name) throws Exception {
    AudioFormat format = new AudioFormat(44100f, 16, 1, true, false);
    int frames = 4410;
    AudioInputStream stream = new AudioInputStream(
        new ByteArrayInputStream(new byte[frames * 2]), format, frames);
    File file = tempDir.resolve(name).toFile();
    AudioSystem.write(stream, AudioFileFormat.Type.WAVE, file);
    return file;
  }

  @Test
  void addAudioLaneReturnsLaneEventAndGrownCompositionLength() throws Exception {
    LX lx = newHeadlessLx();
    File wav = writeWav("tone.wav");

    Map<String, Object> payload = ok(new AddAudioLane().handle(
        lx, Map.of("file", wav.getAbsolutePath())));

    // The shared lane-creation envelope, as in add_clip_lane/add_notes_lane.
    assertEquals("/lx/timeline/composition", payload.get("clipPath"));
    assertEquals(composition(lx).lanes.size(), payload.get("laneCount"));
    Map<?, ?> lane = assertInstanceOf(Map.class, payload.get("lane"));
    assertEquals("audio", lane.get("type"));
    assertEquals(0, lane.get("index"));
    assertEquals("/lx/timeline/composition/lane/1", lane.get("path"));

    Map<?, ?> event = assertInstanceOf(Map.class, payload.get("event"));
    assertEquals("tone.wav", event.get("fileName"));
    assertEquals(wav.getAbsolutePath(), event.get("filePath"));
    double eventMillis = (Double) ((Map<?, ?>) event.get("length")).get("millis");
    assertTrue(eventMillis > 99.0, "audio length ~100ms");

    // The echoed composition length is the read-back grown value, not a request echo.
    Map<?, ?> length = assertInstanceOf(Map.class, payload.get("compositionLength"));
    assertTrue((Double) length.get("millis") >= eventMillis - 1e-3);
  }

  @Test
  void addAudioLaneOnAnUnreadableFileIsTypedInvalidArgument() {
    LX lx = newHeadlessLx();
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> new AddAudioLane().handle(
            lx, Map.of("file", tempDir.resolve("nope.wav").toString())));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
  }

  @Test
  void addNotesLaneReturnsTheLabeledLaneSummary() {
    LX lx = newHeadlessLx();
    Map<String, Object> payload = ok(new AddNotesLane().handle(
        lx, Map.of("label", "Sections")));
    // The shared lane-creation envelope {clipPath, lane, laneCount}.
    assertEquals("/lx/timeline/composition", payload.get("clipPath"));
    assertEquals(composition(lx).lanes.size(), payload.get("laneCount"));
    Map<?, ?> lane = assertInstanceOf(Map.class, payload.get("lane"));
    assertEquals("textNote", lane.get("type"));
    assertEquals("Sections", lane.get("label"));
    assertEquals(true, lane.get("removable"));
    assertEquals(0, lane.get("eventCount"));
    // The advertised path resolves back to the lane.
    assertInstanceOf(TextNoteClipLane.class,
        Resolve.component(lx, (String) lane.get("path")));
  }

  @Test
  void addClipNoteEchoesTheInsertedEventAndSortedIndex() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    TextNoteClipLane lane = composition.addTextNoteLane();
    String lanePath = ClipLanes.lanePath(lane);

    ok(new AddClipNote().handle(lx, Map.of(
        "lanePath", lanePath, "note", "chorus", "cursor", Map.of("beatCount", 16))));
    Map<String, Object> payload = ok(new AddClipNote().handle(lx, Map.of(
        "lanePath", lanePath,
        "note", "intro",
        "cursor", Map.of("bars", 1),
        "length", Map.of("beatCount", 4))));

    assertEquals(lanePath, payload.get("lanePath"));
    // Earlier cursor, later insert: cursor-sorted to index 0 — indices are positional.
    assertEquals(0, payload.get("index"));
    assertEquals("intro", payload.get("note"));
    assertEquals(0.0, ((Map<?, ?>) payload.get("cursor")).get("millis"));
    assertEquals(4, ((Map<?, ?>) payload.get("length")).get("beatCount"));
    assertEquals(4, ((Map<?, ?>) payload.get("end")).get("beatCount"));
    assertEquals(2, lane.events.size());
  }

  @Test
  void addClipNoteOnANonNotesLaneIsTypedTypeMismatch() {
    LX lx = newHeadlessLx();
    // Lane 1 on a bare composition is the master bus lane, not a textNote lane.
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> new AddClipNote().handle(lx, Map.of(
            "lanePath", "/lx/timeline/composition/lane/1",
            "note", "x", "cursor", Map.of("millis", 0))));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
  }

  @Test
  void setClipNoteClampsTheMoveAndEchoesTheReadBackCursor() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);
    TextNoteClipLane lane = composition.addTextNoteLane();
    String lanePath = ClipLanes.lanePath(lane);
    new AddClipNote().handle(lx, Map.of(
        "lanePath", lanePath, "note", "a", "cursor", Map.of("millis", 1_000)));
    new AddClipNote().handle(lx, Map.of(
        "lanePath", lanePath, "note", "b", "cursor", Map.of("millis", 5_000)));

    // Requested 8000ms, but the next event sits at 5000ms: the echo is the clamped
    // engine read-back, never the request.
    Map<String, Object> payload = ok(new SetClipNote().handle(lx, Map.of(
        "lanePath", lanePath, "index", 0,
        "atCursor", Map.of("millis", 1_000),
        "note", "a-moved",
        "cursor", Map.of("millis", 8_000))));
    assertEquals(5_000.0, ((Map<?, ?>) payload.get("cursor")).get("millis"));
    assertEquals("a-moved", payload.get("note"));
    assertEquals(0, payload.get("index"));
  }

  @Test
  void setClipNoteRequiresAtLeastOneEditAndHonorsTheGuard() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    TextNoteClipLane lane = composition.addTextNoteLane();
    String lanePath = ClipLanes.lanePath(lane);
    new AddClipNote().handle(lx, Map.of(
        "lanePath", lanePath, "note", "a", "cursor", Map.of("millis", 1_000)));

    assertThrows(Resolve.ResolveException.class, () -> new SetClipNote().handle(
        lx, Map.of("lanePath", lanePath, "index", 0)));
    // Stale atCursor rejects before any edit is applied.
    assertThrows(Resolve.ResolveException.class, () -> new SetClipNote().handle(
        lx, Map.of("lanePath", lanePath, "index", 0,
            "atCursor", Map.of("millis", 2_000), "note", "x")));
    assertEquals("a", lane.events.get(0).note.getString());
    // Out-of-range index is not_found, not an internal error.
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> new SetClipNote().handle(
            lx, Map.of("lanePath", lanePath, "index", 5, "note", "x")));
    assertEquals(Resolve.Failure.NOT_FOUND, e.failure);
  }

  @Test
  void setCompositionArmReadsBackArmAndRunning() {
    LX lx = newHeadlessLx();
    Map<String, Object> payload = ok(new SetCompositionArm().handle(
        lx, Map.of("armed", true)));
    assertEquals(true, payload.get("armed"));
    assertTrue(lx.engine.timeline.arm.isOn());
    // Upstream armChanged: arming a stopped composition launches it into recording.
    assertEquals(true, payload.get("running"));

    // Disarming does NOT stop the running composition — the payload surfaces that.
    payload = ok(new SetCompositionArm().handle(lx, Map.of("armed", false)));
    assertEquals(false, payload.get("armed"));
    assertEquals(true, payload.get("running"));

    assertThrows(Resolve.ResolveException.class,
        () -> new SetCompositionArm().handle(lx, Map.of("armed", "yes")));
  }
}
