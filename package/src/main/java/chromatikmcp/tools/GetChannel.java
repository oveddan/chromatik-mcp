package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.mixer.LXMasterBus;

import chromatikmcp.domain.Channels;
import chromatikmcp.domain.Resolve;

public final class GetChannel implements LxTool {

  @Override
  public String name() {
    return "get_channel";
  }

  @Override
  public String description() {
    return "One channel's (or the master bus's) full detail — exactly the shape "
        + "list_channels reports per entry at 'detail: full', for just this bus. Use this "
        + "instead of scanning list_channels when you already know the path (e.g. from a "
        + "prior list_channels/get_project_info call): O(1) against this one channel rather "
        + "than assembling every channel's patterns/effects/controls to discard the rest. "
        + "Accepts a channel, group, or the master bus's canonical path (e.g. "
        + "/lx/mixer/channel/1, /lx/mixer/master).";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the channel, group, or master bus, e.g. /lx/mixer/channel/1 or "
            + "/lx/mixer/master"));
    return Schemas.object(properties, List.of("path"));
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    Object pathArg = args.get("path");
    if (!(pathArg instanceof String path) || path.isEmpty()) {
      return Result.error(Result.INVALID_ARGUMENT, "path must be a non-empty string");
    }
    LXBus bus = Resolve.component(lx, path, LXBus.class);
    if (bus instanceof LXAbstractChannel channel) {
      return Result.ok(ListChannels.channelFull(Channels.describe(channel)));
    }
    if (bus instanceof LXMasterBus master) {
      return Result.ok(ListChannels.masterFull(Channels.describeMaster(master)));
    }
    // LXBus has exactly two direct subtypes in LX 1.2.2 (LXAbstractChannel, LXMasterBus)
    // — verified against the pinned LX source. A third would land here as an explicit
    // failure rather than silently falling through with the wrong payload shape.
    return Result.error(Result.INVALID_ARGUMENT,
        "Unsupported bus type at path: " + path + " (found " + bus.getClass().getSimpleName() + ")");
  }
}
