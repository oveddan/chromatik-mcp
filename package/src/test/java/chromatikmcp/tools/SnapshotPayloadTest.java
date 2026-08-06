package chromatikmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import chromatikmcp.domain.Snapshots;

class SnapshotPayloadTest {

  @Test
  void serializesEverySnapshotInfoField() {
    Snapshots.SnapshotInfo snapshot = new Snapshots.SnapshotInfo(
        "/lx/snapshots/snapshot/1", 42, "Look", 1.5, true);

    assertEquals(Map.of(
        "path", snapshot.path(),
        "id", snapshot.id(),
        "label", snapshot.label(),
        "transitionTimeSecs", snapshot.transitionTimeSecs(),
        "hasCustomTransitionTime", snapshot.hasCustomTransitionTime()),
        SnapshotPayload.toMap(snapshot));
  }
}
