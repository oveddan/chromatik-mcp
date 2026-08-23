package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.clip.LXClip;
import heronarts.lx.mixer.LXBus;

import chromatikmcp.domain.Clips;
import chromatikmcp.domain.Resolve;

public final class AddClip implements LxTool {

  @Override
  public String name() {
    return "add_clip";
  }

  @Override
  public String description() {
    return "Create a clip in an empty grid slot — the verb that brings a slot into "
        + "being so every other clip tool can address it. containerPath is the bus that "
        + "owns the row (a channel like /lx/mixer/channel/1, or /lx/mixer/master); index "
        + "is the 0-based scene row, and the resulting clip path is 1-indexed, so index 0 "
        + "becomes /lx/mixer/channel/1/clip/1. An occupied slot is an invalid_argument "
        + "naming the existing clip unless replace:true is passed (the save_project "
        + "overwrite precedent) — replacing discards the old clip's automation and "
        + "snapshot in a single undo step. An index at or past the engine's numScenes is "
        + "rejected: LX hides such clips from the grid and from launch_scene, so raise "
        + "/lx/clips/numScenes with set_parameter first. snapshot (default true) makes "
        + "the clip recall a snapshot when launched, and LX captures the bus's live "
        + "state into it right then — so add_clip with snapshot:true is already a "
        + "capture of that moment, and capture_clip is how you overwrite it later. With "
        + "snapshot:false the clip stores nothing (snapshotViewCount 0) until "
        + "capture_clip runs. Either way a new clip has no automation content. "
        + "Undoable with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("containerPath", Schemas.string(
        "Canonical path of the bus that owns the grid row — a channel "
            + "(/lx/mixer/channel/N) or /lx/mixer/master"));
    properties.put("index", Schemas.integer(
        "0-based scene row; the clip's path is 1-indexed (index 0 -> .../clip/1). Must be "
            + "below the engine's /lx/clips/numScenes",
        0, Integer.MAX_VALUE));
    properties.put("snapshot", Schemas.bool(
        "Whether launching the clip recalls its snapshot (default true) — capture_clip "
            + "writes that snapshot"));
    properties.put("replace", Schemas.bool(
        "Overwrite a clip already in the slot (default false); without it an occupied "
            + "slot is rejected"));
    properties.put("label", Schemas.string(
        "Optional label for the new clip; defaults to LX's <bus>-<row> naming"));
    return Schemas.object(properties, List.of("containerPath", "index"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    LXBus bus = Resolve.component(lx, Args.requireString(args, "containerPath"), LXBus.class);
    int index = Args.requireInt(args, "index");
    boolean snapshot = Args.optionalBoolean(args, "snapshot", true);
    boolean replace = Args.optionalBoolean(args, "replace", false);
    String label = Args.optionalString(args, "label");

    LXClip clip = Clips.addClip(lx, bus, index, snapshot, replace);
    if (label != null) {
      // Direct set, not a second command: undoing the add removes the whole clip, so
      // there is nothing left for a separate rename entry to restore (the
      // Snapshots.addSnapshot / Views.addView pattern).
      clip.label.setValue(label);
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("containerPath", Resolve.canonicalPath(bus));
    payload.put("index", clip.getIndex());
    payload.putAll(Payloads.clip(Clips.describe(clip)));
    return Result.ok(payload);
  }
}
