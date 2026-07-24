package chromatikmcp.domain;

import java.util.ArrayList;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.midi.LXMidiEngine;
import heronarts.lx.midi.LXMidiInput;
import heronarts.lx.midi.LXMidiMapping;
import heronarts.lx.midi.LXMidiOutput;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.surface.LXMidiSurface;

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
      inputs.add(new InputInfo(
          i++,
          input.getName(),
          input.getDescription(),
          input.connected.isOn(),
          input.enabled.isOn(),
          input.channelEnabled.isOn(),
          input.controlEnabled.isOn(),
          input.syncEnabled.isOn()));
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
      result.add(new MappingInfo(
          index++,
          type,
          mapping.channel,
          number,
          note,
          mapping.getDescription(),
          Resolve.canonicalPath(mapping.parameter),
          mapping.parameter.getLabel()));
    }
    return result;
  }

  /** Snapshot the instantiated control surfaces. Call on the engine thread. */
  public static List<SurfaceInfo> surfaces(LX lx) {
    List<SurfaceInfo> result = new ArrayList<>();
    int index = 0;
    for (LXMidiSurface surface : lx.engine.midi.surfaces) {
      result.add(new SurfaceInfo(
          index++,
          surface.getSurfaceName(),
          surface.getDeviceName(),
          surface.getClass().getName(),
          surface.enabled.isOn(),
          surface.connected.isOn(),
          surface.getInput().getName(),
          (surface.getOutput() != null) ? surface.getOutput().getName() : null));
    }
    return result;
  }
}
