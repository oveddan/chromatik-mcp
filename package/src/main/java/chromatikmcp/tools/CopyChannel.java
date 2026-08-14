package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.mixer.LXChannel;

import chromatikmcp.domain.Channels;

public final class CopyChannel implements LxTool {

  @Override
  public String name() {
    return "copy_channel";
  }

  @Override
  public String description() {
    return "Copy a whole channel into the mixer — patterns, their effects and nested racks, "
        + "the channel's own effects, its clips, its mixer settings (fader, blend mode, "
        + "crossfade group), and its channel-level modulators/modulations/triggers, all "
        + "rewired to the copy. This carries far more than copy_pattern does: channel-level "
        + "wiring lives inside the channel, so the modulations that copy_pattern has to leave "
        + "behind travel with a channel copy. Only global modulations (scope /lx/modulation), "
        + "MIDI mappings and snapshot views stay on the source; they are listed in the "
        + "response's unreplicatedWiring array (kind, scope, sourcePath, targetPath). "
        + "Groups cannot be copied — LX's add-channel only builds channels, so copying a "
        + "group would produce an empty channel wearing its label; copy the member channels "
        + "and regroup with group_channels, which is also how you re-group a copy: a copy of "
        + "a grouped channel lands at the top level, never inside the source's group (honoring "
        + "the serialized membership would break LX's rule that a group's members sit "
        + "contiguously behind it), and the response sets groupMembershipDropped: true so you "
        + "know to regroup. Pass an optional 0-based index to insert at a mixer position; omit "
        + "to append. Inserting shifts the 1-based paths of later channels — re-list rather "
        + "than reusing cached paths. Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the channel to copy, e.g. /lx/mixer/channel/1"));
    properties.put("index", Schemas.integer(
        "0-based mixer index to insert at; omit to append at the end",
        Integer.MIN_VALUE, Integer.MAX_VALUE));
    return Schemas.object(properties, List.of("path"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String path = Args.requireString(args, "path");
    int index = Args.optionalInt(args, "index", -1);
    Channels.ChannelCopyResult result = Channels.copyChannel(lx, path, index);
    LXChannel channel = result.channel();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", channel.getCanonicalPath());
    payload.put("id", channel.getId());
    payload.put("label", channel.getLabel());
    payload.put("index", channel.getIndex());
    payload.put("groupMembershipDropped", result.groupMembershipDropped());
    payload.put("unreplicatedWiring", Wiring.payload(result.unreplicatedWiring()));
    return Result.ok(payload);
  }
}
