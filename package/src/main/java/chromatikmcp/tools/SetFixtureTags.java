package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.structure.LXFixture;

import chromatikmcp.domain.Fixtures;
import chromatikmcp.domain.Resolve;

public final class SetFixtureTags implements LxTool {

  @Override
  public String name() {
    return "set_fixture_tags";
  }

  @Override
  public String description() {
    return "Set a fixture's model tags — the vocabulary get_views' selectors match "
        + "against (see get_views). Replaces the fixture's whole tag list. Every token is "
        + "validated against LX's tag regex ([A-Za-z0-9_.\\-/]+) before anything is "
        + "written: LX itself silently drops any tag that fails this check (and silently "
        + "restores the fixture's *default* tags if every token fails), so an unvalidated "
        + "write can look successful while quietly breaking view addressing — this tool "
        + "rejects the whole call instead, naming the offending token, and writes nothing. "
        + "Returns the resulting tag list so the caller sees what actually landed. Rejected "
        + "on a subfixture of a JsonFixture, same as set_fixture_params.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string("Canonical path of the fixture, e.g. /lx/structure/fixture/1"));
    properties.put("tags", Map.of(
        "type", "array",
        "items", Map.of("type", "string"),
        "description", "The fixture's new complete tag list — replaces the existing tags."));
    return Schemas.object(properties, List.of("path", "tags"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    Object pathArg = args.get("path");
    if (!(pathArg instanceof String path) || path.isEmpty()) {
      return Result.error(Result.INVALID_ARGUMENT, "path must be a non-empty string");
    }
    Object tagsArg = args.get("tags");
    if (!(tagsArg instanceof List)) {
      return Result.error(Result.INVALID_ARGUMENT, "tags must be an array of strings");
    }
    List<?> rawTags = (List<?>) tagsArg;
    List<String> tags = new ArrayList<>();
    for (Object tag : rawTags) {
      if (!(tag instanceof String s)) {
        return Result.error(Result.INVALID_ARGUMENT, "tags must be an array of strings");
      }
      tags.add(s);
    }
    if (tags.isEmpty()) {
      return Result.error(Result.INVALID_ARGUMENT, "tags must not be empty");
    }

    LXFixture fixture = Resolve.component(lx, path, LXFixture.class);
    List<String> tagList = Fixtures.setTags(lx, fixture, tags);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", Resolve.canonicalPath(fixture));
    payload.put("tagList", tagList);
    return Result.ok(payload);
  }
}
