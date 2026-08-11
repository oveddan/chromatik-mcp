package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Channels;

public final class UngroupChannel implements LxTool {

  @Override
  public String name() {
    return "ungroup_channel";
  }

  @Override
  public String description() {
    return "Pull one member channel out of its mixer group and place it immediately after the "
        + "remaining group span. Removing the last member leaves an empty group bus; call "
        + "ungroup_channels on that bus to dissolve it. If the member has main or aux focus, "
        + "LX follows it to its new index. Returns invalid_argument if the channel is not grouped. The "
        + "operation shifts positional channel paths, including descendants; the response reports "
        + "every changed canonical path in oscChanges, and callers should re-list channels before "
        + "reusing cached paths. Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of a channel currently in a group"));
    return Schemas.object(properties, List.of("path"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    Channels.UngroupChannelResult result =
        Channels.ungroupChannel(lx, Args.requireString(args, "path"));
    Map<String, Object> payload = ChannelPayload.channel(result.channel());
    payload.put("formerGroupPath", result.groupPath());
    payload.put("groupId", result.groupId());
    payload.put("oscChanges", OscChanges.payload(result.oscChanges()));
    return Result.ok(payload);
  }
}
