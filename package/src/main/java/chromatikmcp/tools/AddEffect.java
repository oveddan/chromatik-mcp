package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.effect.LXEffect;

import chromatikmcp.domain.Channels;

public final class AddEffect implements LxTool {

  @Override
  public String name() {
    return "add_effect";
  }

  @Override
  public String description() {
    return "Add an effect by class name (from list_available_effects — either the full "
        + "class name or the short name it lists) to a channel, master bus, or pattern. "
        + "The container must be a channel path (e.g. /lx/mixer/channel/1), the master bus "
        + "path, or a pattern path (e.g. /lx/mixer/channel/1/pattern/1). Undoable in "
        + "Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("container", Schemas.string(
        "Canonical path of the channel, master bus, or pattern to add the effect to"));
    properties.put("type", Schemas.string(
        "Effect class name, as returned by list_available_effects — full class name or "
            + "short name"));
    return Schemas.object(properties, List.of("container", "type"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    if (!(args.get("container") instanceof String containerPath)) {
      return Result.error(Result.INVALID_ARGUMENT, "Required string argument: container");
    }
    if (!(args.get("type") instanceof String typeName)) {
      return Result.error(Result.INVALID_ARGUMENT, "Required string argument: type");
    }
    Class<? extends LXEffect> effectClass = Channels.resolveEffectClass(lx, typeName);
    LXEffect effect = Channels.addEffect(lx, containerPath, effectClass);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", effect.getCanonicalPath());
    payload.put("id", effect.getId());
    payload.put("label", effect.getLabel());
    payload.put("class", effect.getClass().getName());
    payload.put("enabled", effect.enabled.isOn());
    return Result.ok(payload);
  }
}
