package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.clip.Locator;

import chromatikmcp.domain.Compositions;
import chromatikmcp.domain.Cursors;

public final class AddLocator implements LxTool {

  @Override
  public String name() {
    return "add_locator";
  }

  @Override
  public String description() {
    return "Adds a locator (named position marker) to the arrange-timeline composition "
        + "at the given cursor, optionally labeled. Returns the new locator's summary "
        + "{path, index, label, cursor} with the cursor read back from the engine. "
        + "Locator positions are not clamped — a locator may sit past the composition "
        + "length. The locator list re-sorts by cursor on every add or move, so the "
        + "returned 1-indexed index is the new locator's position in timeline order and "
        + "EARLIER locators' indices may have shifted — re-run list_locators rather than "
        + "reuse indices from earlier responses. Undo removes the locator; a redo "
        + "restores it unlabeled (the label is applied outside the undo stack).";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("cursor", Schemas.cursor(
        "Position for the new locator on the composition timeline"));
    properties.put("label", Schemas.string(
        "Optional display label for the locator (rename later via set_parameter on "
            + "<locatorPath>/label)"));
    return Schemas.object(properties, List.of("cursor"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    var composition = Compositions.get(lx);
    var cursor = Cursors.parse(composition, Args.requireMap(args, "cursor"));
    Locator locator = Compositions.addLocator(lx, cursor, Args.optionalString(args, "label"));
    Map<String, Object> payload =
        Payloads.locator(Compositions.locatorSummary(composition, locator));
    payload.put("locatorCount", composition.locators.size());
    return Result.ok(payload);
  }
}
