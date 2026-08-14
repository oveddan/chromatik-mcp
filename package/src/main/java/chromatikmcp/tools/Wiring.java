package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import chromatikmcp.domain.Channels;

/**
 * JSON shaping for the references into a component held from outside it, shared by the copy
 * tools (as {@code unreplicatedWiring} — what the copy does not carry) and by move_effect
 * (as {@code droppedWiring} — what the move destroys). Same shape either way: a wiring that
 * exists now and will not exist on the thing you end up with.
 */
final class Wiring {

  private Wiring() {}

  static List<Map<String, Object>> payload(List<Channels.ExternalReference> references) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Channels.ExternalReference reference : references) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("kind", reference.kind());
      entry.put("scope", reference.scope());
      if (reference.sourcePath() != null) {
        entry.put("sourcePath", reference.sourcePath());
      }
      entry.put("targetPath", reference.targetPath());
      result.add(entry);
    }
    return result;
  }
}
