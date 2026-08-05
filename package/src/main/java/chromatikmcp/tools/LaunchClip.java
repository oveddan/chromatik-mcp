package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.clip.Cursor;
import heronarts.lx.clip.LXClip;

import chromatikmcp.domain.Clips;
import chromatikmcp.domain.Cursors;
import chromatikmcp.domain.Resolve;

public final class LaunchClip implements LxTool {

  @Override
  public String name() {
    return "launch_clip";
  }

  @Override
  public String description() {
    return "Start clip playback. mode 'play' (the default) is immediate and unquantized, "
        + "from the 'from' cursor or the current playhead — it requires the clip to have "
        + "content (a fresh composition has none until something is recorded or playEnd "
        + "is pushed out with set_clip_marker) and to not already be running. mode "
        + "'automation' launches automation playback subject to the global launch "
        + "quantization, from 'from' or the playStart marker — when quantization is set "
        + "the response shows pending:true and running flips on the quantization "
        + "boundary. mode 'launch' is the full quantized grid-style launch from "
        + "playStart, which also recalls the clip's snapshot if enabled; it does not "
        + "accept 'from'. path defaults to the arrange composition "
        + "(/lx/timeline/composition) and also accepts a grid clip "
        + "(/lx/mixer/channel/N/clip/M). Returns the clip state read back after the "
        + "call (running, pending, playhead). Transport is not an LXCommand upstream — "
        + "Not undoable with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the clip — the composition (default: /lx/timeline/composition) "
            + "or a grid clip (/lx/mixer/channel/N/clip/M)"));
    properties.put("mode", Schemas.enumString(
        "play = immediate unquantized playback from 'from' or the playhead (default); "
            + "automation = quantized automation launch from 'from' or playStart; "
            + "launch = quantized grid-style launch from playStart with snapshot recall",
        List.of("play", "automation", "launch")));
    properties.put("from", Schemas.cursor(
        "Position to start playback from (play/automation modes only)."));
    return Schemas.object(properties, List.of());
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    LXClip clip = Clips.resolve(lx, Args.optionalString(args, "path"));
    String mode = Args.optionalString(args, "mode");
    if (mode == null) {
      mode = "play";
    }
    Map<String, Object> fromSpec = Args.optionalMap(args, "from");
    Cursor from = (fromSpec == null) ? null : Cursors.parse(clip, fromSpec);
    switch (mode) {
      case "play" -> Clips.play(clip, from);
      case "automation" -> Clips.launchAutomation(clip, from);
      case "launch" -> {
        if (from != null) {
          throw Resolve.invalidArgument("mode 'launch' always starts from the playStart "
              + "marker — use mode 'play' or 'automation' to start from a cursor");
        }
        Clips.launch(clip);
      }
      default -> throw Resolve.invalidArgument(
          "Unknown mode '" + mode + "' — expected play, automation, or launch");
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("mode", mode);
    payload.putAll(Payloads.clip(Clips.describe(clip)));
    return Result.ok(payload);
  }
}
