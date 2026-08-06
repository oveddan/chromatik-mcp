package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import chromatikmcp.domain.Snapshots;

/** Wire serializer for a saved global snapshot. */
public final class SnapshotPayload {

  private SnapshotPayload() {}

  public static Map<String, Object> toMap(Snapshots.SnapshotInfo snapshot) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", snapshot.path());
    payload.put("id", snapshot.id());
    payload.put("label", snapshot.label());
    payload.put("transitionTimeSecs", snapshot.transitionTimeSecs());
    payload.put("hasCustomTransitionTime", snapshot.hasCustomTransitionTime());
    return payload;
  }
}
