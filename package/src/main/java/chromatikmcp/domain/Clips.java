package chromatikmcp.domain;

import heronarts.lx.LX;
import heronarts.lx.clip.Cursor;
import heronarts.lx.clip.LXClip;
import heronarts.lx.clip.LXClipContainer;
import heronarts.lx.clip.LXClipEngine;
import heronarts.lx.command.LXCommand;
import heronarts.lx.mixer.LXBus;

import chromatikmcp.domain.Cursors.CursorInfo;

/**
 * {@link LXClip}-scoped primitives — everything here works identically for the arrange
 * composition ({@code /lx/timeline/composition}) and for grid clips
 * ({@code /lx/mixer/channel/N/clip/M}), which resolve natively.
 */
public final class Clips {

  private Clips() {}

  /**
   * Resolves a clip by canonical path, defaulting to the composition when {@code path} is
   * null — the shared entry point for every tool whose {@code path} argument accepts
   * either the composition or a grid clip.
   */
  public static LXClip resolve(LX lx, String path) {
    if (path == null) {
      return Compositions.get(lx);
    }
    return Resolve.component(lx, path, LXClip.class);
  }

  /**
   * The shared clip envelope: identity, time base, and every marker as a full cursor
   * snapshot. {@code timeBase} lives here — once per clip, never per cursor — and decides
   * which cursor fields are authoritative (TEMPO comparisons ignore millis entirely;
   * millis are derived from {@code referenceBpm}, not the live tempo).
   */
  public record ClipEnvelope(
      String path,
      String label,
      String timeBase,
      double referenceBpm,
      CursorInfo length,
      boolean loop,
      CursorInfo loopStart,
      CursorInfo loopEnd,
      CursorInfo playStart,
      CursorInfo playEnd,
      CursorInfo insertMarker,
      CursorInfo playhead,
      boolean running,
      boolean hasContent,
      int laneCount) {}

  /** Snapshots the shared envelope; {@code Payloads.clipEnvelope} owns its MCP field names. */
  public static ClipEnvelope envelope(LXClip clip) {
    return new ClipEnvelope(
        Resolve.canonicalPath(clip),
        clip.getLabel(),
        clip.getTimeBase().name(),
        clip.referenceBpm.getValue(),
        Cursors.describe(clip, clip.length.cursor),
        clip.loop.isOn(),
        Cursors.describe(clip, clip.loopStart.cursor),
        Cursors.describe(clip, clip.loopEnd.cursor),
        Cursors.describe(clip, clip.playStart.cursor),
        Cursors.describe(clip, clip.playEnd.cursor),
        Cursors.describe(clip, clip.insertMarker.cursor),
        Cursors.describe(clip, clip.getCursor()),
        clip.isRunning(),
        clip.hasContent(),
        clip.lanes.size());
  }

  // ============================================================
  // F1 Transport & markers primitives (branch comp-f1-transport-markers) — implemented
  // by that family agent: setMarker/moveMarker (LXCommand.Clip.SetMarker/MoveMarker,
  // payloads echo the cursor READ BACK from the marker — every setter silently clamps),
  // and transport (launch/launchAutomationFrom/playFrom/stop — direct engine edits, LX
  // deliberately ships no transport command; not undoable).
  // Keep additions inside this band; bands are merge-isolation, do not reorder.
  // ============================================================

  /**
   * The get_clip state: the shared envelope plus transport state ({@code pending} —
   * whether a quantized launch is scheduled but hasn't fired yet).
   */
  public record ClipDetail(
      ClipEnvelope envelope,
      boolean pending,
      boolean snapshotEnabled,
      int snapshotViewCount) {}

  /** Snapshots {@link ClipDetail}; {@code Payloads.clip} flattens it onto the wire. */
  public static ClipDetail describe(LXClip clip) {
    return new ClipDetail(
        envelope(clip),
        clip.isPending(),
        clip.snapshotEnabled.isOn(),
        clip.snapshot.views.size());
  }

  /**
   * Sets a marker to an absolute cursor position via {@link LXCommand.Clip.SetMarker}
   * (undoable). Returns the marker's cursor read back from the clip — every setter
   * silently clamps, so the read-back is the only truthful echo (the clamp-echo rule).
   */
  public static Cursor setMarker(LX lx, LXClip clip, LXCommand.Clip.Marker marker, Cursor to) {
    Commands.perform(lx, new LXCommand.Clip.SetMarker(clip, marker, to));
    return marker.getCursor(clip);
  }

  /**
   * Nudges a marker by a non-negative increment, forward or backward, via
   * {@link LXCommand.Clip.MoveMarker} (undoable). Same clamp-echo return as
   * {@link #setMarker} — a backward move past zero bounds at the clip start.
   */
  public static Cursor moveMarker(
      LX lx, LXClip clip, LXCommand.Clip.Marker marker, Cursor increment, boolean forward) {
    if (!forward) {
      // Upstream SUBTRACT does clamp at zero, but through Cursor._subtract's error path,
      // which dumps a "Bogus Cursor subtraction" stack trace into the host log. Bounding
      // the increment to the marker's current position keeps the clamp silent.
      increment = clip.CursorOp().bound(increment, marker.getCursor(clip));
    }
    Commands.perform(lx, new LXCommand.Clip.MoveMarker(clip, marker, increment,
        forward ? LXCommand.Clip.MoveMarker.Operation.ADD
            : LXCommand.Clip.MoveMarker.Operation.SUBTRACT));
    return marker.getCursor(clip);
  }

  // Transport is a direct engine edit by design: LX deliberately ships no transport
  // command (play state is not part of the undo story), so none of these are undoable.

  /**
   * Quantized grid-style launch from the playStart marker: subject to the global launch
   * quantization and recalls the clip's snapshot when enabled. Verified by state
   * read-back: the launch must leave the clip running or pending.
   */
  public static void launch(LXClip clip) {
    clip.launch();
    requireRunningOrPending(clip, "launch");
  }

  /**
   * Quantized automation-only launch (no snapshot recall), from {@code from} or the
   * playStart marker when null. Same running-or-pending verification as {@link #launch}.
   */
  public static void launchAutomation(LXClip clip, Cursor from) {
    if (from == null) {
      clip.launchAutomation();
    } else {
      clip.launchAutomationFrom(from);
    }
    requireRunningOrPending(clip, "launchAutomation");
  }

  /**
   * Immediate, unquantized playback from {@code from} (or the current playhead when
   * null). {@code playFrom} upstream silently no-ops when the clip has no timeline or is
   * already running — both are turned into typed errors here, because a silent no-op is
   * exactly the failure an agent can't see.
   */
  public static void play(LXClip clip, Cursor from) {
    if (!clip.hasContent()) {
      throw Resolve.invalidArgument("Clip has no content to play: " + Resolve.canonicalPath(clip)
          + " — record something, or grow playEnd via set_clip_marker to give it a timeline");
    }
    if (clip.isRunning()) {
      throw Resolve.invalidArgument("Clip is already running: " + Resolve.canonicalPath(clip)
          + " — stop_clip first, or use launch_clip mode 'automation' to relaunch");
    }
    clip.playFrom(from != null ? from : clip.getCursor().clone());
    if (!clip.isRunning()) {
      throw new IllegalStateException("playFrom did not start the clip");
    }
  }

  /**
   * Immediate stop, bypassing launch-quantization delay. Upstream {@code stop()} leaves a
   * pending quantized launch scheduled — it would fire after the "stop" — so both launch
   * triggers are cancelled explicitly; to an agent, stop means stop. Verified by read-back.
   */
  public static void stop(LXClip clip) {
    clip.launch.cancel();
    clip.launchAutomation.cancel();
    clip.stop();
    if (clip.isRunning() || clip.isPending()) {
      throw new IllegalStateException("stop left the clip running or pending");
    }
  }


  // ============================================================
  // Grid clip lifecycle + snapshot capture (issue #230). Everything here is
  // command-backed and undoable; the scene verbs (engine-wide, not clip-scoped) live in
  // {@link Scenes}.
  // ============================================================

  /**
   * Creates a clip in a grid slot via {@link LXCommand.Clip.Add} (undoable).
   *
   * <p>Two guards sit in front of the command, because upstream's behavior at both edges
   * is a silent surprise rather than an error:
   * <ul>
   * <li>{@code Clip.Add.perform} <em>removes</em> whatever clip already occupies the slot
   * before adding — a clobber. {@code replace} has to be asked for explicitly (the
   * {@code save_project overwrite} precedent); without it an occupied slot is an
   * {@code invalid_argument}.</li>
   * <li>{@code LXBus.getClip} returns null for any index at or past the engine's
   * {@code numScenes}, so a clip added beyond it is invisible to the grid, to scene
   * launch, and to the bus's own occupancy check. Rejected with the fix named.</li>
   * </ul>
   *
   * @param index 0-based scene row; the resulting clip path is 1-indexed ({@code index+1}).
   * @param enableSnapshot whether the new clip recalls a snapshot when launched. LX
   *     initializes the snapshot the moment the flag turns on, so passing true is itself
   *     a capture of the bus's state at creation; {@link #captureSnapshot} overwrites it
   *     later.
   */
  public static LXClip addClip(
      LX lx, LXBus bus, int index, boolean enableSnapshot, boolean replace) {
    int numScenes = lx.engine.clips.numScenes.getValuei();
    if (index < 0 || index >= numScenes) {
      throw Resolve.invalidArgument("Scene index " + index + " is outside the engine's "
          + numScenes + " scenes (0-" + (numScenes - 1) + ") — a clip added past it would "
          + "be invisible to the grid and to scene launch; raise /lx/clips/numScenes "
          + "with set_parameter first (max " + LXClipEngine.MAX_SCENES + ")");
    }
    LXClip existing = bus.getClip(index);
    if (existing != null && !replace) {
      throw Resolve.invalidArgument("Scene index " + index + " on "
          + Resolve.canonicalPath(bus) + " already holds a clip ("
          + Resolve.canonicalPath(existing) + ") — pass replace:true to overwrite it, or "
          + "capture into the existing clip with capture_clip");
    }
    Commands.perform(lx, new LXCommand.Clip.Add(bus, index, enableSnapshot));
    LXClip clip = bus.getClip(index);
    if (clip == null) {
      throw new IllegalStateException("Clip.Add did not add a clip at index " + index);
    }
    return clip;
  }

  /** Removes a grid clip via {@link LXCommand.Clip.Remove} (undoable). */
  public static void removeClip(LX lx, LXClip clip) {
    if (clip.isComposition()) {
      throw Resolve.invalidArgument("The arrange composition is not removable: "
          + Resolve.canonicalPath(clip) + " — remove_clip takes a grid clip "
          + "(/lx/mixer/channel/N/clip/M)");
    }
    LXClipContainer container = clip.container;
    Commands.perform(lx, new LXCommand.Clip.Remove(clip));
    if (container.getClips().contains(clip)) {
      throw new IllegalStateException(
          "Clip.Remove did not remove " + Resolve.canonicalPath(clip));
    }
  }

  /**
   * Captures the current live state into the clip's snapshot via
   * {@link LXCommand.Snapshots.Update} (undoable) — the write side of the snapshot that
   * {@code launch_clip} mode {@code launch} already recalls.
   *
   * <p>A clip snapshot is <em>bus-scoped</em>, not a whole-show capture:
   * {@code LXClipSnapshot.initializeViews} calls {@code initializeClipBus}, which stores
   * the owning bus's active pattern (or every enabled pattern in blend mode), those
   * patterns' parameters, and its effects. It deliberately does not store the channel
   * fader, crossfade group, or composite mode — those belong to
   * {@code initializeGlobalBus}, which only a global snapshot ({@code add_snapshot}) runs.
   *
   * <p>Recall is gated on the clip's {@code snapshotEnabled}: capturing into a clip with
   * it off produces a snapshot that never fires. Rather than leave that silent, the flag
   * is turned on when needed — as its own {@link LXCommand.Parameter.SetNormalized}, so
   * undoing a capture that also had to enable recall takes two steps.
   *
   * @return whether snapshot recall had to be enabled as part of this capture.
   */
  public static boolean captureSnapshot(LX lx, LXClip clip) {
    if (clip.isComposition()) {
      // The composition's snapshot initializes to zero views (it has no owning bus to
      // scope a capture to), so capturing into it stores nothing while still flipping
      // snapshotEnabled on — a success response for a mutation that can never recall.
      throw Resolve.invalidArgument("The arrange composition has no bus to capture: "
          + Resolve.canonicalPath(clip) + " — capture_clip takes a grid clip "
          + "(/lx/mixer/channel/N/clip/M); for a whole-mixer capture use add_snapshot");
    }
    boolean enabledRecall = false;
    if (!clip.snapshotEnabled.isOn()) {
      Commands.perform(lx,
          new LXCommand.Parameter.SetNormalized(clip.snapshotEnabled, true));
      enabledRecall = true;
    }
    Commands.perform(lx, new LXCommand.Snapshots.Update(clip.snapshot));
    return enabledRecall;
  }

  private static void requireRunningOrPending(LXClip clip, String what) {
    if (!clip.isRunning() && !clip.isPending()) {
      throw new IllegalStateException(what + " left the clip neither running nor pending");
    }
  }
}
