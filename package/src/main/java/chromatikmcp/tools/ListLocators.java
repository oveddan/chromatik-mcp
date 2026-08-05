package chromatikmcp.tools;

import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Compositions;

public final class ListLocators implements LxTool {

  @Override
  public String name() {
    return "list_locators";
  }

  @Override
  public String description() {
    return "Every locator (named position marker) on the arrange-timeline composition, "
        + "in timeline order: canonical path (/lx/timeline/composition/locator/<n>), "
        + "1-indexed index, label, and position as a full cursor object {millis, "
        + "beatCount, beatBasis, formatted}. Locator addressing is 1-indexed everywhere "
        + "— these tools, the locator:<n> cursor origin, and the canonical path — unlike "
        + "lane/event payloads whose index is 0-based. Indices are POSITIONAL: the list "
        + "re-sorts by cursor on every add or move and shifts on remove, so re-list "
        + "rather than reuse an index from an earlier response. Locators may sit past "
        + "the composition length. Labels are set at add_locator or renamed via "
        + "set_parameter on <locatorPath>/label.";
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
    return Result.ok(Payloads.locatorList(Compositions.listLocators(lx)));
  }
}
