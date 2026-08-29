package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.effect.LXEffect;

import chromatikmcp.domain.Channels;

public final class MoveEffect implements LxTool {

  @Override
  public String name() {
    return "move_effect";
  }

  @Override
  public String description() {
    return "Move an effect: to a new 0-based index within its container (channel, bus, or "
        + "pattern), or — by passing 'containerPath' — into a different container entirely. "
        + "A cross-container move is a true move, not a duplicate: the effect keeps its id, and "
        + "its MIDI mappings, snapshot views and clip lanes follow it. Modulations and "
        + "triggers follow it only while they stay in scope: moving between two containers "
        + "on the same channel (e.g. the channel's own chain to one of its patterns) keeps "
        + "that channel's wiring, but moving to another channel DESTROYS it, because the "
        + "wiring can no longer reach the effect. The response's droppedWiring array lists "
        + "exactly what the move destroyed (kind, scope, sourcePath, targetPath) — empty on "
        + "an in-container reorder. Undo restores it. To duplicate rather than relocate, use "
        + "copy_effect. "
        + "Moving shifts the 1-based paths of the moved effect, any sibling it crosses, and any "
        + "device-local modulators/modulations/triggers those siblings own — re-list rather than "
        + "reusing cached paths; the response's oscChanges array reports exactly which canonical "
        + "paths changed (componentId, before, after). It reports changes only, not components "
        + "removed during the move. "
        + "Returns invalid_argument if the index is out of range, or if containerPath is the "
        + "effect's current container (omit it to reorder in place). Undoable in Chromatik with "
        + "Cmd-Z, which a human can trigger outside this session's control; an undo inverts "
        + "every path in oscChanges with no separate signal, so re-list after any move if undo "
        + "is possible.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the effect to move, e.g. /lx/mixer/channel/1/effect/1"));
    properties.put("index", Schemas.integer(
        "0-based destination index within the effect list", Integer.MIN_VALUE, Integer.MAX_VALUE));
    properties.put("containerPath", Schemas.string(
        "Optional canonical path of a different destination container (channel, master bus, "
            + "or pattern); omit to reorder within the effect's current container"));
    return Schemas.object(properties, List.of("path", "index"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String path = Args.requireString(args, "path");
    int index = Args.requireInt(args, "index");
    String containerPath = Args.optionalString(args, "containerPath",
        "containerPath must be a string path");
    Channels.EffectMoveResult result = Channels.moveEffect(lx, path, containerPath, index);
    LXEffect effect = result.effect();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", effect.getCanonicalPath());
    payload.put("id", effect.getId());
    payload.put("label", effect.getLabel());
    payload.put("index", effect.getIndex());
    payload.put("oscChanges", OscChanges.payload(result.oscChanges()));
    payload.put("droppedWiring", Wiring.payload(result.droppedWiring()));
    return Result.ok(payload);
  }
}
