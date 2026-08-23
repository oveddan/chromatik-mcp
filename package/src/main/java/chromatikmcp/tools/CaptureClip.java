package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.clip.LXClip;

import chromatikmcp.domain.Clips;
import chromatikmcp.domain.Resolve;

public final class CaptureClip implements LxTool {

  @Override
  public String name() {
    return "capture_clip";
  }

  @Override
  public String description() {
    return "Capture the current live state into a clip's snapshot, overwriting whatever "
        + "it held — the write side of the snapshot that launch_clip mode 'launch' "
        + "recalls. This is how a clip becomes a preset without placing automation points "
        + "one at a time. A clip snapshot is BUS-SCOPED, not a whole-show capture: it "
        + "stores the owning bus's active pattern (or every enabled pattern in blend "
        + "mode), those patterns' parameters, and its effects. It does NOT store the "
        + "channel fader, crossfade group, or composite mode — for a whole-mixer capture "
        + "use add_snapshot/update_snapshot instead, and for a fader use an automation "
        + "lane. Recall is gated on the clip's snapshotEnabled, so capturing into a clip "
        + "with it off silently produces a snapshot that never fires — this turns the "
        + "flag on instead and reports enabledRecall:true when it did, which costs a "
        + "second Cmd-Z to undo. Returns the clip state read back after the capture; "
        + "snapshotViewCount is how many parameter values were stored. path must be a "
        + "grid clip (/lx/mixer/channel/N/clip/M) — the arrange composition has no "
        + "owning bus to scope a capture to and is rejected. Undoable with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the clip to capture into, e.g. /lx/mixer/channel/1/clip/1"));
    return Schemas.object(properties, List.of("path"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    LXClip clip = Resolve.component(lx, Args.requireString(args, "path"), LXClip.class);
    boolean enabledRecall = Clips.captureSnapshot(lx, clip);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("enabledRecall", enabledRecall);
    payload.putAll(Payloads.clip(Clips.describe(clip)));
    return Result.ok(payload);
  }
}
