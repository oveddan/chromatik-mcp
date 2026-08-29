package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.effect.LXEffect;

import chromatikmcp.domain.Channels;

public final class CopyEffect implements LxTool {

  @Override
  public String name() {
    return "copy_effect";
  }

  @Override
  public String description() {
    return "Copy a configured effect into any channel, the master bus, or a pattern "
        + "('containerPath') — unlike add_effect, which instantiates a blank one. The copy "
        + "carries the source's parameter values and its own device-local "
        + "modulators/modulations/triggers, rewired to the copy. It does NOT carry wiring "
        + "held outside the effect — channel-level and global modulations and triggers, MIDI "
        + "mappings, snapshot views — which address the source specifically and stay on it; "
        + "every such reference is listed in the response's unreplicatedWiring array (kind, "
        + "scope, sourcePath, targetPath), including clip automation lanes, and for MIDI "
        + "mappings the type/channel/number needed to rebuild them. To relocate rather than "
        + "duplicate, prefer "
        + "move_effect with containerPath: that is a true move and keeps MIDI mappings and "
        + "snapshot views attached. The copy always lands at the end of the destination's "
        + "effect chain — LX's add-effect command takes no index; follow with move_effect to "
        + "position it. Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the effect to copy, e.g. /lx/mixer/channel/1/effect/1"));
    properties.put("containerPath", Schemas.string(
        "Canonical path of the destination channel, master bus, or pattern, e.g. "
            + "/lx/mixer/channel/2, /lx/mixer/master, or /lx/mixer/channel/2/pattern/1; may "
            + "be the source's own container to duplicate in place"));
    return Schemas.object(properties, List.of("path", "containerPath"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String path = Args.requireString(args, "path");
    String containerPath = Args.requireString(args, "containerPath");
    Channels.EffectCopyResult result = Channels.copyEffect(lx, path, containerPath);
    LXEffect effect = result.effect();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", effect.getCanonicalPath());
    payload.put("id", effect.getId());
    payload.put("label", effect.getLabel());
    payload.put("class", effect.getClass().getName());
    payload.put("index", effect.getIndex());
    payload.put("unreplicatedWiring", Wiring.payload(result.unreplicatedWiring()));
    return Result.ok(payload);
  }
}
