package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.clip.LXClip;

import chromatikmcp.domain.Clips;
import chromatikmcp.domain.Resolve;

public final class RemoveClip implements LxTool {

  @Override
  public String name() {
    return "remove_clip";
  }

  @Override
  public String description() {
    return "Remove a grid clip, emptying its slot — its automation lanes, notes, and "
        + "snapshot go with it. path is a grid clip (/lx/mixer/channel/N/clip/M); the "
        + "arrange composition (/lx/timeline/composition) is not removable and is "
        + "rejected. Slots do NOT reindex: removing .../clip/2 leaves .../clip/1 and "
        + ".../clip/3 where they were, because a grid row is an address, not a list "
        + "position. Returns the shared remove-tool shape (removed: the clip's path, "
        + "kind: \"clip\") plus the freed slot's containerPath, index, and the label the "
        + "clip had. Undoable with Cmd-Z, which restores the clip's full contents.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the grid clip to remove, e.g. /lx/mixer/channel/1/clip/1"));
    return Schemas.object(properties, List.of("path"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String path = Args.requireString(args, "path");
    LXClip clip = Resolve.component(lx, path, LXClip.class);

    // Identity of what's about to go away — unreadable after the removal commits.
    String removedPath = Resolve.canonicalPath(clip);
    int index = clip.getIndex();
    String label = clip.getLabel();
    String containerPath = Resolve.canonicalPath(clip.container.asComponent());

    Clips.removeClip(lx, clip);

    // The shared remove_* shape ({removed, kind}) comes first so generic dispatchers keep
    // working; the freed row's coordinates ride alongside as extra keys, the way
    // RemovalPayload.color carries its swatch.
    Map<String, Object> payload = RemovalPayload.of(removedPath, "clip");
    payload.put("containerPath", containerPath);
    payload.put("index", index);
    payload.put("label", label);
    return Result.ok(payload);
  }
}
