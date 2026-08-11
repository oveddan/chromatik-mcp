package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Channels;

public final class UngroupChannels implements LxTool {

  @Override
  public String name() {
    return "ungroup_channels";
  }

  @Override
  public String description() {
    return "Dissolve a mixer group by its canonical path, leaving all members as top-level "
        + "channels. Returns the removed group's id and former path plus each freed channel's "
        + "current path. Dissolving shifts positional channel paths, including descendants; "
        + "the response reports every changed canonical path in oscChanges, and callers should "
        + "re-list channels before reusing cached paths. Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the group to dissolve, e.g. /lx/mixer/channel/1"));
    return Schemas.object(properties, List.of("path"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    Channels.UngroupChannelsResult result =
        Channels.ungroupChannels(lx, Args.requireString(args, "path"));
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("removedGroupPath", result.groupPath());
    payload.put("groupId", result.groupId());
    payload.put("channels", ChannelGroupingPayload.channels(result.channels()));
    payload.put("oscChanges", OscChanges.payload(result.oscChanges()));
    return Result.ok(payload);
  }
}
