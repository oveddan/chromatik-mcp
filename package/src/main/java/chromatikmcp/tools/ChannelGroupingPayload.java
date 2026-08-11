package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.mixer.LXAbstractChannel;

/** Shared JSON shaping for channel grouping mutations. */
final class ChannelGroupingPayload {

  private ChannelGroupingPayload() {}

  static Map<String, Object> channel(LXAbstractChannel channel) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", channel.getCanonicalPath());
    payload.put("id", channel.getId());
    payload.put("label", channel.getLabel());
    payload.put("index", channel.getIndex());
    return payload;
  }

  static List<Map<String, Object>> channels(List<? extends LXAbstractChannel> channels) {
    List<Map<String, Object>> payload = new ArrayList<>();
    for (LXAbstractChannel channel : channels) {
      payload.add(channel(channel));
    }
    return payload;
  }
}
