package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import chromatikmcp.CompositionTestSupport;

import heronarts.lx.LX;
import heronarts.lx.clip.AudioClipEvent;
import heronarts.lx.clip.AudioClipLane;
import heronarts.lx.clip.Cursor;
import heronarts.lx.clip.LXComposition;
import heronarts.lx.clip.TextNoteClipEvent;
import heronarts.lx.clip.TextNoteClipLane;

/**
 * F7 domain primitives: audio lanes (command-backed, do-undo), text-note lanes
 * (command-backed, do-undo), note events (direct edits — clamp-echo is the contract),
 * and the timeline arm (direct field write).
 */
class CompositionAudioTextArmTest extends CompositionTestSupport {

  @TempDir
  Path tempDir;

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

  @Test
  void addAudioLaneLoadsTheFileGrowsTheCompositionAndUndoRemovesIt() throws Exception {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    int lanesBefore = composition.lanes.size();
    assertFalse(composition.hasContent());

    AudioClipLane lane = Compositions.addAudioLane(lx, writeWav("tone.wav"));

    // Audio lanes land at the top of the lane list.
    assertEquals(0, lane.getIndex());
    assertEquals(lanesBefore + 1, composition.lanes.size());
    assertEquals(1, lane.events.size());
    AudioClipEvent event = lane.events.get(0);
    assertEquals("tone.wav", event.fileName.getString());
    // 4410 frames at 44.1kHz = 100ms; import grew the composition to at least that and
    // enabled the timeline on the previously empty composition.
    assertTrue(event.length.getMillis() > 99.0, "audio length ~100ms");
    assertTrue(composition.length.cursor.getMillis() >= event.length.getMillis() - 1e-3);
    assertTrue(composition.hasContent());

    lx.command.undo();
    assertFalse(composition.lanes.contains(lane));
    assertEquals(lanesBefore, composition.lanes.size());
  }

  @Test
  void addAudioLaneRejectsMissingAndNonAudioFilesWithoutCreatingALane() throws Exception {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    int lanesBefore = composition.lanes.size();

    Resolve.ResolveException missing = assertThrows(Resolve.ResolveException.class,
        () -> Compositions.addAudioLane(lx, tempDir.resolve("nope.wav").toFile()));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, missing.failure);

    // A .wav extension on non-audio bytes: upstream setFile would only LOG the failure
    // and leave a zero-length event, which is why the primitive pre-validates.
    Path fake = tempDir.resolve("fake.wav");
    Files.write(fake, "not audio".getBytes());
    Resolve.ResolveException unsupported = assertThrows(Resolve.ResolveException.class,
        () -> Compositions.addAudioLane(lx, fake.toFile()));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, unsupported.failure);

    assertEquals(lanesBefore, composition.lanes.size());
  }

  @Test
  void addTextNoteLaneAppendsOptionallyRenamesAndUndoRemovesIt() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    int lanesBefore = composition.lanes.size();

    TextNoteClipLane lane = Compositions.addTextNoteLane(lx, "Sections");
    assertEquals("Sections", lane.getLabel());
    assertEquals(composition.lanes.size() - 1, lane.getIndex());

    lx.command.undo();
    assertFalse(composition.lanes.contains(lane));
    assertEquals(lanesBefore, composition.lanes.size());

    // Null label keeps the upstream default.
    assertEquals("Notes", Compositions.addTextNoteLane(lx, null).getLabel());
  }

  @Test
  void addNoteSetsTheTextUpstreamDropsAndKeepsEventsSorted() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    TextNoteClipLane lane = composition.addTextNoteLane();

    TextNoteClipEvent chorus = Compositions.addNote(lane, "chorus",
        composition.constructAbsoluteCursor(5_000), null);
    TextNoteClipEvent intro = Compositions.addNote(lane, "intro",
        composition.constructAbsoluteCursor(1_000),
        composition.constructAbsoluteCursor(2_000));

    // Insert is cursor-sorted: the later-added earlier note lands at index 0. Upstream
    // addEvent ignores its note argument — the primitive setting it is the point.
    assertEquals(0, lane.events.indexOf(intro));
    assertEquals(1, lane.events.indexOf(chorus));
    assertEquals("intro", lane.events.get(0).note.getString());
    assertCursorEqual(composition, composition.constructAbsoluteCursor(3_000), intro.end);
  }

  @Test
  void setNoteClampsMovesBetweenNeighborsAndFloorsLength() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);
    TextNoteClipLane lane = composition.addTextNoteLane();
    TextNoteClipEvent first = Compositions.addNote(lane, "a",
        composition.constructAbsoluteCursor(1_000), null);
    Compositions.addNote(lane, "b", composition.constructAbsoluteCursor(5_000), null);

    // Move past the next event clamps to its cursor — the read-back event is the echo
    // source, never the requested cursor.
    Compositions.setNote(lane, 0, null, null,
        composition.constructAbsoluteCursor(8_000), null);
    assertCursorEqual(composition, composition.constructAbsoluteCursor(5_000),
        first.getCursor());

    // Zero length floors at the upstream minimum event length (Cursor.MIN_LOOP).
    Compositions.setNote(lane, 0, null, null, null, composition.constructAbsoluteCursor(0));
    assertTrue(composition.CursorOp().isAfter(first.length, Cursor.ZERO));

    // Text-only edit leaves cursors alone.
    Compositions.setNote(lane, 0, null, "renamed", null, null);
    assertEquals("renamed", first.note.getString());
  }

  @Test
  void setNoteMoveOnlyEditKeepsEndEqualToCursorPlusLength() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    enableTimeline(composition, 10_000);
    TextNoteClipLane lane = composition.addTextNoteLane();
    TextNoteClipEvent event = Compositions.addNote(lane, "a",
        composition.constructAbsoluteCursor(1_000),
        composition.constructAbsoluteCursor(2_000));

    // Move-only edit: upstream moveEvent writes the cursor field directly, bypassing
    // refreshEnd() — without the primitive's re-derivation, end stays at the OLD
    // cursor + length and every subsequent read echoes an inconsistent span.
    Compositions.setNote(lane, 0, null, null,
        composition.constructAbsoluteCursor(3_000), null);

    assertCursorEqual(composition,
        composition.constructAbsoluteCursor(3_000), event.getCursor());
    assertCursorEqual(composition, event.getCursor().add(event.length), event.end);
    assertCursorEqual(composition, composition.constructAbsoluteCursor(5_000), event.end);
  }

  @Test
  void setNoteAtCursorGuardRejectsAStaleIndex() {
    LX lx = newHeadlessLx();
    LXComposition composition = composition(lx);
    TextNoteClipLane lane = composition.addTextNoteLane();
    Compositions.addNote(lane, "a", composition.constructAbsoluteCursor(1_000), null);

    Resolve.ResolveException stale = assertThrows(Resolve.ResolveException.class,
        () -> Compositions.setNote(lane, 0, composition.constructAbsoluteCursor(2_000),
            "x", null, null));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, stale.failure);
    assertEquals("a", lane.events.get(0).note.getString());
  }

  @Test
  void setArmWritesTheBareEngineFieldAndReadsItBack() {
    LX lx = newHeadlessLx();
    assertFalse(lx.engine.timeline.arm.isOn());
    assertTrue(Compositions.setArm(lx, true));
    assertTrue(lx.engine.timeline.arm.isOn());
    assertFalse(Compositions.setArm(lx, false));
    assertFalse(lx.engine.timeline.arm.isOn());
  }
}
