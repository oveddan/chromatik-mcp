package chromatikmcp;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import chromatikmcp.tools.GetStatus;
import chromatikmcp.tools.LxTool;
import chromatikmcp.tools.Tools;

import tools.jackson.databind.ObjectMapper;

/**
 * Shared serialization for the tool catalog dump (used by {@link ToolCatalogDump}, the
 * generator invoked from {@code package/scripts/dump-tool-catalog.sh}, and
 * {@link ToolCatalogDriftTest}, which compares this same JSON against the committed
 * {@code site/src/data/tools.json}) — the two must never diverge on how a tool is
 * serialized, or the drift check is meaningless.
 *
 * <p>Every {@link LxTool} is constructible with no live {@link heronarts.lx.LX}; {@code
 * get_status} is the one exception, so a dummy {@link ServerStatus}/{@link
 * ConnectionSnapshot} stand in here — only name/description/schema/readOnly are read, never
 * {@code handle()}.
 */
final class ToolCatalogJson {

  private ToolCatalogJson() {}

  static String catalogJson() {
    GetStatus getStatus = new GetStatus(new ServerStatus(), () -> new ConnectionSnapshot(0, 0, false));
    List<LxTool> tools = Tools.allTools(getStatus).stream()
        .sorted(Comparator.comparing(LxTool::name))
        .toList();

    List<Map<String, Object>> entries = tools.stream()
        .map(tool -> {
          Map<String, Object> entry = new LinkedHashMap<>();
          entry.put("name", tool.name());
          entry.put("description", tool.description());
          entry.put("readOnly", tool.readOnly());
          entry.put("inputSchema", tool.inputSchema());
          return (Map<String, Object>) entry;
        })
        .toList();

    ObjectMapper mapper = new ObjectMapper();
    try {
      return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(entries) + "\n";
    } catch (RuntimeException e) {
      throw new IllegalStateException("Failed to serialize tool catalog", e);
    }
  }
}
