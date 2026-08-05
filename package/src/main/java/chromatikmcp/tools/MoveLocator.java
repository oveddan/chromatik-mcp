package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.clip.Locator;

import chromatikmcp.domain.Compositions;
import chromatikmcp.domain.Cursors;

public final class MoveLocator implements LxTool {

  @Override
  public String name() {
    return "move_locator";
  }

  @Override
  public String description() {
    return "Moves a locator on the arrange-timeline composition to a new cursor "
        + "position. Address by exactly one of 1-indexed index or exact label (which "
        + "must be unambiguous — duplicate labels require the index). Returns the "
        + "locator's summary {path, index, label, cursor} read back from the engine: "
        + "the list re-sorts by cursor on every move, so the returned index is the "
        + "locator's NEW position in timeline order and other locators' indices may "
        + "have shifted — re-run list_locators rather than reuse indices from earlier "
        + "responses. Positions are not clamped; a locator may sit past the "
        + "composition length.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("index", Schemas.integer(
        "1-indexed locator position in timeline order (see list_locators); exactly one "
            + "of index or label", 1, Integer.MAX_VALUE));
    properties.put("label", Schemas.string(
        "Exact locator label; must match exactly one locator"));
    properties.put("cursor", Schemas.cursor("New position for the locator"));
    return Schemas.object(properties, List.of("cursor"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    var composition = Compositions.get(lx);
    Integer index = args.get("index") == null ? null : Args.requireInt(args, "index");
    Locator locator = Compositions.resolveLocator(
        composition, index, Args.optionalString(args, "label"));
    var cursor = Cursors.parse(composition, Args.requireMap(args, "cursor"));
    Compositions.moveLocator(lx, locator, cursor);
    return Result.ok(Payloads.locator(Compositions.locatorSummary(composition, locator)));
  }
}
