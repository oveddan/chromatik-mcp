package lxmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import heronarts.lx.LX;
import heronarts.lx.midi.LXMidiMapping;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.model.GridModel;

/**
 * Instantiates the qa-strategy verification template for MIDI mapping mutations:
 * do → undo → assert restored, plus the pre-check rejection paths (range, mappability).
 */
class MidiMappingsTest {

  private LX lx;

  private LX newHeadlessLx() {
    this.lx = new LX(new GridModel(8, 8));
    return this.lx;
  }

  @AfterEach
  void tearDown() {
    if (this.lx != null) {
      this.lx.dispose();
      this.lx = null;
    }
  }

  @Test
  void addMappingMutatesEngineStateForCcAndNote() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    int before = lx.engine.midi.mappings.size();

    LXMidiMapping cc = MidiMappings.addMapping(
        lx, MidiMappings.MessageType.CC, 1, 20, channel.fader);
    assertEquals(before + 1, lx.engine.midi.mappings.size());
    assertSame(cc, lx.engine.midi.mappings.get(before));
    assertTrue(cc instanceof LXMidiMapping.ControlChange);
    assertEquals(0, cc.channel, "1-based tool channel maps to LX's 0-based channel");

    LXMidiMapping note = MidiMappings.addMapping(
        lx, MidiMappings.MessageType.NOTE, 16, 60, channel.enabled);
    assertTrue(note instanceof LXMidiMapping.Note);
    assertEquals(15, note.channel);
  }

  @Test
  void addMappingUndoRestoresState() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    int before = lx.engine.midi.mappings.size();

    MidiMappings.addMapping(lx, MidiMappings.MessageType.CC, 1, 20, channel.fader);
    assertEquals(before + 1, lx.engine.midi.mappings.size());

    lx.command.undo();

    assertEquals(before, lx.engine.midi.mappings.size(), "undo removes the mapping");
  }

  @Test
  void addMappingRejectsOutOfRangeChannel() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    var undoBefore = lx.command.getUndoCommand();
    int before = lx.engine.midi.mappings.size();

    assertEquals(Resolve.Failure.TYPE_MISMATCH,
        assertThrows(Resolve.ResolveException.class,
            () -> MidiMappings.addMapping(lx, MidiMappings.MessageType.CC, 0, 20, channel.fader))
            .failure);
    assertEquals(Resolve.Failure.TYPE_MISMATCH,
        assertThrows(Resolve.ResolveException.class,
            () -> MidiMappings.addMapping(lx, MidiMappings.MessageType.CC, 17, 20, channel.fader))
            .failure);
    assertEquals(before, lx.engine.midi.mappings.size(), "nothing was added");
    assertSame(undoBefore, lx.command.getUndoCommand(),
        "the pre-check fired before perform — undo history untouched");
  }

  @Test
  void addMappingRejectsOutOfRangeNumber() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    int before = lx.engine.midi.mappings.size();

    assertEquals(Resolve.Failure.TYPE_MISMATCH,
        assertThrows(Resolve.ResolveException.class,
            () -> MidiMappings.addMapping(lx, MidiMappings.MessageType.NOTE, 1, -1, channel.fader))
            .failure);
    assertEquals(Resolve.Failure.TYPE_MISMATCH,
        assertThrows(Resolve.ResolveException.class,
            () -> MidiMappings.addMapping(lx, MidiMappings.MessageType.NOTE, 1, 128, channel.fader))
            .failure);
    assertEquals(before, lx.engine.midi.mappings.size(), "nothing was added");
  }

  @Test
  void addMappingRejectsUnmappableParameter() {
    LX lx = newHeadlessLx();
    int before = lx.engine.midi.mappings.size();
    var undoBefore = lx.command.getUndoCommand();

    // lx.engine.framesPerSecond is explicitly setMappable(false) upstream.
    assertEquals(Resolve.Failure.TYPE_MISMATCH,
        assertThrows(Resolve.ResolveException.class,
            () -> MidiMappings.addMapping(
                lx, MidiMappings.MessageType.CC, 1, 20, lx.engine.framesPerSecond))
            .failure);
    assertEquals(before, lx.engine.midi.mappings.size(), "nothing was added");
    assertSame(undoBefore, lx.command.getUndoCommand(),
        "the pre-check fired before perform — undo history untouched");
  }

  @Test
  void listSnapshotsMappings() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    MidiMappings.addMapping(lx, MidiMappings.MessageType.CC, 1, 20, channel.fader);
    MidiMappings.addMapping(lx, MidiMappings.MessageType.NOTE, 3, 60, channel.enabled);

    var mappings = MidiMappings.list(lx);
    assertEquals(2, mappings.size());
    assertEquals(MidiMappings.MessageType.CC, mappings.get(0).type());
    assertEquals(1, mappings.get(0).channel());
    assertEquals(20, mappings.get(0).number());
    assertEquals(channel.fader.getCanonicalPath(), mappings.get(0).targetPath());
    assertEquals(MidiMappings.MessageType.NOTE, mappings.get(1).type());
    assertEquals(3, mappings.get(1).channel());
    assertEquals(60, mappings.get(1).number());
    assertEquals(channel.enabled.getCanonicalPath(), mappings.get(1).targetPath());
  }

  @Test
  void removeMappingUndoRestores() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    MidiMappings.addMapping(lx, MidiMappings.MessageType.CC, 1, 20, channel.fader);
    int before = lx.engine.midi.mappings.size();

    MidiMappings.removeMapping(lx, 0);
    assertEquals(before - 1, lx.engine.midi.mappings.size());

    lx.command.undo();
    assertEquals(before, lx.engine.midi.mappings.size(), "undo reconstructs the mapping");
    assertEquals(channel.fader.getCanonicalPath(),
        lx.engine.midi.mappings.get(0).parameter.getCanonicalPath());
  }

  @Test
  void removeMappingRejectsOutOfRangeIndex() {
    LX lx = newHeadlessLx();
    assertEquals(Resolve.Failure.NOT_FOUND,
        assertThrows(Resolve.ResolveException.class,
            () -> MidiMappings.removeMapping(lx, 0)).failure);
  }
}
