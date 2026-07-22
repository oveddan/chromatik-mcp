package chromatikmcp;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;

import org.junit.jupiter.api.Test;

import chromatikmcp.tools.GetStatus;
import chromatikmcp.tools.LxTool;
import chromatikmcp.tools.Tools;

/**
 * Regression guard: ToolCatalogDriftTest fails non-deterministically when tool schemas use
 * Map.of(), whose iteration order is per-JVM randomized. This test ensures every tool's
 * inputSchema() contains only ordered map types (LinkedHashMap or SortedMap), so schema JSON
 * ordering is deterministic.
 */
class ToolSchemaOrderedMapsTest {

  @Test
  void allToolSchemasUseOrderedMaps() {
    GetStatus getStatus = new GetStatus(new ServerStatus(), () -> new ConnectionSnapshot(0, 0, false));
    for (LxTool tool : Tools.allTools(getStatus)) {
      Map<String, Object> schema = tool.inputSchema();
      assertSchemaUsesOrderedMaps(schema, "tool '" + tool.name() + "'");
    }
  }

  @SuppressWarnings("unchecked")
  private void assertSchemaUsesOrderedMaps(Object value, String keyPath) {
    if (value instanceof Map) {
      Map<String, Object> m = (Map<String, Object>) value;
      String mapClass = m.getClass().getSimpleName();
      assertTrue(
          m instanceof LinkedHashMap || m instanceof SortedMap,
          keyPath + " has " + mapClass + " — use Schemas helpers or LinkedHashMap; "
              + "Map.of iteration order is per-JVM randomized and breaks ToolCatalogDriftTest");
      // Recursively check all values in the map
      for (Object v : m.values()) {
        assertSchemaUsesOrderedMaps(v, keyPath);
      }
    } else if (value instanceof Iterable && !(value instanceof String)) {
      // Recursively check items in lists/sets
      for (Object item : (Iterable<?>) value) {
        assertSchemaUsesOrderedMaps(item, keyPath);
      }
    }
  }
}
