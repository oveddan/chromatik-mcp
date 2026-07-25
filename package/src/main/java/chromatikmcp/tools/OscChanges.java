package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import chromatikmcp.domain.Channels;

/** JSON shaping for {@link Channels.PathChange}, shared by move_pattern and move_effect. */
final class OscChanges {

  private OscChanges() {}

  static List<Map<String, Object>> payload(List<Channels.PathChange> changes) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Channels.PathChange change : changes) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("componentId", change.componentId());
      entry.put("before", change.before());
      entry.put("after", change.after());
      result.add(entry);
    }
    return result;
  }
}
