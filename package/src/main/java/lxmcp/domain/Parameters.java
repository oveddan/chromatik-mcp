package lxmcp.domain;

import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.LXPath;
import heronarts.lx.color.ColorParameter;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;

/** Read-only parameter lookup + introspection by canonical LX path. */
public final class Parameters {

  /**
   * {@code value} is type-appropriate (Boolean / String / Integer / Double; colors as an
   * 0xAARRGGBB hex string); {@code normalized}, {@code min}/{@code max}, {@code options}
   * and {@code formatted} are null where the parameter type has no such concept.
   */
  public record ParameterInfo(String path, String label, String description, String type,
      Object value, Double normalized, String units, Double min, Double max,
      List<String> options, String formatted) {}

  private Parameters() {}

  /**
   * Resolve a canonical path (as produced by {@code getCanonicalPath()}, e.g.
   * {@code /lx/mixer/channel/1/fader}) to a snapshot, or null if no parameter is there.
   * Call on the engine thread.
   */
  public static ParameterInfo get(LX lx, String path) {
    LXParameter parameter = LXPath.getParameter(lx, path);
    return (parameter == null) ? null : describe(parameter);
  }

  static ParameterInfo describe(LXParameter parameter) {
    Object value;
    Double min = null;
    Double max = null;
    List<String> options = null;
    String formatted = null;
    if (parameter instanceof BooleanParameter b) {
      value = b.isOn();
    } else if (parameter instanceof StringParameter s) {
      // getValue()/getFormatter() are a change counter for strings — never format them.
      value = s.getString();
    } else if (parameter instanceof ColorParameter c) {
      // Colors pack an int into getValue() via longBitsToDouble; the formatter yields NaN.
      value = String.format("0x%08x", c.getColor());
    } else if (parameter instanceof DiscreteParameter d) {
      value = d.getValuei();
      min = (double) d.getMinValue();
      max = (double) d.getMaxValue();
      String[] opts = d.getOptions();
      if (opts != null) {
        options = List.of(opts);
        formatted = d.getOption();
      } else {
        formatted = d.getFormatter().format(d.getValue());
      }
    } else {
      value = parameter.getValue();
      if (parameter instanceof BoundedParameter b) {
        min = b.range.min;
        max = b.range.max;
      }
      formatted = parameter.getFormatter().format(parameter.getValue());
    }
    return new ParameterInfo(
        parameter.getCanonicalPath(),
        parameter.getLabel(),
        parameter.getDescription(),
        parameter.getClass().getSimpleName(),
        value,
        (parameter instanceof LXNormalizedParameter n) ? n.getNormalized() : null,
        parameter.getUnits().name(),
        min,
        max,
        options,
        formatted);
  }
}
