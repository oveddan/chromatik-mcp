package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Modulators;
import chromatikmcp.domain.Resolve;

public final class ListModulations implements LxTool {

  static final int DEFAULT_LIMIT = 100;
  static final int MAX_LIMIT = 250;

  private record Cursor(
      int offset, Integer modulationCount, Integer triggerCount, String wiringFingerprint) {}

  @Override
  public String name() {
    return "list_modulations";
  }

  @Override
  public String description() {
    return "List one modulation engine's live modulators and wirings. Defaults to "
        + "'detail: summary' — the wiring graph only (modulators: path/label/class; "
        + "modulations and triggers: path/sourcePath/targetPath) — the right choice for surveying a "
        + "project; a real project can carry dozens of modulators and hundreds of wirings, and "
        + "the full shape blows past client response limits. Pass 'detail: full' for today's "
        + "complete shape (modulator OSC addresses/running state, and per-modulation "
        + "range/polarity/rangePath to adjust depth via set_parameter). Wirings are paged in "
        + "continuous-then-trigger order (100 by default, 250 maximum); pass nextCursor back as "
        + "cursor until it is omitted. Modulators repeat on every page. Defaults to the global "
        + "engine; pass scope (a device path) for a pattern/effect's own chain. Knob paths "
        + "derive from a modulator's path (e.g. <path>/macro1).";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("scope", Schemas.string(
        "Optional canonical path of a device (its own engine) or a modulation engine; "
            + "omit for the global engine"));
    properties.put("detail", Schemas.enumString(
        "'summary' (default) for the wiring graph only, or 'full' for today's complete "
            + "payload (OSC addresses, running state, range/polarity/rangePath)",
        List.of("summary", "full")));
    properties.put("cursor", Schemas.string(
        "Opaque cursor from the preceding page's nextCursor; omit for the first page"));
    properties.put("limit", Schemas.integer(
        "Maximum combined number of continuous modulations and triggers to return",
        1, MAX_LIMIT));
    return Schemas.object(properties, List.of());
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String scope = Args.optionalString(args, "scope", "scope must be a string path");
    String detail = Args.optionalString(args, "detail");
    if (detail != null && !detail.equals("summary") && !detail.equals("full")) {
      return Result.error(Result.INVALID_ARGUMENT, "detail must be 'summary' or 'full'");
    }
    boolean full = "full".equals(detail);
    Cursor cursor = parseCursor(Args.optionalString(args, "cursor"));
    int limit = Args.optionalInt(args, "limit", DEFAULT_LIMIT);
    if (limit < 1 || limit > MAX_LIMIT) {
      return Result.error(Result.INVALID_ARGUMENT,
          "limit must be between 1 and " + MAX_LIMIT);
    }

    Modulators.EngineVersion expectedVersion = (cursor.modulationCount() == null) ? null
        : new Modulators.EngineVersion(
            cursor.modulationCount(), cursor.triggerCount(), cursor.wiringFingerprint());
    Modulators.EngineInfo info = Modulators.listEnginePage(
        lx, Modulators.resolveEngine(lx, scope), cursor.offset(), limit, expectedVersion);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("enginePath", info.path());

    List<Map<String, Object>> modulators = new ArrayList<>();
    for (Modulators.ModulatorInfo m : info.modulators()) {
      modulators.add(full ? modulatorFull(m) : modulatorSummary(m));
    }
    payload.put("modulators", modulators);

    List<Map<String, Object>> modulations = new ArrayList<>();
    for (Modulators.ModulationInfo m : info.modulations()) {
      modulations.add(full ? modulationFull(m) : modulationSummary(m));
    }
    payload.put("modulations", modulations);

    List<Map<String, Object>> triggers = new ArrayList<>();
    for (Modulators.TriggerInfo t : info.triggers()) {
      triggers.add(full ? triggerFull(t) : triggerSummary(t));
    }
    payload.put("triggers", triggers);
    payload.put("returnedModulationCount", modulations.size());
    payload.put("returnedTriggerCount", triggers.size());
    payload.put("totalModulationCount", info.totalModulationCount());
    payload.put("totalTriggerCount", info.totalTriggerCount());
    if (info.nextOffset() != null) {
      payload.put("nextCursor", info.nextOffset() + ":"
          + info.totalModulationCount() + ":" + info.totalTriggerCount() + ":"
          + info.wiringFingerprint());
    }

    return Result.ok(payload);
  }

  private static Cursor parseCursor(String cursor) {
    if (cursor == null) {
      return new Cursor(0, null, null, null);
    }
    String[] parts = cursor.split(":", -1);
    if (parts.length != 4 || !parts[3].matches("[0-9a-f]{64}")) {
      throw Resolve.invalidArgument(
          "cursor must be an unchanged value returned by nextCursor");
    }
    try {
      int offset = Integer.parseInt(parts[0]);
      int modulationCount = Integer.parseInt(parts[1]);
      int triggerCount = Integer.parseInt(parts[2]);
      if (offset < 0 || modulationCount < 0 || triggerCount < 0) {
        throw new NumberFormatException();
      }
      return new Cursor(offset, modulationCount, triggerCount, parts[3]);
    } catch (NumberFormatException e) {
      throw Resolve.invalidArgument(
          "cursor must be an unchanged value returned by nextCursor");
    }
  }

  private static Map<String, Object> modulatorFull(Modulators.ModulatorInfo m) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("path", m.path());
    entry.put("id", m.id());
    entry.put("label", m.label());
    entry.put("class", m.className());
    entry.put("running", m.running());
    if (m.oscAddress() != null) {
      entry.put("oscAddress", m.oscAddress());
    }
    return entry;
  }

  private static Map<String, Object> modulatorSummary(Modulators.ModulatorInfo m) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("path", m.path());
    entry.put("label", m.label());
    entry.put("class", m.className());
    return entry;
  }

  private static Map<String, Object> modulationFull(Modulators.ModulationInfo m) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("path", m.path());
    entry.put("id", m.id());
    entry.put("sourcePath", m.sourcePath());
    entry.put("targetPath", m.targetPath());
    entry.put("range", m.range());
    entry.put("polarity", m.polarity());
    entry.put("rangePath", m.rangePath());
    return entry;
  }

  private static Map<String, Object> modulationSummary(Modulators.ModulationInfo m) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("path", m.path());
    entry.put("sourcePath", m.sourcePath());
    entry.put("targetPath", m.targetPath());
    return entry;
  }

  private static Map<String, Object> triggerFull(Modulators.TriggerInfo t) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("path", t.path());
    entry.put("id", t.id());
    entry.put("sourcePath", t.sourcePath());
    entry.put("targetPath", t.targetPath());
    return entry;
  }

  private static Map<String, Object> triggerSummary(Modulators.TriggerInfo t) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("path", t.path());
    entry.put("sourcePath", t.sourcePath());
    entry.put("targetPath", t.targetPath());
    return entry;
  }
}
