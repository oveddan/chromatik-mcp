package lxmcp.domain;

import java.util.ArrayList;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.command.LXCommand;
import heronarts.lx.snapshot.LXGlobalSnapshot;
import heronarts.lx.snapshot.LXSnapshot;
import heronarts.lx.snapshot.LXSnapshotEngine;

/**
 * Mutations on the global snapshot engine ({@code lx.engine.snapshots}) — whole-look
 * captures of mixer/pattern/effect/modulation state, recalled with an optional fade. Every
 * mutation routes through {@code LXCommand} for undo. Call on the engine thread.
 */
public final class Snapshots {

  private Snapshots() {}

  public record SnapshotInfo(String path, int id, String label, double transitionTimeSecs,
      boolean hasCustomTransitionTime) {}

  /** The engine's saved snapshots plus its recall-scoping and transition settings. */
  public record EngineInfo(List<SnapshotInfo> snapshots, List<Parameters.ParameterInfo> settings) {}

  /** Snapshot the engine's live snapshot list and settings; call on the engine thread. */
  public static EngineInfo list(LX lx) {
    LXSnapshotEngine engine = lx.engine.snapshots;
    List<SnapshotInfo> snapshots = new ArrayList<>();
    for (LXGlobalSnapshot snapshot : engine.snapshots) {
      snapshots.add(describe(snapshot));
    }
    List<Parameters.ParameterInfo> settings = List.of(
        Parameters.describe(engine.recallMixer),
        Parameters.describe(engine.recallPattern),
        Parameters.describe(engine.recallEffect),
        Parameters.describe(engine.recallModulation),
        Parameters.describe(engine.recallMaster),
        Parameters.describe(engine.recallOutput),
        Parameters.describe(engine.transitionEnabled),
        Parameters.describe(engine.transitionTimeSecs));
    return new EngineInfo(snapshots, settings);
  }

  /**
   * Capture the current state as a new snapshot, appended to the end of the list.
   * {@code label} overrides the default {@code "Snapshot-N"} label LX assigns; the rename
   * itself is a direct field set, not a separate command — undoing the add removes the
   * whole component regardless, so there is nothing further for Cmd-Z to restore (same
   * pattern as {@link Views#addView}).
   */
  public static LXGlobalSnapshot addSnapshot(LX lx, String label) {
    List<LXGlobalSnapshot> snapshots = lx.engine.snapshots.snapshots;
    int before = snapshots.size();
    Commands.perform(lx, new LXCommand.Snapshots.AddSnapshot());
    if (snapshots.size() != before + 1) {
      throw new IllegalStateException("AddSnapshot did not add a snapshot");
    }
    LXGlobalSnapshot snapshot = snapshots.get(before);
    if (label != null) {
      snapshot.label.setValue(label);
    }
    return snapshot;
  }

  /**
   * Recall a snapshot's captured state. Whether the recall fades or applies instantly is
   * normally governed by the engine's own {@code transitionEnabled}/{@code
   * transitionTimeSecs} settings; {@code immediate} forces an instant recall for this one
   * call by toggling {@code transitionEnabled} off around the command and restoring it
   * immediately after — LX has no separate immediate-recall command for a global snapshot
   * (only {@code LXCommand.Snapshots.RecallImmediate}, which targets clip snapshots).
   *
   * <p>The returned command is real (it lands on the undo stack), but its per-view undo
   * entries are unreliable for plain parameters: {@code LXSnapshotEngine.recall()} calls
   * {@code view.recall()} — mutating the live parameter to the snapshot's value — before
   * calling {@code view.getCommand()} to build the undo entry, so the generated {@code
   * LXCommand.Parameter.SetValue} captures its "original" value from the already-mutated
   * parameter (identical to the new value). This is LX's own ordering, not fixable here.
   *
   * @throws IllegalStateException if the recall was ignored — the only case LX defines is
   *     re-recalling a snapshot already mid-transition into itself.
   */
  public static void recall(LX lx, LXGlobalSnapshot snapshot, boolean immediate) {
    LXSnapshotEngine engine = lx.engine.snapshots;
    boolean wasEnabled = engine.transitionEnabled.isOn();
    if (immediate && wasEnabled) {
      engine.transitionEnabled.setValue(false);
    }
    try {
      Commands.perform(lx, new LXCommand.Snapshots.Recall(snapshot));
    } finally {
      if (immediate && wasEnabled) {
        engine.transitionEnabled.setValue(true);
      }
    }
  }

  /** Recapture the current state into an existing snapshot, overwriting its saved values. */
  public static void update(LX lx, LXSnapshot snapshot) {
    Commands.perform(lx, new LXCommand.Snapshots.Update(snapshot));
  }

  /** Remove a snapshot; remaining snapshots reindex afterwards. */
  public static void removeSnapshot(LX lx, LXGlobalSnapshot snapshot) {
    Commands.perform(lx, new LXCommand.Snapshots.RemoveSnapshot(snapshot));
    if (lx.engine.snapshots.snapshots.contains(snapshot)) {
      throw new IllegalStateException(
          "RemoveSnapshot did not remove " + snapshot.getCanonicalPath());
    }
  }

  private static SnapshotInfo describe(LXGlobalSnapshot snapshot) {
    return new SnapshotInfo(
        Resolve.canonicalPath(snapshot),
        snapshot.getId(),
        snapshot.getLabel(),
        snapshot.transitionTimeSecs.getValue(),
        snapshot.hasCustomTransitionTime.isOn());
  }
}
