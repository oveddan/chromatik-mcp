package chromatikmcp.domain;

import java.util.ArrayList;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.clip.AudioClipLane;
import heronarts.lx.clip.BusClipLane;
import heronarts.lx.clip.ColorPaletteClipLane;
import heronarts.lx.clip.GlobalClipLane;
import heronarts.lx.clip.GlobalModulationClipLane;
import heronarts.lx.clip.LXClip;
import heronarts.lx.clip.LXClipLane;
import heronarts.lx.clip.MidiNoteClipLane;
import heronarts.lx.clip.ParameterClipLane;
import heronarts.lx.clip.PatternClipLane;
import heronarts.lx.clip.TextNoteClipLane;
import heronarts.lx.command.LXCommand;
import heronarts.lx.mixer.LXPatternEngine;
import heronarts.lx.parameter.LXNormalizedParameter;

/** Clip-lane addressing, description, and (family band) lifecycle primitives. */
public final class ClipLanes {

  private ClipLanes() {}

  /**
   * The canonical MCP-facing path of a lane, always in the {@code <clipPath>/lane/<1-based>}
   * form that {@code Resolve} round-trips — never the lane's own {@code getPath()}, whose
   * upstream overrides are inconsistent (see {@code Resolve.pathSegment}). POSITIONAL:
   * shifts whenever lanes are added/removed/moved — re-list rather than reuse a held path.
   */
  public static String lanePath(LXClipLane<?> lane) {
    return Resolve.canonicalPath(lane);
  }

  /** Stable wire type string per lane class (payload {@code type} field). */
  public static String type(LXClipLane<?> lane) {
    return switch (lane) {
      case ParameterClipLane l -> "parameter";
      case PatternClipLane l -> "pattern";
      case MidiNoteClipLane l -> "midiNote";
      case BusClipLane l -> "bus";
      case GlobalModulationClipLane l -> "globalModulation";
      case ColorPaletteClipLane l -> "colorPalette";
      case AudioClipLane l -> "audio";
      case TextNoteClipLane l -> "textNote";
      default -> lane.getClass().getSimpleName();
    };
  }

  /**
   * Whether remove_clip_lane may touch this lane. Auto-managed lanes are structural:
   * removing a composition's {@code BusClipLane} corrupts it ({@code unregisterBus} later
   * throws on the missing lane) and {@code GlobalClipLane}s (global modulation, color
   * palette) are singletons the composition recreates on load; a grid clip's MIDI/pattern
   * lanes are {@code isPermanentClipLane} upstream (protected — mirrored here) and
   * {@code removeClipLane} throws on them. Pre-check this before touching the command
   * engine, and surface it on every lane payload so agents never have to find out by
   * erroring.
   */
  public static boolean isRemovable(LXClipLane<?> lane) {
    if (lane instanceof BusClipLane || lane instanceof GlobalClipLane) {
      return false;
    }
    if (!lane.clip.isComposition()
        && (lane instanceof MidiNoteClipLane || lane instanceof PatternClipLane)) {
      return false;
    }
    return true;
  }

  /**
   * The per-lane summary shared by list_clip_lanes and get_composition (and reused as the
   * base of every lane-mutation echo): path, 0-based index, type, label, eventCount,
   * uiVisible, removable, plus the lane's target where it has one.
   *
   * <p>The three target fields are mutually exclusive and null on a lane that has none —
   * or whose target isn't path-registered. {@code Payloads.laneSummary} omits a null key
   * rather than emitting a literal JSON null (see {@code Payloads.putIfPresent}).
   */
  public record LaneSummary(
      String path,
      int index,
      String type,
      String label,
      int eventCount,
      boolean uiVisible,
      boolean removable,
      String parameterPath,
      String busPath,
      String channelPath) {}

  /** Snapshots one lane; {@code Payloads.laneSummary} owns its MCP field names. */
  public static LaneSummary summary(LXClipLane<?> lane) {
    String parameterPath = null;
    String busPath = null;
    String channelPath = null;
    switch (lane) {
      case ParameterClipLane l -> parameterPath = Resolve.canonicalPathOrNull(l.parameter);
      case BusClipLane l -> busPath = Resolve.canonicalPathOrNull(l.bus);
      // Both channel fields are nullable upstream (master-bus MIDI lane, rack pattern lane).
      case MidiNoteClipLane l -> channelPath =
          (l.channel != null) ? Resolve.canonicalPathOrNull(l.channel) : null;
      case PatternClipLane l -> channelPath =
          (l.channel != null) ? Resolve.canonicalPathOrNull(l.channel) : null;
      default -> { }
    }
    return new LaneSummary(
        lanePath(lane),
        lane.getIndex(),
        type(lane),
        lane.getLabel(),
        lane.events.size(),
        lane.uiVisible.isOn(),
        isRemovable(lane),
        parameterPath,
        busPath,
        channelPath);
  }

  /** Lane summaries for every lane on the clip, in engine order. */
  public static List<LaneSummary> list(LXClip clip) {
    List<LaneSummary> lanes = new ArrayList<>();
    for (LXClipLane<?> lane : clip.lanes) {
      lanes.add(summary(lane));
    }
    return lanes;
  }

  // ============================================================
  // F3 Lane lifecycle primitives (branch comp-f3-lanes).
  // Keep additions inside this band; bands are merge-isolation, do not reorder.
  // ============================================================

  /** An add result: the lane now backing the request, and whether it pre-existed. */
  public record AddedLane(LXClipLane<?> lane, boolean alreadyExisted) {}

  /**
   * Adds a parameter automation lane via {@code LXCommand.Clip.AddLane}. The
   * existing-lane case is detected by state-read BEFORE performing: AddLane flags itself
   * {@code isIgnored} when the lane exists, {@code LXCommandEngine.perform} never pushes
   * an ignored command, and {@code Commands.perform} would misread that benign no-op as a
   * failed command. Returning the existing lane keeps the common agent intent ("make sure
   * a lane exists for this parameter") idempotent instead of an internal error.
   */
  public static AddedLane addParameterLane(LX lx, LXClip clip, LXNormalizedParameter parameter) {
    ParameterClipLane existing = clip.getParameterLane(parameter);
    if (existing != null) {
      return new AddedLane(existing, true);
    }
    Commands.perform(lx, new LXCommand.Clip.AddLane(clip, parameter));
    ParameterClipLane lane = clip.getParameterLane(parameter);
    if (lane == null) {
      throw new IllegalStateException("AddLane committed but the parameter lane is missing");
    }
    return new AddedLane(lane, false);
  }

  /**
   * Pattern-lane counterpart of {@link #addParameterLane}; same state-read rationale
   * ({@code AddPatternLane} is equally an {@code isIgnored} no-op when the lane exists —
   * always true on a grid channel clip, whose pattern lane is permanent).
   */
  public static AddedLane addPatternLane(LX lx, LXClip clip, LXPatternEngine engine) {
    PatternClipLane existing = clip.getPatternLane(engine, false);
    if (existing != null) {
      return new AddedLane(existing, true);
    }
    Commands.perform(lx, new LXCommand.Clip.AddPatternLane(clip, engine));
    PatternClipLane lane = clip.getPatternLane(engine, false);
    if (lane == null) {
      throw new IllegalStateException("AddPatternLane committed but the pattern lane is missing");
    }
    return new AddedLane(lane, false);
  }

  /**
   * Removes a lane via {@code LXCommand.Clip.RemoveClipLane}, pre-checked against
   * {@link #isRemovable}: upstream {@code removeClipLane} throws on a grid clip's
   * permanent lanes but happily removes a composition's auto-managed bus/global lanes,
   * corrupting it (see {@link #isRemovable}) — so the guard must run before the command
   * engine is touched, not rely on upstream to refuse.
   */
  public static void removeLane(LX lx, LXClipLane<?> lane) {
    if (!isRemovable(lane)) {
      throw Resolve.invalidArgument("Not removable: " + lanePath(lane)
          + " is an auto-managed " + type(lane) + " lane (bus/globalModulation/colorPalette"
          + " lanes everywhere; midiNote/pattern lanes on grid clips)");
    }
    LXClip clip = lane.clip;
    Commands.perform(lx, new LXCommand.Clip.RemoveClipLane(lane));
    if (clip.lanes.contains(lane)) {
      throw new IllegalStateException("RemoveClipLane committed but the lane is still present");
    }
  }

  /**
   * Moves a lane via {@code LXCommand.Clip.MoveLane}. The engine may override the request
   * without failing the command — {@code LXComposition.validateMoveClipLaneIndex}
   * constrains minor lanes to their channel's section and snaps section lanes across
   * whole sections (or refuses outright, leaving the lane in place) — so callers must
   * echo {@code lane.getIndex()} read back after the move, never the request. Bus lanes
   * mirror mixer order: upstream logs and no-ops on them, which would silently lie to an
   * agent, so they are rejected up front.
   */
  public static LXClipLane<?> moveLane(LX lx, LXClipLane<?> lane, int index) {
    if (lane instanceof BusClipLane) {
      throw Resolve.invalidArgument("A bus lane's position mirrors the mixer — move the"
          + " channel in the mixer instead: " + lanePath(lane));
    }
    int laneCount = lane.clip.lanes.size();
    if (index < 0 || index >= laneCount) {
      throw Resolve.invalidArgument("index " + index + " out of range: clip has "
          + laneCount + " lanes (0-" + (laneCount - 1) + ")");
    }
    Commands.perform(lx, new LXCommand.Clip.MoveLane(lane, index));
    return lane;
  }

  /**
   * Shows/hides a lane in the arrange UI. {@code uiVisible} is a bare public field — the
   * one LXClipLane UI parameter NOT registered via {@code addInternalParameter} — so
   * there is no command for it: direct engine edit, not undoable, playback unaffected.
   */
  public static LXClipLane<?> setVisible(LXClipLane<?> lane, boolean visible) {
    lane.uiVisible.setValue(visible);
    return lane;
  }
}
