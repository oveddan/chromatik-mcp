package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.clip.Locator;

import chromatikmcp.domain.Compositions;

public final class RemoveLocator implements LxTool {

  @Override
  public String name() {
    return "remove_locator";
  }

  @Override
  public String description() {
    return "Removes a locator from the arrange-timeline composition, addressed by "
        + "exactly one of 1-indexed index or exact label (which must be unambiguous — "
        + "duplicate labels require the index). Returns the removed locator's last "
        + "state {index, label, cursor} and the remaining locatorCount. Locator "
        + "indices are POSITIONAL and shift on every add, move, or remove — re-run "
        + "list_locators rather than reuse an index from an earlier response. Undo "
        + "restores the locator with its label and position.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("index", Schemas.integer(
        "1-indexed locator position in timeline order (see list_locators); exactly one "
            + "of index or label", 1, Integer.MAX_VALUE));
    properties.put("label", Schemas.string(
        "Exact locator label; must match exactly one locator"));
    return Schemas.object(properties, List.of());
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
    // Snapshot before the perform: the summary of a removed (disposed) locator is
    // meaningless afterwards.
    Map<String, Object> removed =
        Payloads.locator(Compositions.locatorSummary(composition, locator));
    // Drop the path: locator paths are positional, so after the removal this one addresses
    // whichever locator slid into the slot — the one field of the shared summary that would
    // be actively misleading here.
    removed.remove("path");
    Compositions.removeLocator(lx, locator);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("removed", removed);
    payload.put("locatorCount", composition.locators.size());
    return Result.ok(payload);
  }
}
