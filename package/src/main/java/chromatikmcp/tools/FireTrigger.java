package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Parameters;

public final class FireTrigger implements LxTool {

  @Override
  public String name() {
    return "fire_trigger";
  }

  @Override
  public String description() {
    return "Fire a momentary trigger by its canonical path — a TriggerParameter, or a "
        + "momentary boolean like a MacroTriggers macro (the pulse's rising edge fires any "
        + "wired trigger modulations). The value auto-resets to false. If launch "
        + "quantization defers the fire (pattern/clip launch), the response has "
        + "pending=true and it fires at the next tempo boundary — do NOT re-fire, that "
        + "queues a duplicate. Firing is an action with side effects, not undoable state; "
        + "use set_parameter for toggles and values.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Schemas.object(
        Map.of("path", Schemas.string("Canonical LX path of the trigger parameter")),
        List.of("path"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    if (!(args.get("path") instanceof String path)) {
      return Result.error(Result.INVALID_ARGUMENT, "Required string argument: path");
    }
    Parameters.FireInfo fire = Parameters.fire(lx, path);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", fire.parameter().path());
    payload.put("fired", !fire.pending());
    if (fire.pending()) {
      payload.put("pending", true);
    }
    payload.put("value", fire.parameter().value());
    return Result.ok(payload);
  }
}
