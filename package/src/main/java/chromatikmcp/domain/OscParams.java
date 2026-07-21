package chromatikmcp.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.parameter.LXParameter;

/**
 * Flat enumeration of every OSC-addressable parameter in the running engine, for external
 * OSC senders (e.g. the Bitwig OSC bridge) to offer click-to-bind target pickers.
 *
 * <p>Unlike {@link Parameters#listFor}, which deliberately walks one shallow level and
 * expects the client to traverse path-by-path, this walks the whole component tree in one
 * call — an OSC sender wants the full address space at once, not a tree-walk protocol.
 */
public final class OscParams {

  /**
   * Defense-in-depth against a pathological tree; the visited set already breaks cycles.
   * Real projects sit well under this (engine → mixer → channel → pattern → params ≈ 5).
   */
  private static final int MAX_DEPTH = 12;

  private OscParams() {}

  /**
   * Every parameter reachable from {@code lx.engine} that LX exposes over OSC, as flat
   * wire-shape maps. Call on the engine thread.
   */
  public static List<Map<String, Object>> list(LX lx) {
    List<Map<String, Object>> out = new ArrayList<>();
    walk(lx.engine, 0, new HashSet<>(), out);
    return out;
  }

  private static void walk(
      LXComponent component, int depth, Set<Integer> visited, List<Map<String, Object>> out) {
    if (component == null || depth > MAX_DEPTH || !visited.add(component.getId())) {
      return;
    }
    String componentPath = Resolve.canonicalPath(component);
    String componentLabel = component.getLabel();
    for (LXParameter parameter : component.getParameters()) {
      Parameters.ParameterInfo info = Parameters.describe(parameter);
      if (info.oscAddress() == null) {
        continue;
      }
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("oscAddress", info.oscAddress());
      entry.put("label", info.label());
      entry.put("path", info.path());
      entry.put("type", info.type());
      entry.put("componentPath", componentPath);
      entry.put("componentLabel", componentLabel);
      entry.put("units", info.units());
      if (info.min() != null) {
        entry.put("min", info.min());
      }
      if (info.max() != null) {
        entry.put("max", info.max());
      }
      if (info.normalized() != null) {
        entry.put("normalized", info.normalized());
      }
      out.add(entry);
    }
    for (LXComponent child : component.children.values()) {
      walk(child, depth + 1, visited, out);
    }
    for (List<? extends LXComponent> array : component.childArrays.values()) {
      for (LXComponent child : array) {
        walk(child, depth + 1, visited, out);
      }
    }
  }
}
