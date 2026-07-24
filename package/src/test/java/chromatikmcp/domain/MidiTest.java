package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sound.midi.InvalidMidiDataException;

import org.junit.jupiter.api.Test;

import chromatikmcp.HeadlessLxTest;

import heronarts.lx.LX;
import heronarts.lx.command.LXCommand;
import heronarts.lx.midi.MidiControlChange;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.mixer.LXChannel;

class MidiTest extends HeadlessLxTest {

  @Test
  void devicesSnapshotReturnsBothPortListsAndIsTotalOnEmpty() {
    LX lx = newHeadlessLx();
    // Headless construction discovers no hardware ports; the snapshot must still return
    // both (possibly empty) lists rather than null — clients iterate unconditionally.
    Midi.DevicesInfo info = Midi.devices(lx);
    assertNotNull(info.inputs());
    assertNotNull(info.outputs());
    assertEquals(lx.engine.midi.inputs.size(), info.inputs().size());
    assertEquals(lx.engine.midi.outputs.size(), info.outputs().size());
  }

  @Test
  void surfacesSnapshotIsTotalOnEmpty() {
    LX lx = newHeadlessLx();
    assertNotNull(Midi.surfaces(lx));
    assertEquals(lx.engine.midi.surfaces.size(), Midi.surfaces(lx).size());
  }

  @Test
  void mappingsSnapshotReflectsAControlChangeMapping() throws InvalidMidiDataException {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();

    // A CC mapping does not need a live input — LXMidiMapping.create only needs the message
    // + a parameter with a parent, so the read snapshot is testable headlessly.
    lx.command.perform(new LXCommand.Midi.AddMapping(
        new MidiControlChange(2, 20, 64), channel.fader));

    var mappings = Midi.mappings(lx);
    assertEquals(1, mappings.size());
    Midi.MappingInfo m = mappings.get(0);
    assertEquals(0, m.index());
    assertEquals("cc", m.type());
    assertEquals(2, m.channel());
    assertEquals(20, m.number());
    assertNull(m.note(), "CC mappings carry no note name");
    assertEquals(channel.fader.getCanonicalPath(), m.targetPath());
    assertEquals(channel.fader.getLabel(), m.targetLabel());
  }

  @Test
  void mappingsSnapshotReflectsANoteMappingWithPitchName() throws InvalidMidiDataException {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();

    lx.command.perform(new LXCommand.Midi.AddMapping(
        new MidiNoteOn(0, 60, 127), channel.enabled));

    var mappings = Midi.mappings(lx);
    assertEquals(1, mappings.size());
    Midi.MappingInfo m = mappings.get(0);
    assertEquals("note", m.type());
    assertEquals(0, m.channel());
    assertEquals(60, m.number());
    assertNotNull(m.note(), "note mappings carry the pitch name");
    assertTrue(m.note().contains("C"), "pitch 60 is a C: " + m.note());
    assertEquals(channel.enabled.getCanonicalPath(), m.targetPath());
  }

  @Test
  void mappingIndicesAreSequential() throws InvalidMidiDataException {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    lx.command.perform(new LXCommand.Midi.AddMapping(
        new MidiControlChange(0, 1, 0), channel.fader));
    lx.command.perform(new LXCommand.Midi.AddMapping(
        new MidiControlChange(0, 2, 0), channel.enabled));

    var mappings = Midi.mappings(lx);
    assertEquals(2, mappings.size());
    assertEquals(0, mappings.get(0).index());
    assertEquals(1, mappings.get(1).index());
  }
}
