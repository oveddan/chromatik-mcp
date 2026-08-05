package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.clip.LXClip;

import chromatikmcp.domain.Clips;

public final class StopClip implements LxTool {

  @Override
  public String name() {
    return "stop_clip";
  }

  @Override
  public String description() {
    return "Stop clip playback immediately, bypassing any launch-quantization delay; "
        + "also cancels a pending quantized launch. Safe to call on a stopped clip "
        + "(no-op). path defaults to the arrange composition (/lx/timeline/composition) "
        + "and also accepts a grid clip (/lx/mixer/channel/N/clip/M). Returns the clip "
        + "state read back after the call (running, pending, playhead — the playhead "
        + "stays where playback halted). Transport is not an LXCommand upstream — "
        + "Not undoable with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the clip — the composition (default: /lx/timeline/composition) "
            + "or a grid clip (/lx/mixer/channel/N/clip/M)"));
    return Schemas.object(properties, List.of());
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    LXClip clip = Clips.resolve(lx, Args.optionalString(args, "path"));
    Clips.stop(clip);
    return Result.ok(Payloads.clip(Clips.describe(clip)));
  }
}
