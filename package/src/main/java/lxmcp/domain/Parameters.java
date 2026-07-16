package lxmcp.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.color.ColorParameter;
import heronarts.lx.command.LXCommand;
import heronarts.lx.osc.LXOscEngine;
import heronarts.lx.parameter.AggregateParameter;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.FunctionalParameter;
import heronarts.lx.modulation.LXCompoundModulation;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.QuantizedTriggerParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.parameter.TriggerParameter;

/** Parameter lookup, introspection, and undoable set by canonical LX path. */
public final class Parameters {

  /**
   * {@code value} is type-appropriate (Boolean / String / Integer / Double; colors as an
   * 0xAARRGGBB hex string); {@code normalized}, {@code min}/{@code max}, {@code options} and
   * {@code formatted} are null where the parameter type has no such concept. For a parameter
   * with at least one active {@link LXCompoundModulation}, {@code value}/{@code normalized}
   * are the live effective (modulated) reading, {@code modulated} is {@code true}, and
   * {@code baseValue}/{@code baseNormalized} carry the unmodulated knob position; otherwise
   * {@code value}/{@code normalized} are the base value, {@code modulated} is {@code false},
   * and {@code baseValue}/{@code baseNormalized} are null (no noise on the common case).
   */
  public record ParameterInfo(String path, String label, String description, String type,
      Object value, Double normalized, String units, Double min, Double max,
      List<String> options, String formatted, String oscAddress,
      boolean modulated, Object baseValue, Double baseNormalized) {}

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

  /** A direct child component: {@code key} is its {@code children}/{@code childArrays} key. */
  public record ChildInfo(String key, String path, String label, String className) {}

  /**
   * A component's identity plus a snapshot of every parameter it directly owns, and its
   * direct child components (one shallow level — no recursion, no grandchildren, no child
   * parameters). Use a child's own path with {@code list_parameters} to walk further.
   */
  public record ComponentParameters(String path, int id, String label, String className,
      List<ParameterInfo> parameters, List<ChildInfo> children) {}

  /**
   * List every parameter directly on the component at {@code path}. Call on the engine
   * thread.
   *
   * @throws Resolve.ResolveException typed failure: bad/unknown path, or a path that
   *     resolves to a parameter rather than a component (told to use {@code get_parameter}
   *     instead).
   */
  public static ComponentParameters listFor(LX lx, String path) {
    LXComponent component;
    try {
      component = Resolve.component(lx, path);
    } catch (Resolve.ResolveException e) {
      if (e.failure == Resolve.Failure.TYPE_MISMATCH) {
        try {
          Resolve.parameter(lx, path);
        } catch (Resolve.ResolveException notAParameter) {
          throw e;
        }
        throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
            "Not a component at path: " + path + " — that path is a parameter, use get_parameter");
      }
      throw e;
    }
    List<ParameterInfo> parameters = component.getParameters().stream()
        .map(Parameters::describe)
        .collect(Collectors.toList());
    return new ComponentParameters(
        Resolve.canonicalPath(component),
        component.getId(),
        component.getLabel(),
        component.getClass().getName(),
        parameters,
        describeChildren(component));
  }

  private static List<ChildInfo> describeChildren(LXComponent component) {
    List<ChildInfo> children = new ArrayList<>();
    Set<String> seenPaths = new HashSet<>();
    for (Map.Entry<String, LXComponent> entry : component.children.entrySet()) {
      ChildInfo child = describeChild(entry.getKey(), entry.getValue());
      children.add(child);
      seenPaths.add(child.path());
    }
    for (Map.Entry<String, List<? extends LXComponent>> entry : component.childArrays.entrySet()) {
      String key = entry.getKey();
      for (LXComponent child : entry.getValue()) {
        ChildInfo childInfo = describeChild(key, child);
        if (!seenPaths.contains(childInfo.path())) {
          children.add(childInfo);
          seenPaths.add(childInfo.path());
        }
      }
    }
    return children;
  }

  private static ChildInfo describeChild(String key, LXComponent child) {
    return new ChildInfo(
        key, Resolve.canonicalPath(child), child.getLabel(), child.getClass().getName());
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
      Commands.perform(lx, new LXCommand.Parameter.SetValue(d, requireDiscreteIndex(d, value)));
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

  /**
   * Discrete/selector parameters accept either an in-range integer index or (when the
   * parameter has {@link DiscreteParameter#getOptions()}) an option name string — e.g. a
   * device's view selector accepts the target view's label, so callers don't have to look
   * up its numeric index first.
   */
  private static int requireDiscreteIndex(DiscreteParameter p, Object value) {
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
    if (value instanceof String s) {
      String[] options = p.getOptions();
      if (options == null) {
        throw mismatch(p, "has no named options — expects an integer value");
      }
      List<Integer> matches = new ArrayList<>();
      for (int i = 0; i < options.length; ++i) {
        if (options[i].equals(s)) {
          matches.add(i);
        }
      }
      if (matches.size() == 1) {
        return matches.get(0);
      }
      String validOptions = String.join(", ", options);
      throw mismatch(p, matches.isEmpty()
          ? "has no option named '" + s + "' — valid options: " + validOptions
          : "has " + matches.size() + " options named '" + s + "' — ambiguous, valid "
              + "options: " + validOptions);
    }
    throw mismatch(p, "expects an integer value or an option name string");
  }

  private static double requireNumber(LXParameter p, Object value) {
    if (value instanceof Number n) {
      return n.doubleValue();
    }
    throw mismatch(p, "expects a numeric value");
  }

  private static Resolve.ResolveException mismatch(LXParameter p, String detail) {
    return new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
        Resolve.canonicalPath(p) + " (" + p.getClass().getSimpleName() + ") " + detail);
  }

  static ParameterInfo describe(LXParameter parameter) {
    // Only CompoundParameter / CompoundDiscreteParameter implement Target — the only
    // parameter types LX layers live modulation on top of the base value/normalized.
    boolean modulated = parameter instanceof LXCompoundModulation.Target target
        && target.getModulations().stream().anyMatch(m -> m.enabled.isOn());
    Object value;
    Object baseValue = null;
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
      // getValuei()/getOption()/getFormatter().format(getValue()) already read through live
      // modulation on a CompoundDiscreteParameter — only the base needs a separate call.
      value = d.getValuei();
      if (modulated) {
        baseValue = d.getBaseValuei();
      }
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
      // Effective (modulated) value when a compound modulation is live, so a polling
      // client sees the value that's actually driving the render; base value otherwise —
      // a set-then-read client must see the value it actually set.
      double effective = modulated ? parameter.getValue() : parameter.getBaseValue();
      value = effective;
      if (modulated) {
        baseValue = parameter.getBaseValue();
      }
      if (parameter instanceof BoundedParameter b) {
        min = b.range.min;
        max = b.range.max;
      }
      formatted = parameter.getFormatter().format(effective);
    }
    Double normalized = null;
    Double baseNormalized = null;
    if (parameter instanceof LXNormalizedParameter n) {
      if (modulated) {
        normalized = n.getNormalized();
        baseNormalized = n.getBaseNormalized();
      } else {
        normalized = n.getBaseNormalized();
      }
    }
    return new ParameterInfo(
        Resolve.canonicalPath(parameter),
        parameter.getLabel(),
        parameter.getDescription(),
        parameter.getClass().getSimpleName(),
        value,
        normalized,
        parameter.getUnits().name(),
        min,
        max,
        options,
        formatted,
        // Differs from the canonical path for modulator-owned parameters (label-based
        // segments); null for parameters LX does not expose over OSC. docs/osc-addressing.md
        LXOscEngine.getOscAddress(parameter),
        modulated,
        baseValue,
        baseNormalized);
  }
}
