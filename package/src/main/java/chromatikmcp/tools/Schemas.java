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

  static Map<String, Object> number(String description) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "number");
    schema.put("description", description);
    return schema;
  }

  /**
   * The shared write-side cursor object (see {@code chromatikmcp.domain.Cursors}):
   * exactly one of the four forms, enforced in {@code Cursors.parse} rather than JSON
   * Schema (the SDK validator's oneOf failure wording is unhelpful; the parse error names
   * the offending keys). Every tool taking a timeline position uses this schema so agents
   * see one cursor vocabulary everywhere.
   */
  static Map<String, Object> cursor(String description) {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("millis", number(
        "Absolute-time form: position in milliseconds (>= 0)"));
    properties.put("beatCount", integer(
        "Tempo form: whole beats from the start (>= 0); optionally with beatBasis",
        0, Integer.MAX_VALUE));
    properties.put("beatBasis", number(
        "Tempo form: position within the beat, [0, 1) — only with beatCount (default 0)"));
    properties.put("bars", integer(
        "Bar/beat sugar: 1-indexed bar number as read off the arrange ruler; "
            + "optionally with beats/sixteenths", 1, Integer.MAX_VALUE));
    properties.put("beats", integer(
        "Bar/beat sugar: 1-indexed beat within the bar — only with bars (default 1)",
        1, Integer.MAX_VALUE));
    properties.put("sixteenths", integer(
        "Bar/beat sugar: 1-indexed sixteenth within the beat (1-4) — only with bars "
            + "(default 1)", 1, 4));
    properties.put("at", string(
        "Symbolic origin form: playhead | insertMarker | start | end | loopStart | "
            + "loopEnd | playStart | playEnd | locator:<n> (1-indexed); optionally with "
            + "offsetBeats or offsetMillis"));
    properties.put("offsetBeats", number(
        "Signed beat offset applied to 'at' (negative moves earlier, bounded at 0)"));
    properties.put("offsetMillis", number(
        "Signed millisecond offset applied to 'at' (negative moves earlier, bounded at 0)"));
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("description", description + " Exactly one form: {millis} | "
        + "{beatCount[, beatBasis]} | {bars[, beats, sixteenths]} | "
        + "{at[, offsetBeats | offsetMillis]}.");
    schema.put("properties", properties);
    schema.put("additionalProperties", false);
    return schema;
  }
}
