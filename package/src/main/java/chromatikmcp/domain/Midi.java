package chromatikmcp.domain;

import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;

import heronarts.lx.LX;
import heronarts.lx.command.LXCommand;
import heronarts.lx.midi.LXMidiEngine;
import heronarts.lx.midi.LXMidiInput;
import heronarts.lx.midi.LXMidiMapping;
import heronarts.lx.midi.LXMidiOutput;
import heronarts.lx.midi.LXShortMessage;
import heronarts.lx.midi.MidiControlChange;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.midi.surface.LXMidiSurface;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;

/**
 * Read-only view of the MIDI engine ({@code lx.engine.midi}): the physical input/output
 * ports LX has discovered, the parameter mappings driven by incoming MIDI, and the
 * instantiated control surfaces. This is the "how is this rig controlled externally"
 * surface that OSC discovery ({@code get_project_info}) can't answer.
 *
 * <p>MIDI ports, mappings, and surfaces are not {@code LXComponent}s with canonical paths
 * (an {@link LXMidiInput} is an {@code LXMidiDevice}, an {@link LXMidiMapping} is a plain
 * serializable), so each is addressed by its 0-based {@code index} into the engine's live
 * list. Indices shift when a device connects/disconnects or a mapping is removed — re-list
 * before reusing one. Call every method on the engine thread; the returned records are
 * immutable and safe to read anywhere.
 */
public final class Midi {

  private Midi() {}

  /**
   * A discovered MIDI input port. {@code enabled} is the union of the three routing flags;
   * each flag independently governs one path incoming MIDI can take: {@code channelEnabled}
   * forwards notes/CCs to channel + modulator devices, {@code controlEnabled} feeds the
   * control-mapping layer (the mappings {@link #mappings} lists), {@code syncEnabled} lets
   * this port's MIDI clock drive the engine tempo (only takes effect when get_tempo's
   * clockSource is MIDI). {@code connected} starts true for every listed port and flips false
   * only if the device drops off mid-session; ports remembered from a saved project whose
   * hardware is absent are never listed at all.
   */
  public record InputInfo(int index, String name, String description, boolean connected,
      boolean enabled, boolean channelEnabled, boolean controlEnabled, boolean syncEnabled) {}

  /** A discovered MIDI output port. Outputs have no routing flags — only enabled/connected. */
  public record OutputInfo(int index, String name, String description, boolean connected,
      boolean enabled) {}

  /** Both port lists in one snapshot — the pair a client needs to describe the I/O surface. */
  public record DevicesInfo(List<InputInfo> inputs, List<OutputInfo> outputs) {}

  /**
   * One parameter mapping. {@code type} is {@code "note"} or {@code "cc"}; {@code number}
   * is the note pitch (0-127) or CC number (0-127) accordingly; {@code note} is the pitch
   * name (e.g. {@code "C3"}) for note mappings, null for CC. {@code channel} is the 0-based
   * MIDI channel (0-15). {@code targetPath} is the canonical path of the mapped parameter —
   * feed it to get_parameter/set_parameter. {@code label} is LX's description of the mapping
   * source; {@code targetLabel} is the mapped parameter's display label.
   */
  public record MappingInfo(int index, String type, int channel, int number, String note,
      String label, String targetPath, String targetLabel) {}

  /**
   * An instantiated control surface (e.g. an APC40, a MidiFighterTwister). {@code name} is
   * the human surface name, {@code deviceName} the MIDI device it binds to. {@code enabled}
   * means the surface is actively driving/reading LEDs; {@code connected} means its device
   * is present.
   */
  public record SurfaceInfo(int index, String name, String deviceName, String className,
      boolean enabled, boolean connected, String inputName, String outputName) {}

  /** Snapshot the discovered input and output ports. Call on the engine thread. */
  public static DevicesInfo devices(LX lx) {
    LXMidiEngine engine = lx.engine.midi;
    List<InputInfo> inputs = new ArrayList<>();
    int i = 0;
    for (LXMidiInput input : engine.inputs) {
      inputs.add(inputInfo(i++, input));
    }
    List<OutputInfo> outputs = new ArrayList<>();
    int o = 0;
    for (LXMidiOutput output : engine.outputs) {
      outputs.add(new OutputInfo(
          o++,
          output.getName(),
          output.getDescription(),
          output.connected.isOn(),
          output.enabled.isOn()));
    }
    return new DevicesInfo(inputs, outputs);
  }

  /** Snapshot the parameter mappings driven by incoming MIDI. Call on the engine thread. */
  public static List<MappingInfo> mappings(LX lx) {
    List<MappingInfo> result = new ArrayList<>();
    int index = 0;
    for (LXMidiMapping mapping : lx.engine.midi.mappings) {
      result.add(mappingInfo(index++, mapping));
    }
    return result;
  }

  /** Snapshot the instantiated control surfaces. Call on the engine thread. */
  public static List<SurfaceInfo> surfaces(LX lx) {
    List<SurfaceInfo> result = new ArrayList<>();
    int index = 0;
    for (LXMidiSurface surface : lx.engine.midi.surfaces) {
      result.add(surfaceInfo(index++, surface));
    }
    return result;
  }

  // ── Mutations ────────────────────────────────────────────────────────────────

  /**
   * Fixed velocity for a note mapping added via {@link #addMapping}. A mapping wires
   * channel+pitch (note) or channel+cc (control change) to a parameter; LX dispatches on
   * that identity, not the triggering message's velocity/value, so any in-range constant
   * works — 127 (max) matches what a real controller sends for a firm keypress.
   */
  private static final int MAPPING_NOTE_VELOCITY = 127;

  /** Fixed initial value for a CC mapping added via {@link #addMapping}; see above. */
  private static final int MAPPING_CC_VALUE = 0;

  /**
   * Add a MIDI mapping: incoming {@code type} ('note' or 'cc') on {@code channel} (0-15)
   * with pitch/cc {@code number} (0-127) drives the parameter at {@code targetPath}.
   * Routes through {@code LXCommand.Midi.AddMapping} (undoable). Only parameters
   * implementing {@link LXNormalizedParameter} are mappable — the same restriction LX's own
   * UI mapping mode enforces (LXMidiMapping.create's signature).
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH if the target isn't a normalized
   *     parameter, or channel/number are out of MIDI range (LXShortMessage validates)
   */
  public static MappingInfo addMapping(LX lx, String type, int channel, int number, String targetPath) {
    LXParameter parameter = Resolve.parameter(lx, targetPath);
    if (!(parameter instanceof LXNormalizedParameter normalized)) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Parameter at " + targetPath + " cannot be MIDI-mapped (not a normalized parameter): "
              + parameter.getClass().getSimpleName());
    }
    LXShortMessage message;
    try {
      message = switch (type) {
        case "note" -> new MidiNoteOn(channel, number, MAPPING_NOTE_VELOCITY);
        case "cc" -> new MidiControlChange(channel, number, MAPPING_CC_VALUE);
        default -> throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
            "type must be 'note' or 'cc': " + type);
      };
    } catch (InvalidMidiDataException e) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Invalid MIDI mapping (channel " + channel + ", number " + number + "): " + e.getMessage());
    }
    List<LXMidiMapping> mappings = lx.engine.midi.mappings;
    Commands.perform(lx, new LXCommand.Midi.AddMapping(message, normalized));
    int index = mappings.size() - 1;
    return mappingInfo(index, mappings.get(index));
  }

  /**
   * Remove the mapping at {@code index} (0-based, into {@link #mappings}). Routes through
   * {@code LXCommand.Midi.RemoveMapping} (undoable). Remaining mappings reindex, so a
   * caller must re-list before reusing another index.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH if index is out of range
   */
  public static MappingInfo removeMapping(LX lx, int index) {
    List<LXMidiMapping> mappings = lx.engine.midi.mappings;
    if (index < 0 || index >= mappings.size()) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Mapping index " + index + " out of range [0," + (mappings.size() - 1) + "]");
    }
    LXMidiMapping mapping = mappings.get(index);
    MappingInfo removed = mappingInfo(index, mapping);
    Commands.perform(lx, new LXCommand.Midi.RemoveMapping(lx, mapping));
    return removed;
  }

  /**
   * Set one or more of an input's routing flags (see {@link InputInfo}) by 0-based index
   * into {@link #devices}' input list. A {@code null} argument leaves that flag unchanged.
   * No {@code LXCommand} covers these {@code BooleanParameter}s, so they're set directly
   * (CLAUDE.md layering); {@code enabled} is a derived union LX recomputes from the three
   * flags and can't be set directly.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH if index is out of range
   */
  public static InputInfo setInputFlags(LX lx, int index, Boolean channelEnabled,
      Boolean controlEnabled, Boolean syncEnabled) {
    List<LXMidiInput> inputs = lx.engine.midi.inputs;
    if (index < 0 || index >= inputs.size()) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Input index " + index + " out of range [0," + (inputs.size() - 1) + "]");
    }
    LXMidiInput input = inputs.get(index);
    if (channelEnabled != null) {
      input.channelEnabled.setValue(channelEnabled);
    }
    if (controlEnabled != null) {
      input.controlEnabled.setValue(controlEnabled);
    }
    if (syncEnabled != null) {
      input.syncEnabled.setValue(syncEnabled);
    }
    return inputInfo(index, input);
  }

  /**
   * Enable or disable a control surface by 0-based index into {@link #surfaces}. No
   * {@code LXCommand} covers surface enablement, so it's set directly on the surface's
   * {@code BooleanParameter} (CLAUDE.md layering).
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH if index is out of range
   */
  public static SurfaceInfo setSurfaceEnabled(LX lx, int index, boolean enabled) {
    List<LXMidiSurface> surfaces = lx.engine.midi.surfaces;
    if (index < 0 || index >= surfaces.size()) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Surface index " + index + " out of range [0," + (surfaces.size() - 1) + "]");
    }
    LXMidiSurface surface = surfaces.get(index);
    surface.enabled.setValue(enabled);
    return surfaceInfo(index, surface);
  }

  // ── Private helpers ──────────────────────────────────────────────────────────

  private static InputInfo inputInfo(int index, LXMidiInput input) {
    return new InputInfo(
        index,
        input.getName(),
        input.getDescription(),
        input.connected.isOn(),
        input.enabled.isOn(),
        input.channelEnabled.isOn(),
        input.controlEnabled.isOn(),
        input.syncEnabled.isOn());
  }

  private static MappingInfo mappingInfo(int index, LXMidiMapping mapping) {
    String type;
    int number;
    String note;
    if (mapping instanceof LXMidiMapping.Note noteMapping) {
      type = "note";
      number = noteMapping.pitch;
      note = MidiNote.getPitchString(noteMapping.pitch);
    } else if (mapping instanceof LXMidiMapping.ControlChange ccMapping) {
      type = "cc";
      number = ccMapping.cc;
      note = null;
    } else {
      throw new IllegalStateException("Unknown LXMidiMapping subtype: " + mapping.getClass().getName());
    }
    return new MappingInfo(
        index,
        type,
        mapping.channel,
        number,
        note,
        mapping.getDescription(),
        Resolve.canonicalPath(mapping.parameter),
        mapping.parameter.getLabel());
  }

  private static SurfaceInfo surfaceInfo(int index, LXMidiSurface surface) {
    return new SurfaceInfo(
        index,
        surface.getSurfaceName(),
        surface.getDeviceName(),
        surface.getClass().getName(),
        surface.enabled.isOn(),
        surface.connected.isOn(),
        surface.getInput().getName(),
        (surface.getOutput() != null) ? surface.getOutput().getName() : null);
  }
}
