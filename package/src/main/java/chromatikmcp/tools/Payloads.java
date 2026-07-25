package chromatikmcp.tools;

import java.util.Map;

/** Payload-shaping helpers shared across tool handlers. */
final class Payloads {

  private Payloads() {}

  /**
   * Puts {@code key} only when {@code value} is non-null. Used for fields backed by
   * {@code Resolve.canonicalPathOrNull} (an object that isn't path-registered has a null
   * path) — omit the key rather than emit a bogus "/null" or a literal JSON null, since a
   * key whose type flips between string and null breaks clients.
   */
  static void putIfPresent(Map<String, Object> map, String key, Object value) {
    if (value != null) {
      map.put(key, value);
    }
  }
}
