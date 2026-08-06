package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

/** Shared wire serializer for successful remove-tool results. */
public final class RemovalPayload {

  private RemovalPayload() {}

  public static Map<String, Object> of(String removed, String kind) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("removed", removed);
    payload.put("kind", kind);
    return payload;
  }

  public static Map<String, Object> color(String removed, String swatch) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("removed", removed);
    payload.put("swatch", swatch);
    payload.put("kind", "color");
    return payload;
  }
}
