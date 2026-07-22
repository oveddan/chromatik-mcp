package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON-Schema map builders for tool input schemas. */
final class Schemas {

  private Schemas() {}

  static Map<String, Object> noArgs() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("properties", new LinkedHashMap<>());
    schema.put("additionalProperties", false);
    return schema;
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
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "string");
    schema.put("description", description);
    return schema;
  }

  static Map<String, Object> enumString(String description, List<String> values) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "string");
    schema.put("description", description);
    schema.put("enum", values);
    return schema;
  }

  static Map<String, Object> integer(String description, int minimum, int maximum) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "integer");
    schema.put("description", description);
    schema.put("minimum", minimum);
    schema.put("maximum", maximum);
    return schema;
  }

  static Map<String, Object> bool(String description) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "boolean");
    schema.put("description", description);
    return schema;
  }
}
