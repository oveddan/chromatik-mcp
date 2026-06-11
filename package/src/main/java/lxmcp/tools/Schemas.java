package lxmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON-Schema map builders for tool input schemas. */
final class Schemas {

  private Schemas() {}

  static Map<String, Object> noArgs() {
    return Map.of("type", "object", "properties", Map.of(), "additionalProperties", false);
  }

  static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("properties", properties);
    schema.put("required", required);
    schema.put("additionalProperties", false);
    return schema;
  }

  static Map<String, Object> string(String description) {
    return Map.of("type", "string", "description", description);
  }
}
