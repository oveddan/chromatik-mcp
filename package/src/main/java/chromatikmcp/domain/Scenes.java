package chromatikmcp.domain;

import java.util.ArrayList;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.clip.LXClip;
import heronarts.lx.clip.LXClipEngine;
import heronarts.lx.mixer.LXBus;

/**
 * Scene primitives — a scene is one row of the clip grid: the clips sitting at the same
 * index across every channel plus the master bus. Firing the row is what makes a "chapter"
 * land simultaneously instead of channel by channel.
 *
 * <p>Scene launch is a direct engine edit, like the rest of clip transport: LX ships no
 * {@code LXCommand} for it (play state is not part of the undo story), so none of this is
 * undoable. Clip-scoped primitives live in {@link Clips}.
 */
public final class Scenes {

  private Scenes() {}

  /** One clip a scene launch touched, read back after the call. */
  public record SceneClip(String path, String label, boolean running, boolean pending) {}

  /** The scene row: its index and every clip on it. */
  public record SceneState(int index, List<SceneClip> clips) {}

  /**
   * Launches every clip on scene row {@code index}.
   *
   * <p>{@code immediate} picks between the engine's two entry points:
   * {@code launchScene} is subject to the global launch quantization (clips report
   * {@code pending} until the boundary), {@code triggerScene} fires now.
   *
   * <p>Upstream turns an empty row into a silent cancel — {@code _launchClipSceneScheduled}
   * calls {@code scenes[index].cancel()} when no bus holds a clip there, so the call looks
   * like it worked and nothing happens. That is checked first and raised as a typed error
   * instead.
   */
  public static SceneState launch(LX lx, int index, boolean immediate) {
    List<LXClip> clips = clipsOnRow(lx, requireSceneIndex(lx, index));
    if (clips.isEmpty()) {
      throw Resolve.invalidArgument("No clips on scene " + index
          + " — no bus holds a clip at that row, and LX silently cancels a launch of an "
          + "empty scene; create one with add_clip first");
    }
    if (immediate) {
      lx.engine.clips.triggerScene(index);
    } else {
      lx.engine.clips.launchScene(index);
    }
    return describe(lx, index);
  }

  /** Snapshots scene row {@code index} — every clip on it with its transport state. */
  public static SceneState describe(LX lx, int index) {
    List<SceneClip> clips = new ArrayList<>();
    for (LXClip clip : clipsOnRow(lx, requireSceneIndex(lx, index))) {
      clips.add(new SceneClip(
          Resolve.canonicalPath(clip),
          clip.getLabel(),
          clip.isRunning(),
          clip.isPending()));
    }
    return new SceneState(index, clips);
  }

  /**
   * Bounds the row against the engine's live {@code numScenes} rather than
   * {@link LXClipEngine#MAX_SCENES}: {@code LXBus.getClip} returns null past
   * {@code numScenes}, so rows beyond it can hold nothing regardless of the scene
   * parameter array's full length.
   */
  private static int requireSceneIndex(LX lx, int index) {
    int numScenes = lx.engine.clips.numScenes.getValuei();
    if (index < 0 || index >= numScenes) {
      throw Resolve.invalidArgument("Scene index " + index + " is outside the engine's "
          + numScenes + " scenes (0-" + (numScenes - 1) + ") — raise /lx/clips/numScenes "
          + "with set_parameter to reach further rows (max " + LXClipEngine.MAX_SCENES + ")");
    }
    return index;
  }

  /** Every clip at {@code index}, channels in mixer order then the master bus. */
  private static List<LXClip> clipsOnRow(LX lx, int index) {
    List<LXClip> clips = new ArrayList<>();
    for (LXBus bus : lx.engine.mixer.channels) {
      LXClip clip = bus.getClip(index);
      if (clip != null) {
        clips.add(clip);
      }
    }
    LXClip masterClip = lx.engine.mixer.masterBus.getClip(index);
    if (masterClip != null) {
      clips.add(masterClip);
    }
    return clips;
  }
}
