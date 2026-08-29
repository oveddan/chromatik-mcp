package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.pattern.LXPattern;

import chromatikmcp.domain.Channels;

public final class CopyPattern implements LxTool {

  @Override
  public String name() {
    return "copy_pattern";
  }

  @Override
  public String description() {
    return "Copy a configured pattern into any channel or PatternRack ('containerPath'), "
        + "including a different channel from the source's — unlike add_pattern, which "
        + "instantiates a blank one. The copy carries everything inside the source pattern: "
        + "parameter values, its own effects, a PatternRack's nested patterns and their "
        + "effects, and the pattern's device-local modulators/modulations/triggers, rewired "
        + "to the copy. It does NOT carry wiring held outside the pattern — channel-level "
        + "and global modulations and triggers, MIDI mappings, snapshot views — which "
        + "address the source specifically and stay on it; every such reference is listed "
        + "in the response's unreplicatedWiring array (kind, scope, sourcePath, targetPath), "
        + "including clip automation lanes and pattern-launch events that address the source, "
        + "and for MIDI mappings the type/channel/number add_midi_mapping needs to rebuild "
        + "them, so you can restore what matters with wire_modulator/wire_trigger/add_midi_mapping. "
        + "There is no cross-channel move: copy here, then remove_pattern on the source, "
        + "and expect to rewire everything unreplicatedWiring reported. Pass an optional "
        + "0-based index to insert at a specific position; omit to append. Inserting shifts "
        + "the 1-based paths of later sibling patterns — re-list rather than reusing cached "
        + "paths. Returns invalid_argument if the destination is inside the pattern being "
        + "copied or the index is out of range. Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the pattern to copy, e.g. /lx/mixer/channel/1/pattern/1"));
    properties.put("containerPath", Schemas.string(
        "Canonical path of the destination channel or PatternRack, e.g. /lx/mixer/channel/2 "
            + "or /lx/mixer/channel/2/pattern/1 when that pattern is a PatternRack; may be "
            + "the source's own container to duplicate in place"));
    properties.put("index", Schemas.integer(
        "0-based insertion index; omit to append at the end", Integer.MIN_VALUE, Integer.MAX_VALUE));
    return Schemas.object(properties, List.of("path", "containerPath"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String path = Args.requireString(args, "path");
    String containerPath = Args.requireString(args, "containerPath");
    int index = Args.optionalInt(args, "index", -1);
    Channels.PatternCopyResult result = Channels.copyPattern(lx, path, containerPath, index);
    LXPattern pattern = result.pattern();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", pattern.getCanonicalPath());
    payload.put("id", pattern.getId());
    payload.put("label", pattern.getLabel());
    payload.put("class", pattern.getClass().getName());
    payload.put("index", pattern.getIndex());
    payload.put("unreplicatedWiring", Wiring.payload(result.unreplicatedWiring()));
    return Result.ok(payload);
  }
}
