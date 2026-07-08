package lxmcp.domain;

import java.util.List;
import java.util.stream.Collectors;

import heronarts.lx.LX;
import heronarts.lx.color.ColorParameter;
import heronarts.lx.command.LXCommand;
import heronarts.lx.osc.LXOscEngine;
import heronarts.lx.parameter.AggregateParameter;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.FunctionalParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.QuantizedTriggerParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.parameter.TriggerParameter;

/** Parameter lookup, introspection, and undoable set by canonical LX path. */
public final class Parameters {

  /**
   * {@code value} is type-appropriate (Boolean / String / Integer / Double; colors as an
   * 0xAARRGGBB hex string) and reports the base (unmodulated) value; {@code normalized},
   * {@code min}/{@code max}, {@code options} and {@code formatted} are null where the
   * parameter type has no such concept.
   */
  public record ParameterInfo(String path, String label, String description, String type,
      Object value, Double normalized, String units, Double min, Double max,
      List<String> options, String formatted, String oscAddress) {}

  private Parameters() {}

  /**
   * Resolve a canonical path (as produced by {@code getCanonicalPath()}, e.g.
   * {@code /lx/mixer/channel/1/fader}) to a snapshot. Call on the engine thread.
   *
   * @throws Resolve.ResolveException typed failure when the path is malformed, empty,
   *     or doesn't lead to a parameter
   */
  public static ParameterInfo get(LX lx, String path) {
    return describe(Resolve.parameter(lx, path));
  }

  /**
   * Set the parameter at {@code path} to {@code value}, routed through the matching
   * {@code LXCommand.Parameter} setter so the change is undoable. This is the single place
   * that knows how each parameter type is set — dispatched on the resolved runtime type.
   * Call on the engine thread.
   *
   * <p>{@code value} must match the parameter: a number for numeric/bounded parameters, an
   * in-range integer for discrete/enum, a boolean for toggles, a string for text. An {@link
   * AggregateParameter} (colors, MIDI filters, …) packs several subparameters — those are set
   * individually via their own paths, so setting the aggregate directly is rejected. Momentary
   * triggers are actions, not values, and are likewise rejected.
   *
   * @return a fresh snapshot of the parameter after the set.
   * @throws Resolve.ResolveException typed failure: bad path, non-parameter target, an
   *     unsettable parameter (aggregate, trigger, computed), or a value whose type doesn't
   *     match the parameter.
   */
  public static ParameterInfo set(LX lx, String path, Object value) {
    LXParameter parameter = Resolve.parameter(lx, path);
    if (parameter instanceof StringParameter s) {
      Commands.perform(lx, new LXCommand.Parameter.SetString(s, requireString(parameter, value)));
    } else if (parameter instanceof TriggerParameter) {
      // Fires side effects and synchronously resets to false — the snapshot would echo
      // value=false for a set(true) (inviting client retries that re-fire the trigger)
      // and the undo entry would be a false->false no-op.
      throw mismatch(parameter, "is a momentary trigger — use fire_trigger");
    } else if (parameter instanceof BooleanParameter b) {
      Commands.perform(lx, new LXCommand.Parameter.SetNormalized(b, requireBoolean(parameter, value)));
    } else if (parameter instanceof DiscreteParameter d) {
      Commands.perform(lx, new LXCommand.Parameter.SetValue(d, requireInt(d, value)));
    } else if (parameter instanceof AggregateParameter a) {
      // No command sets a packed aggregate double sanely (colors: SetColor covers only
      // hue+saturation; MIDI filters bit-unpack the raw double into six subparameters);
      // the subparameters are individually addressable, so route the caller there.
      throw mismatch(parameter, "is an aggregate — set its components via the "
          + a.subparameters.keySet().stream()
              .map(key -> ".../" + key).collect(Collectors.joining(", "))
          + " paths");
    } else if (parameter instanceof FunctionalParameter) {
      // setValue() throws UnsupportedOperationException, which perform() would swallow
      // (silently wiping the undo stack) and we'd return a false success — reject up front.
      throw mismatch(parameter, "is a computed read-only parameter and cannot be set");
    } else {
      Commands.perform(lx, new LXCommand.Parameter.SetValue(parameter, requireNumber(parameter, value)));
    }
    return describe(parameter);
  }

  /** {@code pending}: launch quantization deferred the fire to the next tempo boundary. */
  public record FireInfo(ParameterInfo parameter, boolean pending) {}

  /**
   * Fire the momentary trigger at {@code path}: a {@link TriggerParameter} fires and
   * auto-resets; a momentary {@link BooleanParameter} (e.g. a MacroTriggers macro) gets a
   * press/release pulse, whose rising edge fires any wired trigger modulations.
   *
   * <p>Deliberately not routed through LXCommand: firing is an action with side effects,
   * not undoable state — there is nothing for Cmd-Z to restore (the value is already
   * false again). Toggles and plain values are set_parameter territory.
   *
   * @return the post-fire snapshot plus whether the fire is merely *pending*: a
   *     {@link QuantizedTriggerParameter} (pattern/clip launch) under launch quantization
   *     defers to the next tempo boundary — a caller that treats pending as failed and
   *     re-fires would queue duplicate launches.
   * @throws Resolve.ResolveException typed failure: bad path, or not a momentary trigger.
   */
  public static FireInfo fire(LX lx, String path) {
    LXParameter parameter = Resolve.parameter(lx, path);
    boolean pending = false;
    if (parameter instanceof TriggerParameter t) {
      t.trigger();
      if (t instanceof QuantizedTriggerParameter q) {
        pending = q.pending.isOn();
      }
    } else if (parameter instanceof BooleanParameter b
        && b.getMode() == BooleanParameter.Mode.MOMENTARY) {
      if (b.isOn()) {
        // Already held (a UI/MIDI press): release first so the pulse still has a rising edge.
        b.setValue(false);
      }
      b.setValue(true);
      b.setValue(false);
    } else {
      throw mismatch(parameter,
          "is not a momentary trigger — use set_parameter for toggles and values");
    }
    return new FireInfo(describe(parameter), pending);
  }

  private static String requireString(LXParameter p, Object value) {
    if (value instanceof String s) {
      return s;
    }
    throw mismatch(p, "expects a string value");
  }

  private static boolean requireBoolean(LXParameter p, Object value) {
    if (value instanceof Boolean b) {
      return b;
    }
    throw mismatch(p, "expects a boolean value");
  }

  private static int requireInt(DiscreteParameter p, Object value) {
    if (value instanceof Number n) {
      double d = n.doubleValue();
      if (d != Math.rint(d)) {
        throw mismatch(p, "expects an integer value");
      }
      // DiscreteParameter silently wraps out-of-range values modulo the range — reject instead.
      if (d < p.getMinValue() || d > p.getMaxValue()) {
        throw mismatch(p, "expects an integer in ["
            + p.getMinValue() + ", " + p.getMaxValue() + "]");
      }
      return (int) d;
    }
    throw mismatch(p, "expects an integer value");
  }

  private static double requireNumber(LXParameter p, Object value) {
    if (value instanceof Number n) {
      return n.doubleValue();
    }
    throw mismatch(p, "expects a numeric value");
  }

  private static Resolve.ResolveException mismatch(LXParameter p, String detail) {
    return new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
        p.getCanonicalPath() + " (" + p.getClass().getSimpleName() + ") " + detail);
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
      // Base value, not getValue(): a modulated CompoundParameter layers live modulation on
      // top, and a set-then-read client must see the value it actually set.
      value = parameter.getBaseValue();
      if (parameter instanceof BoundedParameter b) {
        min = b.range.min;
        max = b.range.max;
      }
      formatted = parameter.getFormatter().format(parameter.getBaseValue());
    }
    return new ParameterInfo(
        parameter.getCanonicalPath(),
        parameter.getLabel(),
        parameter.getDescription(),
        parameter.getClass().getSimpleName(),
        value,
        (parameter instanceof LXNormalizedParameter n) ? n.getBaseNormalized() : null,
        parameter.getUnits().name(),
        min,
        max,
        options,
        formatted,
        // Differs from the canonical path for modulator-owned parameters (label-based
        // segments); null for parameters LX does not expose over OSC. docs/osc-addressing.md
        LXOscEngine.getOscAddress(parameter));
  }
}
