package chromatikmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sound.midi.InvalidMidiDataException;

import org.junit.jupiter.api.Test;

import chromatikmcp.HeadlessLxTest;

import heronarts.lx.LX;
import heronarts.lx.command.LXCommand;
import heronarts.lx.midi.LXMidiInput;
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

  // ── addMapping / removeMapping ───────────────────────────────────────────────

  @Test
  void addMappingAddsCcMappingAndUndoRemoves() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();

    Midi.MappingInfo added = Midi.addMapping(
        lx, "cc", 2, 20, channel.fader.getCanonicalPath());

    assertEquals(0, added.index());
    assertEquals("cc", added.type());
    assertEquals(2, added.channel());
    assertEquals(20, added.number());
    assertNull(added.note());
    assertEquals(channel.fader.getCanonicalPath(), added.targetPath());
    assertEquals(1, lx.engine.midi.mappings.size());

    lx.command.undo();
    assertEquals(0, lx.engine.midi.mappings.size(), "undo should remove the mapping");
  }

  @Test
  void addMappingAddsNoteMappingWithPitchName() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();

    Midi.MappingInfo added = Midi.addMapping(
        lx, "note", 0, 60, channel.enabled.getCanonicalPath());

    assertEquals("note", added.type());
    assertEquals(0, added.channel());
    assertEquals(60, added.number());
    assertNotNull(added.note());
    assertTrue(added.note().contains("C"), "pitch 60 is a C: " + added.note());
  }

  @Test
  void addMappingRejectsNonNormalizedTarget() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();

    // The label parameter is a StringParameter, not an LXNormalizedParameter.
    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Midi.addMapping(lx, "cc", 0, 1, channel.label.getCanonicalPath()));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
  }

  @Test
  void addMappingUnknownTargetPathIsNotFound() {
    LX lx = newHeadlessLx();

    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Midi.addMapping(lx, "cc", 0, 1, "/lx/nope/nothing"));
    assertEquals(Resolve.Failure.NOT_FOUND, e.failure);
  }

  @Test
  void removeMappingRemovesAndUndoRestores() throws InvalidMidiDataException {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    lx.command.perform(new LXCommand.Midi.AddMapping(
        new MidiControlChange(2, 20, 64), channel.fader));
    assertEquals(1, lx.engine.midi.mappings.size());

    Midi.MappingInfo removed = Midi.removeMapping(lx, 0);
    assertEquals("cc", removed.type());
    assertEquals(2, removed.channel());
    assertEquals(20, removed.number());
    assertEquals(channel.fader.getCanonicalPath(), removed.targetPath());
    assertEquals(0, lx.engine.midi.mappings.size());

    lx.command.undo();
    assertEquals(1, lx.engine.midi.mappings.size(), "undo should restore the mapping");
  }

  @Test
  void removeMappingInvalidIndexIsTypeMismatch() {
    LX lx = newHeadlessLx();

    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Midi.removeMapping(lx, 0));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
  }

  // ── setInputFlags ────────────────────────────────────────────────────────────

  @Test
  void setInputFlagsSetsFlagAndDerivedEnabledUnion() {
    LX lx = newHeadlessLx();
    LXMidiInput input = awaitFirstInput(lx);
    assertFalse(input.enabled.isOn(), "no routing flag set yet");

    Midi.InputInfo info = Midi.setInputFlags(lx, 0, true, null, null);
    assertTrue(info.channelEnabled());
    assertFalse(info.controlEnabled());
    assertFalse(info.syncEnabled());
    assertTrue(info.enabled(), "enabled is the union of the three flags");
    assertTrue(input.channelEnabled.isOn());

    Midi.InputInfo cleared = Midi.setInputFlags(lx, 0, false, null, null);
    assertFalse(cleared.enabled(), "union drops to false once every flag is off");
  }

  @Test
  void setInputFlagsInvalidIndexIsTypeMismatch() {
    LX lx = newHeadlessLx();

    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Midi.setInputFlags(lx, 0, true, null, null));
    assertEquals(Resolve.Failure.TYPE_MISMATCH, e.failure);
  }

  /**
   * LX populates {@code engine.midi.inputs} from an async device-detection thread that
   * finishes with an {@code engine.addTask(...)}, so headless tests (which never run the
   * engine loop) see it empty until they drain the queue themselves. The JDK's built-in
   * "Real Time Sequencer" software device is always present, on any OS, so this resolves
   * without depending on real hardware.
   */
  private static LXMidiInput awaitFirstInput(LX lx) {
    long deadline = System.currentTimeMillis() + 5000;
    while (lx.engine.midi.inputs.isEmpty()) {
      if (System.currentTimeMillis() > deadline) {
        throw new IllegalStateException("No MIDI input discovered within 5s");
      }
      lx.engine.run();
      try {
        Thread.sleep(20);
      } catch (InterruptedException ix) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(ix);
      }
    }
    return lx.engine.midi.inputs.get(0);
  }
}
