package lxmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;

import lxmcp.domain.Catalog;

/**
 * {@code get_component_doc}: returns the semantic catalog entry for an LX pattern,
 * effect, or modulator class.
 *
 * <p>A registered but undocumented class is a valid response ({@code documented: false})
 * — it is not an error. An unregistered class is {@code not_found}.
 */
public final class GetComponentDoc implements LxTool {

  @Override
  public String name() {
    return "get_component_doc";
  }

  @Override
  public String description() {
    return "Return the semantic catalog entry for an LX pattern, effect, or modulator class: "
        + "visual summary, parameter interactions, usage tips, and staleness metadata. "
        + "Accepts either the full class name or the short name returned by the "
        + "list_available_* tools (a short name ambiguous across patterns/effects/modulators "
        + "is rejected, naming the candidates). Registered but undocumented classes return "
        + "documented:false (not an error).";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Schemas.object(
        Map.of("class", Schemas.string(
            "Class name, as returned by list_available_* tools — full class name "
                + "(e.g. heronarts.lx.pattern.color.GradientPattern) or short name "
                + "(e.g. GradientPattern)")),
        List.of("class"));
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    if (!(args.get("class") instanceof String className) || className.isEmpty()) {
      return Result.error(Result.INVALID_ARGUMENT, "Required non-empty string argument: class");
    }

    // Throws ResolveException(NOT_FOUND) for unknown classes; seam maps to not_found
    Class<? extends LXComponent> clazz = Catalog.findClass(lx, className);

    List<String> tags = Optional.ofNullable(lx.registry.getTags(clazz))
        .map(List::copyOf).orElse(List.of());

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("class", className);
    payload.put("name", LXComponent.getComponentName(clazz));
    payload.put("category", LXComponent.getCategory(clazz));
    payload.put("tags", tags);

    Catalog.CatalogEntry entry = Catalog.locateEntry(clazz);
    if (entry == null) {
      payload.put("documented", false);
      return Result.ok(payload);
    }

    payload.put("documented", true);
    payload.put("summary", entry.summary());
    if (entry.parameterInteractions() != null) {
      payload.put("parameterInteractions", entry.parameterInteractions());
    }
    if (entry.usageTips() != null) {
      payload.put("usageTips", entry.usageTips());
    }

    Catalog.Frontmatter fm = entry.frontmatter();
    String recordedHash = fm.get("classBytesSha256");
    Catalog.Staleness staleness = Catalog.staleness(clazz, recordedHash);
    Object stale = switch (staleness) {
      case FRESH -> Boolean.FALSE;
      case STALE -> Boolean.TRUE;
      case UNKNOWN -> "unknown";
    };
    String currentHash = Catalog.computeBytesHash(clazz);

    Map<String, Object> catalogMeta = new LinkedHashMap<>();
    if (fm.get("generatedAt") != null) {
      catalogMeta.put("generatedAt", fm.get("generatedAt"));
    }
    if (fm.get("lxVersion") != null) {
      catalogMeta.put("lxVersion", fm.get("lxVersion"));
    }
    if (recordedHash != null) {
      catalogMeta.put("catalogClassBytesSha256", recordedHash);
    }
    if (currentHash != null) {
      catalogMeta.put("currentClassBytesSha256", currentHash);
    }
    catalogMeta.put("stale", stale);
    catalogMeta.put("source", entry.source());
    payload.put("catalog", catalogMeta);

    return Result.ok(payload);
  }
}
