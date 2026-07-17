package lxmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import lxmcp.HeadlessLxTest;

import heronarts.lx.LX;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.snapshot.LXGlobalSnapshot;

/**
 * Instantiates the qa-strategy verification template for snapshot mutations:
 * do → undo → assert restored. The undo assertion doubles as proof each primitive routes
 * through a real LXCommand rather than a direct edit.
 */
class SnapshotsTest extends HeadlessLxTest {

  @Test
  void listReportsEngineSettingsAndSnapshots() {
    LX lx = newHeadlessLx();

    Snapshots.EngineInfo info = Snapshots.list(lx);

    assertTrue(info.snapshots().isEmpty());
    assertEquals(8, info.settings().size());
  }

  @Test
  void addSnapshotCapturesCurrentStateAndDefaultsLabel() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    channel.fader.setValue(0.7);
    int before = lx.engine.snapshots.snapshots.size();

    LXGlobalSnapshot added = Snapshots.addSnapshot(lx, null);

    assertEquals(before + 1, lx.engine.snapshots.snapshots.size());
    assertEquals("Snapshot-1", added.getLabel());
    assertTrue(added.getCanonicalPath().startsWith("/lx/snapshots/snapshot/"));
  }

  @Test
  void addSnapshotAppliesCustomLabel() {
    LX lx = newHeadlessLx();

    LXGlobalSnapshot added = Snapshots.addSnapshot(lx, "My Look");

    assertEquals("My Look", added.getLabel());
  }

  @Test
  void addSnapshotUndoRemovesIt() {
    LX lx = newHeadlessLx();
    int before = lx.engine.snapshots.snapshots.size();

    LXGlobalSnapshot added = Snapshots.addSnapshot(lx, "Undo Me");
    assertEquals(before + 1, lx.engine.snapshots.snapshots.size());

    lx.command.undo();

    assertEquals(before, lx.engine.snapshots.snapshots.size());
    assertFalse(lx.engine.snapshots.snapshots.contains(added));
  }

  @Test
  void recallRestoresCapturedState() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    channel.fader.setValue(0.7);
    LXGlobalSnapshot snapshot = Snapshots.addSnapshot(lx, "Look");

    channel.fader.setValue(0.2);
    Snapshots.recall(lx, snapshot, true);

    assertEquals(0.7, channel.fader.getValue(), 1e-9);
  }

  @Test
  void recallIsCommandBackedButLxsPerViewUndoIsANoOpForPlainParameters() {
    // Documents an LX-side quirk rather than lx-mcp behavior: LXSnapshotEngine.recall()
    // calls view.recall() (mutating the live parameter to the snapshot's value) BEFORE
    // calling view.getCommand() to build the undo entry, so the generated
    // LXCommand.Parameter.SetValue captures its "original" value from the
    // *already-mutated* live parameter — identical to the new value. Recall is still a
    // real LXCommand (it lands on the undo stack, per this test), but Cmd-Z after a
    // recall does not restore a plain parameter's pre-recall value.
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    channel.fader.setValue(0.7);
    LXGlobalSnapshot snapshot = Snapshots.addSnapshot(lx, "Look");

    channel.fader.setValue(0.2);
    Snapshots.recall(lx, snapshot, true);
    assertEquals(0.7, channel.fader.getValue(), 1e-9);

    lx.command.undo();

    assertEquals(0.7, channel.fader.getValue(), 1e-9,
        "LX's generated undo command is a no-op here (see comment above) — not restored to 0.2");
  }

  @Test
  void recallImmediateRestoresTransitionEnabledAfterward() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    channel.fader.setValue(0.5);
    LXGlobalSnapshot snapshot = Snapshots.addSnapshot(lx, "Look");
    lx.engine.snapshots.transitionEnabled.setValue(true);

    channel.fader.setValue(0.1);
    Snapshots.recall(lx, snapshot, true);

    assertEquals(0.5, channel.fader.getValue(), 1e-9, "immediate bypasses the fade");
    assertTrue(lx.engine.snapshots.transitionEnabled.isOn(),
        "the engine setting is restored once the immediate recall completes");
  }

  @Test
  void updateRecapturesCurrentStateAndUndoRestoresPrevious() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    channel.fader.setValue(0.4);
    LXGlobalSnapshot snapshot = Snapshots.addSnapshot(lx, "Look");

    channel.fader.setValue(0.9);
    Snapshots.update(lx, snapshot);

    channel.fader.setValue(0.1);
    Snapshots.recall(lx, snapshot, true);
    assertEquals(0.9, channel.fader.getValue(), 1e-9, "update recaptured the new fader value");

    // Undo the Update itself (Update.undo reloads the snapshot's own prior serialized
    // state — unlike Recall, above, this one is not the getCommand()-after-mutate quirk).
    // The preceding recall already pushed its own (no-op) undo entry, so undo twice.
    lx.command.undo();
    lx.command.undo();
    channel.fader.setValue(0.1);
    Snapshots.recall(lx, snapshot, true);
    assertEquals(0.4, channel.fader.getValue(), 1e-9, "undoing update restores the original capture");
  }

  @Test
  void removeSnapshotDeletesItAndUndoRestoresIt() {
    LX lx = newHeadlessLx();
    LXGlobalSnapshot snapshot = Snapshots.addSnapshot(lx, "Doomed");
    int before = lx.engine.snapshots.snapshots.size();

    Snapshots.removeSnapshot(lx, snapshot);

    assertEquals(before - 1, lx.engine.snapshots.snapshots.size());
    assertFalse(lx.engine.snapshots.snapshots.contains(snapshot));

    lx.command.undo();

    assertEquals(before, lx.engine.snapshots.snapshots.size());
    assertTrue(lx.engine.snapshots.snapshots.contains(snapshot)
        || lx.engine.snapshots.snapshots.stream()
            .anyMatch(s -> "Doomed".equals(s.getLabel())),
        "undo restores an equivalent snapshot");
  }

  @Test
  void resolveByPathFindsTheSnapshot() {
    LX lx = newHeadlessLx();
    LXGlobalSnapshot snapshot = Snapshots.addSnapshot(lx, "Findable");

    LXGlobalSnapshot resolved =
        Resolve.component(lx, snapshot.getCanonicalPath(), LXGlobalSnapshot.class);

    assertNotNull(resolved);
    assertEquals(snapshot, resolved);
  }

  @Test
  void resolveUnknownSnapshotPathThrowsNotFound() {
    LX lx = newHeadlessLx();

    Resolve.ResolveException e = assertThrows(Resolve.ResolveException.class,
        () -> Resolve.component(lx, "/lx/snapshots/snapshot/99", LXGlobalSnapshot.class));
    assertEquals(Resolve.Failure.NOT_FOUND, e.failure);
  }
}
