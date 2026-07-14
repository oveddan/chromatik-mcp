package lxmcp.domain;

import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;

import heronarts.lx.LX;
import heronarts.lx.command.LXCommand;
import heronarts.lx.midi.LXMidiMapping;
import heronarts.lx.midi.MidiControlChange;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.parameter.LXNormalizedParameter;

/**
 * Mutations and reads on {@code lx.engine.midi.mappings}. {@link LXMidiMapping} is a plain
 * {@code LXSerializable}, not an {@code LXComponent} — mappings carry no id or canonical
 * path, so tools address them by their position in the list (same staleness caveat as
 * pattern/effect indices elsewhere in this codebase: a removal reindexes the tail).
 */
public final class MidiMappings {

  private MidiMappings() {}

  /** The two message shapes {@code LXMidiMapping.create} accepts (LXMidiMapping.isValidMessageType). */
  public enum MessageType { NOTE, CC }

  public record MappingInfo(int index, MessageType type, int channel, int number, String targetPath) {}

  /** Call on the engine thread; the returned records are safe to read anywhere. */
  public static List<MappingInfo> list(LX lx) {
    List<LXMidiMapping> mappings = lx.engine.midi.mappings;
    List<MappingInfo> result = new ArrayList<>();
    for (int i = 0; i < mappings.size(); i++) {
      result.add(describe(i, mappings.get(i)));
    }
    return result;
  }

  private static MappingInfo describe(int index, LXMidiMapping mapping) {
    MessageType type;
    int number;
    if (mapping instanceof LXMidiMapping.Note note) {
      type = MessageType.NOTE;
      number = note.pitch;
    } else if (mapping instanceof LXMidiMapping.ControlChange cc) {
      type = MessageType.CC;
      number = cc.cc;
    } else {
      throw new IllegalStateException("Unknown LXMidiMapping subtype: " + mapping.getClass());
    }
    // channel is stored 0-based internally; tool-conventions addresses MIDI channels 1-16.
    return new MappingInfo(index, type, mapping.channel + 1, number, mapping.parameter.getCanonicalPath());
  }

  /**
   * Add a MIDI mapping from a declarative {@code (type, channel, number)} triple onto
   * {@code parameter}. {@code LXCommand.Midi.AddMapping}'s constructor wants a live
   * {@link heronarts.lx.midi.LXShortMessage}, not raw bytes — this synthesizes one from
   * the declarative args so the tool surface stays declarative
   * (docs/spike/lxcommand-mapping.md's {@code add_midi_mapping} row).
   *
   * @param channel 1-16 (MIDI convention); mapped to LX's 0-based internally
   * @param number note pitch or CC number, 0-127
   * @throws Resolve.ResolveException TYPE_MISMATCH for an out-of-range channel/number or a
   *     parameter LX does not consider mappable ({@code LXParameter.isMappable()} — not
   *     enforced by {@code LXMidiMapping.create} itself, but every stock UI mapping flow
   *     respects it, so an MCP-created mapping onto an explicitly non-mappable parameter
   *     (e.g. internal engine/MIDI/OSC config params) would be surprising, unsupported state)
   */
  public static LXMidiMapping addMapping(LX lx, MessageType type, int channel, int number,
      LXNormalizedParameter parameter) {
    if (channel < 1 || channel > 16) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "MIDI channel " + channel + " out of range [1,16]");
    }
    if (number < 0 || number > 127) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "MIDI " + (type == MessageType.NOTE ? "note" : "CC") + " number " + number
              + " out of range [0,127]");
    }
    if (!parameter.isMappable()) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Parameter " + parameter.getCanonicalPath() + " is not mappable "
              + "(LXParameter.isMappable() is false)");
    }
    int lxChannel = channel - 1;
    try {
      var message = switch (type) {
        case NOTE -> new MidiNoteOn(lxChannel, number, MidiNote.MAX_VELOCITY);
        case CC -> new MidiControlChange(lxChannel, number, 0);
      };
      List<LXMidiMapping> mappings = lx.engine.midi.mappings;
      int before = mappings.size();
      Commands.perform(lx, new LXCommand.Midi.AddMapping(message, parameter));
      if (mappings.size() != before + 1) {
        throw new IllegalStateException("AddMapping did not add a mapping");
      }
      return mappings.get(before);
    } catch (InvalidMidiDataException x) {
      // Unreachable given the range checks above (LX's own bytes constraint mirrors
      // [0,15]/[0,127]) — kept as a typed safety net rather than an unchecked escape.
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Could not construct a MIDI message: " + x.getMessage());
    }
  }

  /**
   * Remove the mapping at {@code index} (as returned by {@link #list}).
   *
   * @throws Resolve.ResolveException NOT_FOUND if index is out of range.
   */
  public static void removeMapping(LX lx, int index) {
    List<LXMidiMapping> mappings = lx.engine.midi.mappings;
    if (index < 0 || index >= mappings.size()) {
      throw new Resolve.ResolveException(Resolve.Failure.NOT_FOUND,
          "No MIDI mapping at index " + index + " (have " + mappings.size() + ")");
    }
    LXMidiMapping mapping = mappings.get(index);
    Commands.perform(lx, new LXCommand.Midi.RemoveMapping(lx, mapping));
  }
}
