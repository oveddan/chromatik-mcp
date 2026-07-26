package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;

import chromatikmcp.domain.Catalog;
import chromatikmcp.domain.Resolve;

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
        + "Accepts exactly one of 'class' (the full class name or the short name returned "
        + "by the list_available_* tools — a short name ambiguous across "
        + "patterns/effects/modulators is rejected, naming the candidates) or 'path' (the "
        + "canonical path of a live component instance, e.g. "
        + "/lx/mixer/channel/1/pattern/1 — its class is looked up and documented). "
        + "Registered but undocumented classes return documented:false (not an error). "
        + "catalog.candidates always lists every entry considered for the class, winner "
        + "first, then the rest ranked by accuracy (bytecode match, then recency) — a "
        + "single-element array is the common case; more than one appears when, e.g., a "
        + "stock class is documented by both LX and this plugin, so the choice of winner "
        + "is auditable. The served summary/parameterInteractions/usageTips are merged, "
        + "not just the ranked winner's: Summary comes from the ranked winner, but any "
        + "candidate that names parameterInteractions or usageTips in its curated: "
        + "frontmatter contributes that section even if it lost the ranking. "
        + "catalog.merged.grafts reports a section whenever the winning declarer isn't the "
        + "ranked winner, or two or more candidates contested it (base:false and/or "
        + "tied:true) — empty is the common case.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("class", Schemas.string(
        "Class name, as returned by list_available_* tools — full class name "
            + "(e.g. heronarts.lx.pattern.color.GradientPattern) or short name "
            + "(e.g. GradientPattern). Exactly one of 'class' or 'path' is required."));
    properties.put("path", Schemas.string(
        "Canonical path of a live component instance (e.g. "
            + "/lx/mixer/channel/1/pattern/1) whose class is documented. Exactly one of "
            + "'class' or 'path' is required."));
    return Schemas.object(properties, List.of());
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    Object classArg = args.get("class");
    Object pathArg = args.get("path");
    boolean hasClass = classArg != null;
    boolean hasPath = pathArg != null;
    if (hasClass == hasPath) {
      if (hasClass) {
        return Result.error(Result.INVALID_ARGUMENT,
            "Exactly one of 'class' or 'path' is required; both were given");
      } else {
        return Result.error(Result.INVALID_ARGUMENT,
            "Exactly one of 'class' or 'path' is required; neither was given");
      }
    }
    // Defensive type checks: the SDK validates inputSchema server-side before the handler runs
    // (see docs/tool-conventions.md "Input validation" section), so these are unreachable via MCP.
    // They persist here because handlers may be invoked directly in unit tests (e.g.,
    // SubfixtureTreeTest calls handlers directly without MCP marshalling).
    if (hasClass && !(classArg instanceof String)) {
      return Result.error(Result.INVALID_ARGUMENT, "class must be a string");
    }
    if (hasPath && !(pathArg instanceof String)) {
      return Result.error(Result.INVALID_ARGUMENT, "path must be a string");
    }
    if (hasClass && ((String) classArg).isBlank()) {
      return Result.error(Result.INVALID_ARGUMENT, "class must not be empty");
    }
    if (hasPath && ((String) pathArg).isBlank()) {
      return Result.error(Result.INVALID_ARGUMENT, "path must not be empty");
    }

    Class<? extends LXComponent> clazz;
    if (hasPath) {
      // Throws ResolveException(NOT_FOUND/TYPE_MISMATCH/INVALID_PATH); seam maps to wire codes
      LXComponent component = Resolve.component(lx, (String) pathArg);
      clazz = component.getClass();
      // Verify the component's class is a documentable kind (pattern/effect/modulator).
      // Catalog.findClass throws NOT_FOUND if not registered; mirror that error.
      Catalog.findClass(lx, clazz.getName());
    } else {
      // Throws ResolveException(NOT_FOUND) for unknown classes; seam maps to not_found
      clazz = Catalog.findClass(lx, (String) classArg);
    }

    List<String> tags = Optional.ofNullable(lx.registry.getTags(clazz))
        .map(List::copyOf).orElse(List.of());

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("class", clazz.getName());
    payload.put("name", LXComponent.getComponentName(clazz));
    payload.put("category", LXComponent.getCategory(clazz));
    payload.put("tags", tags);

    Catalog.Resolution resolution = Catalog.resolve(lx, clazz);
    Catalog.CatalogEntry entry = resolution.merged().entry();
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
    // Curated sections are hand-written and carried across regenerations, so the class-bytes
    // hash does not vouch for them — say which ones, rather than letting `stale: false`
    // imply the whole body was re-derived.
    if (fm.get("curated") != null) {
      catalogMeta.put("curated", fm.get("curated"));
      if (fm.get("curatedAt") != null) {
        catalogMeta.put("curatedAt", fm.get("curatedAt"));
      }
    }
    catalogMeta.put("source", entry.source());
    // Always emitted, including the one-element case (the common case): its absence
    // would otherwise be ambiguous between "exactly one copy visible" and "this server
    // predates the feature."
    List<Map<String, Object>> candidates = new ArrayList<>();
    for (Catalog.Candidate candidate : resolution.candidates()) {
      Catalog.Frontmatter candidateFm = candidate.entry().frontmatter();
      String candidateHash = candidateFm.get("classBytesSha256");
      Map<String, Object> candidatePayload = new LinkedHashMap<>();
      candidatePayload.put("source", candidate.source());
      candidatePayload.put("url", candidate.url());
      if (candidateFm.get("generatedAt") != null) {
        candidatePayload.put("generatedAt", candidateFm.get("generatedAt"));
      }
      if (candidateHash != null) {
        candidatePayload.put("classBytesSha256", candidateHash);
      }
      // Read the domain's verdict rather than recomputing it here: recomputing from
      // `currentHash` (a separately, statically cached lookup) is how this field could
      // disagree with the ranking that actually picked the winner. Three-valued like
      // `stale` above, not a collapsed boolean: "no classBytesSha256 recorded" (e.g. a
      // hand-written overlay fixture), "live bytecode unreadable", and "hash genuinely
      // differs" are distinct states a client shouldn't have to guess between.
      candidatePayload.put("bytesMatch", switch (candidate.bytesMatch()) {
        case FRESH -> Boolean.TRUE;
        case STALE -> Boolean.FALSE;
        case UNKNOWN -> "unknown";
      });
      candidates.add(candidatePayload);
    }
    catalogMeta.put("candidates", candidates);

    // Always emitted, same rationale as candidates above: an empty grafts array means
    // "merging was considered and nothing needed grafting" (the common case), not
    // "this server predates section-level merging."
    List<Map<String, Object>> grafts = new ArrayList<>();
    for (Catalog.Graft graft : resolution.merged().grafts()) {
      Map<String, Object> graftPayload = new LinkedHashMap<>();
      graftPayload.put("section", graft.section());
      graftPayload.put("source", graft.source());
      graftPayload.put("url", graft.url());
      graftPayload.put("tied", graft.tied());
      graftPayload.put("base", graft.base());
      grafts.add(graftPayload);
    }
    Map<String, Object> mergedMeta = new LinkedHashMap<>();
    mergedMeta.put("grafts", grafts);
    catalogMeta.put("merged", mergedMeta);

    payload.put("catalog", catalogMeta);

    return Result.ok(payload);
  }
}
