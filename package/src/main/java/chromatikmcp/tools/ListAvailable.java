package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Catalog;
import chromatikmcp.domain.Registry;

/**
 * The three {@code list_available_*} tools share one handler, varied by {@link Kind} —
 * each lists the classes registered with LX that a future add-mutation can instantiate.
 */
public final class ListAvailable implements LxTool {

  public enum Kind {
    PATTERNS("list_available_patterns", "patterns", "pattern"),
    EFFECTS("list_available_effects", "effects", "effect"),
    MODULATORS("list_available_modulators", "modulators", "modulator");

    private final String toolName;
    private final String payloadKey;
    private final String noun;

    Kind(String toolName, String payloadKey, String noun) {
      this.toolName = toolName;
      this.payloadKey = payloadKey;
      this.noun = noun;
    }
  }

  private final Kind kind;

  public ListAvailable(Kind kind) {
    this.kind = kind;
  }

  @Override
  public String name() {
    return this.kind.toolName;
  }

  @Override
  public String description() {
    return "List every " + this.kind.noun + " class registered with LX and available to "
        + "instantiate, with display name, category, and tags.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Schemas.noArgs();
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    List<Registry.ComponentType> types = switch (this.kind) {
      case PATTERNS -> Registry.patterns(lx);
      case EFFECTS -> Registry.effects(lx);
      case MODULATORS -> Registry.modulators(lx);
    };
    List<Map<String, Object>> entries = new ArrayList<>();
    for (Registry.ComponentType type : types) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("class", type.className());
      entry.put("name", type.name());
      entry.put("category", type.category());
      entry.put("tags", type.tags());
      if (type.global() != null) {
        entry.put("global", type.global());
      }
      if (type.device() != null) {
        entry.put("device", type.device());
      }
      entry.put("documented", Catalog.hasEntry(type.clazz()));
      entries.add(entry);
    }
    return Result.ok(Map.of(this.kind.payloadKey, entries));
  }
}
