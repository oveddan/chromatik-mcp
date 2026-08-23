package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Scenes;

public final class LaunchScene implements LxTool {

  @Override
  public String name() {
    return "launch_scene";
  }

  @Override
  public String description() {
    return "Fire a whole row of the clip grid at once — every clip at that index across "
        + "all channels plus the master bus. This is what makes a chapter land "
        + "simultaneously; launching its clips one at a time loses that. index is the "
        + "0-based scene row, matching add_clip (index 0 fires the clips at "
        + "/lx/mixer/channel/N/clip/1). By default the launch is subject to the global "
        + "launch quantization — clips come back pending:true and flip to running on the "
        + "quantization boundary — while immediate:true fires now. Each launched clip "
        + "recalls its own snapshot if enabled (see capture_clip). A row no bus holds a "
        + "clip on is rejected with invalid_argument rather than silently cancelled, "
        + "which is what LX does on its own. Returns every clip on the row with its "
        + "running/pending state read back after the call. A quantized launch also "
        + "cancels any other scene still pending; immediate:true does NOT — it fires "
        + "the clips directly, so a scene pending from an earlier quantized launch "
        + "still lands afterwards. Transport is not an LXCommand upstream — "
        + "Not undoable with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("index", Schemas.integer(
        "0-based scene row to launch; must be below the engine's /lx/clips/numScenes",
        0, Integer.MAX_VALUE));
    properties.put("immediate", Schemas.bool(
        "Fire now, bypassing the global launch quantization (default false)"));
    return Schemas.object(properties, List.of("index"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    int index = Args.requireInt(args, "index");
    boolean immediate = Args.optionalBoolean(args, "immediate", false);
    return Result.ok(Payloads.scene(Scenes.launch(lx, index, immediate)));
  }
}
