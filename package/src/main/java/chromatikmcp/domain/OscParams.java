package chromatikmcp.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;

/**
 * Flat enumeration of every OSC-addressable parameter in the running engine, for external
 * OSC senders (e.g. the Bitwig OSC bridge) to offer click-to-bind target pickers.
 *
 * <p>Unlike {@link Parameters#listFor}, which deliberately walks one shallow level and
 * expects the client to traverse path-by-path, this walks the whole component tree in one
 * call — an OSC sender wants the full address space at once, not a tree-walk protocol.
 */
public final class OscParams {

  private OscParams() {}

  /** One detached OSC-addressable parameter entry; optional bounds/value are null. */
  public record OscParamInfo(
      String oscAddress,
      String label,
      String path,
      String type,
      String componentPath,
      String componentLabel,
      String componentType,
      String units,
      Double min,
      Double max,
      String value) {}

  /**
   * Every parameter reachable from {@code lx.engine} that LX exposes over OSC, as typed
   * detached entries. Call on the engine thread.
   *
   * <p>May be polled by an external OSC sender, so this deliberately computes only static
   * metadata ({@link Parameters#describeMeta}) — no modulation scan, no formatting — to avoid
   * doing avoidable work on the thread that renders a live show. The one exception is
   * {@link heronarts.lx.parameter.StringParameter} values, included below because they are a
   * cheap field read with no formatting/modulation cost, and consumers have no other way to
   * read them (e.g. a macro modulator's user-given knob names).
   */
  public static List<OscParamInfo> list(LX lx) {
    List<OscParamInfo> out = new ArrayList<>();
    walk(lx.engine, new HashSet<>(), out);
    return out;
  }

  private static void walk(
      LXComponent component, Set<Integer> visited, List<OscParamInfo> out) {
    if (component == null || !visited.add(component.getId())) {
      return;
    }
    String componentPath = Resolve.canonicalPath(component);
    String componentLabel = component.getLabel();
    // componentLabel is user-editable (renamed freely in the UI), so it can't be used to tell
    // components apart programmatically. componentType is the stable discriminator a consumer
    // needs to e.g. pick out LX's macro modulators (MacroKnobs, MacroTriggers, MacroSwitches)
    // from the full parameter list. Computed once per component, not per parameter.
    String componentType = component.getClass().getSimpleName();
    for (LXParameter parameter : component.getParameters()) {
      Parameters.ParameterMeta meta = Parameters.describeMeta(parameter);
      if (meta.oscAddress() == null) {
        continue;
      }
      // String values are the one exception to the "no live value reads" rule above: a plain
      // field read with no formatting/modulation work, and consumers need it. Macro knob
      // display names (e.g. from Bitwig) live in a modulator's label1..label8 StringParameter
      // VALUES, not in any static metadata field — without reading it here, a consumer can't
      // show the user-given name for a macro knob at all.
      String value = (parameter instanceof StringParameter s) ? s.getString() : null;
      out.add(new OscParamInfo(
          meta.oscAddress(), meta.label(), meta.path(), meta.type(),
          componentPath, componentLabel, componentType, meta.units(),
          meta.min(), meta.max(), value));
    }
    for (LXComponent child : component.children.values()) {
      walk(child, visited, out);
    }
    for (List<? extends LXComponent> array : component.childArrays.values()) {
      for (LXComponent child : array) {
        walk(child, visited, out);
      }
    }
  }
}
