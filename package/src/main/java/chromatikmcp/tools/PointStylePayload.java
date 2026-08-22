package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import chromatikmcp.domain.PointStyle;

/** Shared wire serializer for both point-style tools. */
final class PointStylePayload {

  private PointStylePayload() {}

  static Map<String, Object> setting(PointStyle.Setting setting) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("name", setting.name());
    // UIPointCloud parameters have no canonical path, so ParameterInfo omits only that
    // unavailable key; every remaining field is the normal parameter wire shape.
    payload.putAll(setting.parameter().toMap());
    return payload;
  }

  static Map<String, Object> settings(List<PointStyle.Setting> settings) {
    List<Map<String, Object>> entries = new ArrayList<>(settings.size());
    for (PointStyle.Setting setting : settings) {
      entries.add(setting(setting));
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("settings", entries);
    return payload;
  }
}
