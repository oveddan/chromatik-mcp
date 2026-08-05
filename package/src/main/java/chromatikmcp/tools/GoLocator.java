package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.clip.Locator;

import chromatikmcp.domain.Compositions;

public final class GoLocator implements LxTool {

  @Override
  public String name() {
    return "go_locator";
  }

  @Override
  public String description() {
    return "Transport jump to a locator on the arrange-timeline composition, addressed "
        + "by exactly one of 1-indexed index or exact label (which must be unambiguous "
        + "— duplicate labels require the index). Mirrors the app's own locator "
        + "navigation: if the composition is RUNNING, relaunches automation playback "
        + "from the locator (subject to global launch quantization); if STOPPED, moves "
        + "the insert marker there and scrubs lane values to that point WITHOUT "
        + "starting playback (launch separately to play). Returns the locator summary, "
        + "launched (whether the running-relaunch branch was taken), running, and the "
        + "insertMarker and playhead cursors read back from the engine — the insert "
        + "marker is bounded to the composition length, so it may differ from a "
        + "locator sitting past the end. Not undoable with Cmd-Z.";
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
    boolean launched = Compositions.goLocator(lx, locator);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("locator",
        Payloads.locator(Compositions.locatorSummary(composition, locator)));
    payload.put("launched", launched);
    payload.put("running", composition.isRunning());
    payload.put("insertMarker", Payloads.cursor(composition, composition.insertMarker.cursor));
    payload.put("playhead", Payloads.cursor(composition, composition.getCursor()));
    return Result.ok(payload);
  }
}
